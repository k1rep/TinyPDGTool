package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.*;

public class ControlFlowGraph {
    private final DirectedGraph graph;
    private final Deque<Set<Object>> previousNodes = new ArrayDeque<>();
    private final Deque<Set<Object>> continueFromNodes = new ArrayDeque<>();
    private final Deque<Set<Object>> breakingFromNodes = new ArrayDeque<>();
    private final Deque<Set<Object>> returnFromNodes = new ArrayDeque<>();
    private final Deque<Set<Object>> throwingNodes = new ArrayDeque<>();
    private static final Set<Object> EmptySet = Collections.emptySet();
    private final ResolvedMethodDeclaration context;
    public static final String ControlFlowEdge = "controlflow";
    public static final String YieldEdge = "yield";
    public static final String ReturnEdge = "return";

    public ControlFlowGraph(DirectedGraph graph, ResolvedMethodDeclaration context) {
        this.graph = graph;
        this.context = context;
    }

    private void addNextNode(Object node) {
        for (Object fromNode : this.previousNodes.pop()) {
            this.graph.addEdge(fromNode, node, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        this.previousNodes.push(Collections.singleton(node));
    }

    public void visit(Node node) {
        if (node instanceof Expression) {
            if (!(node instanceof LambdaExpr)) {
                addNextNode(node);
            }
        } else {
            for (Node child : node.getChildNodes()) {
                visit(child);
            }
        }
    }

    public void visitBreakStatement(BreakStmt node) {
        this.breakingFromNodes.push(new HashSet<>(this.breakingFromNodes.pop()));
        this.previousNodes.push(EmptySet);
    }

    public void visitIfStatement(IfStmt node) {
        visit(node.getCondition());
        Set<Object> immutableHashSet = this.previousNodes.peek();
        visit(node.getThenStmt());
        Set<Object> other = this.previousNodes.pop();
        this.previousNodes.push(immutableHashSet);
        if (node.getElseStmt().isPresent()) {
            visit(node.getElseStmt().get());
        }
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
    }

    public void visitContinueStatement(ContinueStmt node) {
        Set<Object> other = this.previousNodes.pop();
        this.continueFromNodes.push(new HashSet<>(this.continueFromNodes.pop()));
        this.previousNodes.push(EmptySet);
    }

    public void visitReturnStatement(ReturnStmt node) {
        addNextNode(node);
        this.returnFromNodes.push(new HashSet<>(this.returnFromNodes.pop()));
        this.previousNodes.pop();
        this.previousNodes.push(EmptySet);
    }

    public void visitYieldStatement(YieldStmt node) {
        addNextNode(node);
        MethodEntryNode fromNode = new MethodEntryNode(this.context);
        if (this.previousNodes.peek() != null) {
            for (Object toNode : this.previousNodes.peek()) {
                this.graph.addEdge(fromNode, toNode, new AbstractMap.SimpleEntry<>(YieldEdge, null), this.context, this.context);
            }
        }
    }

    public void visitForEachStatement(ForEachStmt node) {
        visit(node.getIterable());
        Object toNode = null;
        if (this.previousNodes.peek() != null) {
            toNode = this.previousNodes.peek().iterator().next();
        }
        this.breakingFromNodes.push(EmptySet);
        this.continueFromNodes.push(EmptySet);
        visit(node.getBody());
        for (Object fromNode : this.continueFromNodes.pop()) {
            this.graph.addEdge(fromNode, toNode, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        this.previousNodes.push(this.breakingFromNodes.pop());
    }

    public void visitForStatement(ForStmt node) {
        if (node.getInitialization().isNonEmpty()) {
            addNextNode(node.getInitialization().get(0));
        }
        node.getInitialization().forEach(this::addNextNode);
        Set<Object> nodes = this.previousNodes.peek();
        node.getCompare().ifPresent(this::visit);
        Object commonExitPoint = null;
        if (nodes != null) {
            commonExitPoint = getCommonExitPoint(nodes);
        }
        Set<Object> immutableHashSet = this.previousNodes.peek();
        this.continueFromNodes.push(EmptySet);
        this.breakingFromNodes.push(EmptySet);
        visit(node.getBody());
        node.getUpdate().forEach(this::visit);
        for (Object fromNode : this.previousNodes.pop()) {
            this.graph.addEdge(fromNode, commonExitPoint, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        if (immutableHashSet != null) {
            this.previousNodes.push(new HashSet<>(immutableHashSet));
        }
    }

    public void visitSwitchStatement(SwitchStmt node) {
        visit(node.getSelector());
        Set<Object> other = this.previousNodes.peek();
        this.breakingFromNodes.push(EmptySet);
        for (SwitchEntry entry : node.getEntries()) {
            this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
            visit(entry);
        }
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
    }

    public void visitTryStatement(TryStmt node) {
        if (node.getFinallyBlock().isPresent()) {
            this.returnFromNodes.push(EmptySet);
            visitTryCatch(node);
            Set<Object> other = this.returnFromNodes.pop();
            this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
            if (!other.isEmpty()) {
                this.returnFromNodes.push(new HashSet<>(this.returnFromNodes.pop()));
            }
        } else {
            visitTryCatch(node);
        }
    }

    public void visitThrowStatement(ThrowStmt node) {
        addNextNode(node);
        this.throwingNodes.push(new HashSet<>(this.throwingNodes.pop()));
        this.previousNodes.pop();
        this.previousNodes.push(EmptySet);
    }

    private void visitTryCatch(TryStmt node) {
        this.throwingNodes.push(EmptySet);
        visit(node.getTryBlock());
        if (!node.getCatchClauses().isEmpty()) {
            Set<Object> collection = null;
            if (this.previousNodes.peek() != null) {
                collection = new HashSet<>(this.previousNodes.peek());
            }
            Set<Object> other = null;
            if (collection != null) {
                other = new HashSet<>(collection);
            }
            for (CatchClause catchClause : node.getCatchClauses()) {
                visit(catchClause);
                if (other != null) {
                    other.addAll(this.previousNodes.pop());
                }
                this.previousNodes.push(collection);
            }
            this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
        } else {
            this.returnFromNodes.push(new HashSet<>(this.returnFromNodes.pop()));
        }
    }

    public void visitConditionalExpression(ConditionalExpr node) {
        visit(node.getCondition());
        Set<Object> immutableHashSet = this.previousNodes.peek();
        visit(node.getThenExpr());
        Set<Object> other = this.previousNodes.pop();
        this.previousNodes.push(immutableHashSet);
        visit(node.getElseExpr());
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
    }

    public void visitWhileStatement(WhileStmt node) {
        Set<Object> nodes = this.previousNodes.peek();
        visit(node.getCondition());
        Object commonExitPoint = null;
        if (nodes != null) {
            commonExitPoint = getCommonExitPoint(nodes);
        }
        Set<Object> immutableHashSet = this.previousNodes.peek();
        this.continueFromNodes.push(EmptySet);
        this.breakingFromNodes.push(EmptySet);
        visit(node.getBody());
        for (Object fromNode : this.previousNodes.pop()) {
            this.graph.addEdge(fromNode, commonExitPoint, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        if (immutableHashSet != null) {
            this.previousNodes.push(new HashSet<>(immutableHashSet));
        }
    }

    public void visitDoStatement(DoStmt node) {
        Object commonExitPoint = null;
        if (this.previousNodes.peek() != null) {
            commonExitPoint = getCommonExitPoint(this.previousNodes.peek());
        }
        this.continueFromNodes.push(EmptySet);
        this.breakingFromNodes.push(EmptySet);
        visit(node.getBody());
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
        visit(node.getCondition());
        Set<Object> other = this.previousNodes.pop();
        for (Object fromNode : other) {
            this.graph.addEdge(fromNode, commonExitPoint, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        this.previousNodes.push(new HashSet<>(this.breakingFromNodes.pop()));
    }

    public void addMethodDeclaration(Node declarationBody, ResolvedMethodDeclaration rootSymbol) {
        MethodEntryNode toNode = new MethodEntryNode(rootSymbol);
        this.previousNodes.push(Collections.singleton(toNode));
        this.returnFromNodes.push(EmptySet);
        this.throwingNodes.push(EmptySet);
        visit(declarationBody);
        MethodExitNode methodExitNode = new MethodExitNode(rootSymbol);
        for (Object fromNode : this.previousNodes.pop()) {
            this.graph.addEdge(fromNode, methodExitNode, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        this.graph.addEdge(methodExitNode, toNode, new AbstractMap.SimpleEntry<>(ReturnEdge, rootSymbol), this.context, this.context);
    }

    private Object getCommonExitPoint(Set<Object> nodes) {
        if(nodes.isEmpty()) return null;
        Iterator<Object> iterator = nodes.iterator();
        Object firstNode = iterator.next();
        Set<Object> commonExits = new HashSet<>(this.graph.getOutEdgesFrom(firstNode));
        while(iterator.hasNext()){
            Object node = iterator.next();
            commonExits.retainAll(this.graph.getOutEdgesFrom(node));
            if(commonExits.isEmpty()){
                break;
            }
        }
        return commonExits.isEmpty() ? firstNode: commonExits.iterator().next();
    }
}
