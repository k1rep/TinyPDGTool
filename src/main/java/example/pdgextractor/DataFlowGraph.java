package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DataFlowGraph extends VoidVisitorAdapter<Void> {
    private static final Logger logger = Logger.getLogger(DataFlowGraph.class.getName());
    private final DirectedGraph graph;
    private final JavaParserFacade javaParserFacade;
    private final Deque<Map<ResolvedValueDeclaration, Set<Object>>> previousOutFlow = new ArrayDeque<>();
    private final Deque<Map<ResolvedValueDeclaration, Set<Object>>> continueOutFlow = new ArrayDeque<>();
    private final Deque<Map<ResolvedValueDeclaration, Set<Object>>> breakOutFlow = new ArrayDeque<>();

    public static final String DATA_FLOW_EDGE = "dataflow";

    public DataFlowGraph(DirectedGraph graph, JavaParserFacade javaParserFacade) {
        this.graph = graph;
        this.javaParserFacade = javaParserFacade;
    }

    private void addNextNode(ExpressionStmt syntaxNode) {
        logger.info("Syntax node: " + syntaxNode);
        Node parentNode = syntaxNode.getParentNode().orElse(null);
        while (parentNode != null && !this.graph.containsNode(parentNode)){
            parentNode =  parentNode.getParentNode().orElse(null);
        }
        if(parentNode == null) {
            return;
        }
        logger.info("Debugging=================");
        ResolvedValueDeclaration valueDeclaration;
        try {
            valueDeclaration = javaParserFacade.solve(syntaxNode.getExpression()).getCorrespondingDeclaration();
        } catch (Exception e) {
            return;
        }
        logger.info("Debugging=================");
        Map<ResolvedValueDeclaration, Set<Object>> dictionary = new HashMap<>(this.previousOutFlow.pop());

        if (dictionary.containsKey(valueDeclaration)) {
            for (Object fromNode : dictionary.get(valueDeclaration)) {
                this.graph.addEdge(fromNode, syntaxNode, new AbstractMap.SimpleEntry<>(DATA_FLOW_EDGE, valueDeclaration), fromNode.toString(), Objects.requireNonNull(syntaxNode).toString());
            }
        }

        dictionary.put(valueDeclaration, Collections.singleton(syntaxNode));
        this.previousOutFlow.push(dictionary);
    }

    private void addDeclarations(VariableDeclarationExpr node) {
        Node parentNode = node.getParentNode().orElse(null);
        while (parentNode != null && !this.graph.containsNode(parentNode)) {
            parentNode = parentNode.getParentNode().orElse(null);
        }
        if (parentNode == null) {
            return;
        }

        Map<ResolvedValueDeclaration, Set<Object>> dictionary = new HashMap<>(this.previousOutFlow.pop());
        for (VariableDeclarator variable : node.getVariables()) {
            ResolvedValueDeclaration declaredSymbol = javaParserFacade.solve(variable.getNameAsExpression()).getCorrespondingDeclaration();
            dictionary.put(declaredSymbol, Collections.singleton(parentNode));
        }

        this.previousOutFlow.push(dictionary);
    }

    @Override
    public void visit(BlockStmt node, Void arg) {
        this.previousOutFlow.push(new HashMap<>());
        for (Node childNode : node.getChildNodes()) {
            logger.info(childNode.getMetaModel().getTypeName());
            logger.info("Visiting " + childNode);
            logger.info("Previous outflow: " + this.previousOutFlow.peek());
            if (childNode instanceof VariableDeclarationExpr) {
                addDeclarations((VariableDeclarationExpr) childNode);
            } else if(childNode instanceof ExpressionStmt) {
                addNextNode((ExpressionStmt) childNode);
            } else{
                childNode.accept(this, arg);
            }
        }
        this.previousOutFlow.pop();

    }

    @Override
    public void visit(IfStmt node, Void arg) {
        node.getCondition().accept(this, arg);
        Map<ResolvedValueDeclaration, Set<Object>> immutableDictionary = this.previousOutFlow.peek();
        node.getThenStmt().accept(this, arg);
        Map<ResolvedValueDeclaration, Set<Object>> dict2 = this.previousOutFlow.pop();
        this.previousOutFlow.push(immutableDictionary);
        node.getElseStmt().ifPresent(elseStmt -> elseStmt.accept(this, arg));
        this.previousOutFlow.push(mergeDicts(this.previousOutFlow.pop(), dict2));
    }

    @Override
    public void visit(WhileStmt node, Void arg) {
        node.getCondition().accept(this, arg);
        Map<ResolvedValueDeclaration, Set<Object>> dict2 = this.previousOutFlow.peek();
        node.getBody().accept(this, arg);
        this.previousOutFlow.push(mergeDicts(this.previousOutFlow.pop(), Objects.requireNonNull(dict2)));
    }

    @Override
    public void visit(ForEachStmt node, Void arg) {
        node.getIterable().accept(this, arg);
        Map<ResolvedValueDeclaration, Set<Object>> dict1 = new HashMap<>(this.previousOutFlow.pop());
        ResolvedValueDeclaration symbol = javaParserFacade.solve(node.getVariable()).getCorrespondingDeclaration();
        dict1.put(symbol, Collections.singleton(node.getIterable()));
        this.previousOutFlow.push(dict1);
        node.getBody().accept(this, arg);
        this.previousOutFlow.push(mergeDicts(dict1, this.previousOutFlow.pop()));
    }

    @Override
    public void visit(ForStmt node, Void arg) {
        for (Expression initializer : node.getInitialization()) {
            initializer.accept(this, arg);
        }
        node.getCompare().ifPresent(compare -> compare.accept(this, arg));
        Map<ResolvedValueDeclaration, Set<Object>> dict1 = this.previousOutFlow.peek();
        node.getBody().accept(this, arg);
        for (Expression update : node.getUpdate()) {
            update.accept(this, arg);
        }
        this.previousOutFlow.push(mergeDicts(Objects.requireNonNull(dict1), this.previousOutFlow.pop()));
    }

    @Override
    public void visit(DoStmt node, Void arg) {
        node.getBody().accept(this, arg);
        node.getCondition().accept(this, arg);
    }

    private Map<ResolvedValueDeclaration, Set<Object>> mergeDicts(
            Map<ResolvedValueDeclaration, Set<Object>> dict1,
            Map<ResolvedValueDeclaration, Set<Object>> dict2) {
        Map<ResolvedValueDeclaration, Set<Object>> result = new HashMap<>(dict1);
        for (Map.Entry<ResolvedValueDeclaration, Set<Object>> entry : dict2.entrySet()) {
            result.merge(entry.getKey(), entry.getValue(), (set1, set2) -> {
                Set<Object> mergedSet = new HashSet<>(set1);
                mergedSet.addAll(set2);
                return mergedSet;
            });
        }
        return result;
    }

    public void addDataFlowEdges(BlockStmt node, ResolvedMethodDeclaration symbol, Map<ResolvedValueDeclaration, Set<Object>> existingNode) {
        if (node == null || symbol == null) {
            return;
        }

        if (existingNode == null) {
            existingNode = new HashMap<>();
        }

        Set<Object> startingInFlows = new HashSet<>();
        startingInFlows.add(new MethodEntryNode(symbol));

        Map<ResolvedValueDeclaration, Set<Object>> initialFlow = existingNode.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().contains(symbol) ? startingInFlows : entry.getValue()
                ));

        this.previousOutFlow.push(initialFlow);

        this.visit(node, null);

        Map<ResolvedValueDeclaration, Set<Object>> result = this.previousOutFlow.pop();
        MethodExitNode toNode = new MethodExitNode(symbol);
        logger.info(result.toString());
        for (Map.Entry<ResolvedValueDeclaration, Set<Object>> entry : result.entrySet()) {
            for (Object fromNode : entry.getValue()) {
                logger.info("Adding dataflow edge from " + fromNode + " to " + toNode);
                this.graph.addEdge(fromNode, toNode, new AbstractMap.SimpleEntry<>(DATA_FLOW_EDGE, entry.getKey()), fromNode.toString(), toNode.toString());
            }
        }
    }
}
