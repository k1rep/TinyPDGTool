package example.pdgextractor;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import java.util.*;

public class DataFlowGraph extends VoidVisitorAdapter<Void> {
    private final DirectedGraph graph;
    private final JavaParserFacade javaParserFacade;
    private final Deque<Map<String, Set<Node>>> previousOutFlow = new ArrayDeque<>();

    public DataFlowGraph(DirectedGraph graph, JavaParserFacade javaParserFacade) {
        this.graph = graph;
        this.javaParserFacade = javaParserFacade;
    }

    private void addNextNode(Expression node) {
        Node syntaxNode = node;
        while (syntaxNode != null && !this.graph.containsNode(syntaxNode)) {
            syntaxNode = syntaxNode.getParentNode().orElse(null);
        }
        if (syntaxNode == null) return;
        if (previousOutFlow.isEmpty()) {
            previousOutFlow.push(new HashMap<>());
        }
        Map<String, Set<Node>> currentOutFlow = new HashMap<>(previousOutFlow.pop());
        Set<String> readSymbols = getReadSymbols(node);
        for (String symbol : readSymbols) {
            if (currentOutFlow.containsKey(symbol)) {
                for (Node fromNode : currentOutFlow.get(symbol)) {
                    this.graph.addEdge(fromNode, syntaxNode, new AbstractMap.SimpleEntry<>("dataflow", symbol), null, null);
                }
            }
        }

        Set<String> writtenSymbols = getWrittenSymbols(node);
        for (String symbol : writtenSymbols) {
            currentOutFlow.put(symbol, new HashSet<>(Collections.singletonList(syntaxNode)));
        }
        previousOutFlow.push(currentOutFlow);
    }

    private Set<String> getReadSymbols(Expression node) {
        Set<String> readSymbols = new HashSet<>();
        node.findAll(NameExpr.class).forEach(nameExpr -> {
            try {
                ResolvedDeclaration resolvedDeclaration = javaParserFacade.solve(nameExpr).getCorrespondingDeclaration();
                if (resolvedDeclaration instanceof ResolvedValueDeclaration) {
                    readSymbols.add(resolvedDeclaration.getName());
                }
            } catch (Exception ignored) {
            }
        });
        return readSymbols;
    }

    private Set<String> getWrittenSymbols(Expression node) {
        Set<String> writtenSymbols = new HashSet<>();
        if (node instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) node;
            try {
                ResolvedDeclaration resolvedDeclaration = javaParserFacade.solve(assignExpr.getTarget()).getCorrespondingDeclaration();
                if (resolvedDeclaration instanceof ResolvedValueDeclaration) {
                    writtenSymbols.add(resolvedDeclaration.getName());
                }
            } catch (Exception ignored) {
            }
        }
        return writtenSymbols;
    }

    @Override
    public void visit(ExpressionStmt node, Void arg) {
        super.visit(node, arg);
        if (node.getExpression() != null) {
            addNextNode(node.getExpression());
        }
    }

    @Override
    public void visit(VariableDeclarationExpr node, Void arg) {
        super.visit(node, arg);
        addDeclarations(node);
    }

    private void addDeclarations(VariableDeclarationExpr node) {
        Node parent = node;
        while (parent != null && !this.graph.containsNode(parent)) {
            parent = parent.getParentNode().orElse(null);
        }
        if (parent == null) return;
        if (previousOutFlow.isEmpty()) {
            previousOutFlow.push(new HashMap<>());
        }
        Map<String, Set<Node>> currentOutFlow = new HashMap<>(previousOutFlow.pop());
        Node finalParent = parent;
        node.getVariables().forEach(variable -> {
            String name = variable.getNameAsString();
            currentOutFlow.put(name, new HashSet<>(Collections.singletonList(finalParent)));
            variable.getInitializer().ifPresent(initializer -> initializer.accept(this, null));
        });
        previousOutFlow.push(currentOutFlow);
    }

    @Override
    public void visit(IfStmt node, Void arg) {
        node.getCondition().accept(this, arg);
        Map<String, Set<Node>> thenOutFlow = new HashMap<>();
        Map<String, Set<Node>> elseOutFlow = new HashMap<>();
        Map<String, Set<Node>> beforeIf = previousOutFlow.peek();
        node.getThenStmt().accept(this, arg);
        if(!previousOutFlow.isEmpty()) {
             thenOutFlow = previousOutFlow.pop();
        }
        if(!previousOutFlow.isEmpty()) {
            previousOutFlow.push(beforeIf);
        }
        node.getElseStmt().ifPresent(stmt -> stmt.accept(this, arg));
        if(!previousOutFlow.isEmpty()){
            elseOutFlow = previousOutFlow.pop();
        }
        if(thenOutFlow != null && elseOutFlow != null) {
            previousOutFlow.push(mergeDicts(thenOutFlow, elseOutFlow));
        }
    }

    @Override
    public void visit(WhileStmt node, Void arg) {
        node.getCondition().accept(this, arg);
        Map<String, Set<Node>> beforeWhile = previousOutFlow.peek();
        node.getBody().accept(this, arg);
        previousOutFlow.push(mergeDicts(Objects.requireNonNull(beforeWhile), previousOutFlow.pop()));
    }

    private Map<String, Set<Node>> mergeDicts(Map<String, Set<Node>> map1, Map<String, Set<Node>> map2) {
        Map<String, Set<Node>> merged = new HashMap<>(map1);
        map2.forEach((key, value) -> merged.merge(key, value, (v1, v2) -> {
            Set<Node> mergedSet = new HashSet<>(v1);
            mergedSet.addAll(v2);
            return mergedSet;
        }));
        return merged;
    }
}