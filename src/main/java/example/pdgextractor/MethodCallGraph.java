package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import java.util.AbstractMap;
import java.util.Optional;

public class MethodCallGraph extends VoidVisitorAdapter<Void> {
    private final DirectedGraph graph;
    private final JavaParserFacade javaParserFacade;
    public static final String METHOD_INVOKE_EDGE = "invoke";

    public MethodCallGraph(DirectedGraph graph, JavaParserFacade javaParserFacade) {
        this.graph = graph;
        this.javaParserFacade = javaParserFacade;
    }

    @Override
    public void visit(MethodCallExpr node, Void arg) {
        super.visit(node, arg);
        Node syntaxNode = node;
        while (syntaxNode != null && !graph.containsNode(syntaxNode)) {
            syntaxNode = syntaxNode.getParentNode().orElse(null);
        }

        try {
            Optional<ResolvedMethodDeclaration> resolvedMethod = Optional.ofNullable(javaParserFacade.solve(node).getCorrespondingDeclaration());
            if (resolvedMethod.isPresent()) {
                ResolvedMethodDeclaration method = resolvedMethod.get();
                graph.addEdge(syntaxNode, new MethodEntryNode(method),
                        new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, method), null, method);
            } else {
                String unknownMethod = "Unk." + (node != null ? node.getName() : null);
                graph.addEdge(syntaxNode, new UnkMethodEntryNode(unknownMethod),
                        new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownMethod), null, unknownMethod);
            }
        } catch (UnsolvedSymbolException e) {
            String unknownMethod = "Unk." + (node != null ? node.getName() : null);
            graph.addEdge(syntaxNode, new UnkMethodEntryNode(unknownMethod),
                    new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownMethod), null, unknownMethod);
            System.err.println("Could not resolve symbol: " + e.getName());
        } catch (RuntimeException e) {
            String unknownMethod = "Unk." + (node != null ? node.getName() : null);
            graph.addEdge(syntaxNode, new UnkMethodEntryNode(unknownMethod),
                    new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownMethod), null, unknownMethod);
            System.err.println("Could not resolve symbol: " + e.getMessage());
        }
    }


    @Override
    public void visit(ObjectCreationExpr node, Void arg) {
        super.visit(node, arg);
        Node syntaxNode = node;
        while (syntaxNode != null && !graph.containsNode(syntaxNode)) {
            syntaxNode = syntaxNode.getParentNode().orElse(null);
        }
        try {
            Optional<ResolvedConstructorDeclaration> resolvedConstructor = Optional.ofNullable(javaParserFacade.solve(node).getCorrespondingDeclaration());
            if (resolvedConstructor.isPresent()) {
                ResolvedConstructorDeclaration constructor = resolvedConstructor.get();
                graph.addEdge(syntaxNode, new MethodEntryNode(constructor),
                        new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, constructor), null, constructor);
            } else {
                String unknownConstructor = (node != null ? node.getType().asString() : null) + ".cstr";
                graph.addEdge(syntaxNode, new UnkMethodEntryNode(unknownConstructor),
                        new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownConstructor), null, unknownConstructor);
            }
        }catch (UnsolvedSymbolException e) {
            String unknownConstructor = (node != null ? node.getType().asString() : null) + ".cstr";
            graph.addEdge(syntaxNode, new UnkMethodEntryNode(unknownConstructor),
                    new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownConstructor), null, unknownConstructor);
            System.err.println("Could not resolve symbol: " + e.getName());
        } catch (RuntimeException e) {
            String unknownConstructor = (node != null ? node.getType().asString() : null) + ".cstr";
            graph.addEdge(syntaxNode, new UnkMethodEntryNode(unknownConstructor),
                    new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownConstructor), null, unknownConstructor);
            System.err.println("Could not resolve symbol: " + e.getMessage());
        }
    }

    @Override
    public void visit(LambdaExpr node, Void arg) {
        super.visit(node, arg);
        String unknownMethod = "Unk." + (node != null ? node.toString() : null);
        graph.addEdge(node, new UnkMethodEntryNode(unknownMethod),
                new AbstractMap.SimpleEntry<>(METHOD_INVOKE_EDGE, unknownMethod), null, unknownMethod);
    }
}
