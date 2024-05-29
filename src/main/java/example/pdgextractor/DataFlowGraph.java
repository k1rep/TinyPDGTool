package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import java.util.*;
import java.util.stream.Collectors;

public class DataFlowGraph {
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

    private void addNextNode(Expression syntaxNode) {
        while (syntaxNode != null && !this.graph.containsNode(syntaxNode)) {
            syntaxNode = (Expression) syntaxNode.getParentNode().orElse(null);
        }
        if(syntaxNode == null) {
            return;
        }

        ResolvedValueDeclaration valueDeclaration;
        try {
            valueDeclaration = javaParserFacade.solve(syntaxNode).getCorrespondingDeclaration();
        } catch (Exception e) {
            return;
        }

        Map<ResolvedValueDeclaration, Set<Object>> dictionary = new HashMap<>(this.previousOutFlow.pop());

        if (dictionary.containsKey(valueDeclaration)) {
            for (Object fromNode : dictionary.get(valueDeclaration)) {
                this.graph.addEdge(fromNode, syntaxNode, new AbstractMap.SimpleEntry<>(DATA_FLOW_EDGE, valueDeclaration), fromNode.toString(), Objects.requireNonNull(syntaxNode).toString());
            }
        }

        dictionary.put(valueDeclaration, Collections.singleton(syntaxNode));
        this.previousOutFlow.push(dictionary);
    }

    public void visit(Node node) {
        if (node instanceof Expression) {
            if(node instanceof VariableDeclarationExpr) {
                addDeclarations((VariableDeclarationExpr) node);
            } else {
                if (node instanceof BinaryExpr) {
                    addNextNode((Expression) node);
                }
            }
        } else if(node instanceof IfStmt) {
            visitIfStatement((IfStmt) node);
        } else if(node instanceof WhileStmt) {
            visitWhileStatement((WhileStmt) node);
        } else if(node instanceof ForEachStmt) {
            visitForEachStatement((ForEachStmt) node);
        } else if(node instanceof ForStmt) {
            visitForStatement((ForStmt) node);
        } else if(node instanceof DoStmt) {
            visitDoStatement((DoStmt) node);
        } else {
            for (Node child : node.getChildNodes()) {
                visit(child);
            }
        }
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

    public void visitIfStatement(IfStmt node) {
        visit(node.getCondition());
        Map<ResolvedValueDeclaration, Set<Object>> immutableDictionary = this.previousOutFlow.peek();
        visit(node.getThenStmt());
        Map<ResolvedValueDeclaration, Set<Object>> dict2 = this.previousOutFlow.pop();
        this.previousOutFlow.push(immutableDictionary);
        visit(node.getElseStmt().orElse(null));
        this.previousOutFlow.push(mergeDicts(this.previousOutFlow.pop(), dict2));
    }

    public void visitWhileStatement(WhileStmt node) {
        visit(node.getCondition());
        Map<ResolvedValueDeclaration, Set<Object>> dict2 = this.previousOutFlow.peek();
        visit(node.getBody());
        this.previousOutFlow.push(mergeDicts(this.previousOutFlow.pop(), dict2));
    }

    public void visitForEachStatement(ForEachStmt node) {
        visit(node.getIterable());
        Map<ResolvedValueDeclaration, Set<Object>> dict1 = new HashMap<>(this.previousOutFlow.pop());
        ResolvedValueDeclaration symbol = javaParserFacade.solve(node.getVariable()).getCorrespondingDeclaration();
        dict1.put(symbol, Collections.singleton(node.getIterable()));
        this.previousOutFlow.push(dict1);
        visit(node.getBody());
        this.previousOutFlow.push(mergeDicts(dict1, this.previousOutFlow.pop()));
    }

    public void visitForStatement(ForStmt node) {
        for (Expression initializer : node.getInitialization()) {
            visit(initializer);
        }
        visit(node.getCompare().orElse(null));
        Map<ResolvedValueDeclaration, Set<Object>> dict1 = this.previousOutFlow.peek();
        visit(node.getBody());
        for (Expression update : node.getUpdate()) {
            visit(update);
        }
        this.previousOutFlow.push(mergeDicts(dict1, this.previousOutFlow.pop()));
    }

    public void visitDoStatement(DoStmt node) {
        visit(node.getBody());
        visit(node.getCondition());
    }

    private Map<ResolvedValueDeclaration, Set<Object>> mergeDicts(
            Map<ResolvedValueDeclaration, Set<Object>> dict1,
            Map<ResolvedValueDeclaration, Set<Object>> dict2) {
        Set<ResolvedValueDeclaration> keys = new HashSet<>();
        keys.addAll(dict1.keySet());
        keys.addAll(dict2.keySet());

        Map<ResolvedValueDeclaration, Set<Object>> result = new HashMap<>();
        for (ResolvedValueDeclaration key : keys) {
            Set<Object> values = new HashSet<>();
            if (dict1.containsKey(key)) {
                values.addAll(dict1.get(key));
            }
            if (dict2.containsKey(key)) {
                values.addAll(dict2.get(key));
            }
            result.put(key, values);
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

        visit(node);

        Map<ResolvedValueDeclaration, Set<Object>> result = this.previousOutFlow.pop();
        MethodExitNode toNode = new MethodExitNode(symbol);

        for (Map.Entry<ResolvedValueDeclaration, Set<Object>> entry : result.entrySet()) {
            for (Object fromNode : entry.getValue()) {
                this.graph.addEdge(fromNode, toNode, new AbstractMap.SimpleEntry<>(DATA_FLOW_EDGE, entry.getKey()), fromNode.toString(), toNode.toString());
            }
        }
    }
}
