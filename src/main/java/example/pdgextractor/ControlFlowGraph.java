package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.*;

public class ControlFlowGraph extends VoidVisitorAdapter<Void> {
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

    @Override
    public void visit(ExpressionStmt node, Void arg) {
        super.visit(node, arg);
        addNextNode(node);
    }

    @Override
    public void visit(BreakStmt node, Void arg) {
        this.breakingFromNodes.push(new HashSet<>(this.breakingFromNodes.pop()));
        this.previousNodes.push(EmptySet);
    }

    @Override
    public void visit(IfStmt node, Void arg) {
        node.getCondition().accept(this, arg);
        Set<Object> immutableHashSet = this.previousNodes.peek();
        node.getThenStmt().accept(this, arg);
        Set<Object> other = this.previousNodes.pop();
        this.previousNodes.push(immutableHashSet);
        if (node.getElseStmt().isPresent()) {
            node.getElseStmt().get().accept(this, arg);
        }
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
    }

    @Override
    public void visit(ContinueStmt node, Void arg) {
        Set<Object> other = this.previousNodes.pop();
        this.continueFromNodes.push(new HashSet<>(this.continueFromNodes.pop()));
        this.previousNodes.push(EmptySet);
    }

    @Override
    public void visit(ReturnStmt node, Void arg) {
        addNextNode(node);
        this.returnFromNodes.push(new HashSet<>(this.returnFromNodes.pop()));
        this.previousNodes.pop();
        this.previousNodes.push(EmptySet);
    }

    @Override
    public void visit(YieldStmt node, Void arg) {
        addNextNode(node);
        MethodEntryNode fromNode = new MethodEntryNode(this.context);
        if (this.previousNodes.peek() != null) {
            for (Object toNode : this.previousNodes.peek()) {
                this.graph.addEdge(fromNode, toNode, new AbstractMap.SimpleEntry<>(YieldEdge, null), this.context, this.context);
            }
        }
    }

    @Override
    public void visit(ForEachStmt node, Void arg) {
        node.getIterable().accept(this, arg);
        Object toNode = null;
        if (this.previousNodes.peek() != null) {
            toNode = this.previousNodes.peek().iterator().next();
        }
        this.breakingFromNodes.push(EmptySet);
        this.continueFromNodes.push(EmptySet);
        node.getBody().accept(this, arg);
        for (Object fromNode : this.continueFromNodes.pop()) {
            this.graph.addEdge(fromNode, toNode, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        this.previousNodes.push(this.breakingFromNodes.pop());
    }

    @Override
    public void visit(ForStmt node, Void arg) {
        if (node.getInitialization().isNonEmpty()) {
            addNextNode(node.getInitialization().get(0));
        }
        node.getInitialization().forEach(this::addNextNode);
        Set<Object> nodes = this.previousNodes.peek();
        node.getCompare().ifPresent(access -> access.accept(this, arg));
        Object commonExitPoint = null;
        if (nodes != null) {
            commonExitPoint = getCommonExitPoint(nodes);
        }
        Set<Object> immutableHashSet = this.previousNodes.peek();
        this.continueFromNodes.push(EmptySet);
        this.breakingFromNodes.push(EmptySet);
        node.getBody().accept(this, arg);
        node.getUpdate().forEach(access -> access.accept(this, arg));
        for (Object fromNode : this.previousNodes.pop()) {
            this.graph.addEdge(fromNode, commonExitPoint, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        if (immutableHashSet != null) {
            this.previousNodes.push(new HashSet<>(immutableHashSet));
        }
    }

    @Override
    public void visit(SwitchStmt node, Void arg) {
        node.getSelector().accept(this, arg);
        Set<Object> other = this.previousNodes.peek();
        this.breakingFromNodes.push(EmptySet);
        for (SwitchEntry entry : node.getEntries()) {
            this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
            entry.accept(this, arg);
        }
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
    }

    @Override
    public void visit(TryStmt node, Void arg) {
        if (node.getFinallyBlock().isPresent()) {
            this.returnFromNodes.push(EmptySet);
            visitTryCatch(node, arg);
            Set<Object> other = this.returnFromNodes.pop();
            this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
            if (!other.isEmpty()) {
                this.returnFromNodes.push(new HashSet<>(this.returnFromNodes.pop()));
            }
        } else {
            visitTryCatch(node, arg);
        }
    }

    @Override
    public void visit(ThrowStmt node, Void arg) {
        addNextNode(node);
        this.throwingNodes.push(new HashSet<>(this.throwingNodes.pop()));
        this.previousNodes.pop();
        this.previousNodes.push(EmptySet);
    }

    private void visitTryCatch(TryStmt node, Void arg) {
        this.throwingNodes.push(EmptySet);
        node.getTryBlock().accept(this, arg);
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
                catchClause.accept(this, arg);
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

    @Override
    public void visit(ConditionalExpr node, Void arg) {
        node.getCondition().accept(this, arg);
        Set<Object> immutableHashSet = this.previousNodes.peek();
        node.getThenExpr().accept(this, arg);
        Set<Object> other = this.previousNodes.pop();
        this.previousNodes.push(immutableHashSet);
        node.getElseExpr().accept(this, arg);
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
    }

    @Override
    public void visit(WhileStmt node, Void arg) {
        Set<Object> nodes = this.previousNodes.peek();
        node.getCondition().accept(this, arg);
        Object commonExitPoint = null;
        if (nodes != null) {
            commonExitPoint = getCommonExitPoint(nodes);
        }
        Set<Object> immutableHashSet = this.previousNodes.peek();
        this.continueFromNodes.push(EmptySet);
        this.breakingFromNodes.push(EmptySet);
        node.getBody().accept(this, arg);
        for (Object fromNode : this.previousNodes.pop()) {
            this.graph.addEdge(fromNode, commonExitPoint, new AbstractMap.SimpleEntry<>(ControlFlowEdge, null), this.context, this.context);
        }
        if (immutableHashSet != null) {
            this.previousNodes.push(new HashSet<>(immutableHashSet));
        }
    }

    @Override
    public void visit(DoStmt node, Void arg) {
        Object commonExitPoint = null;
        if (this.previousNodes.peek() != null) {
            commonExitPoint = getCommonExitPoint(this.previousNodes.peek());
        }
        this.continueFromNodes.push(EmptySet);
        this.breakingFromNodes.push(EmptySet);
        node.getBody().accept(this, arg);
        this.previousNodes.push(new HashSet<>(this.previousNodes.pop()));
        node.getCondition().accept(this, arg);
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
        declarationBody.accept(this, null);
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
