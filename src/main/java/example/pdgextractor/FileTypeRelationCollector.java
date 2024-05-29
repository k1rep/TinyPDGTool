package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import java.util.*;

public class FileTypeRelationCollector extends VoidVisitorAdapter<Void> {
    private final Map<AbstractNode, Set<AbstractNode>> subtypingRelationships;
    private final SymbolResolver symbolResolver;
    private final Set<ResolvedMethodDeclaration> visitedMethods = new HashSet<>();

    public FileTypeRelationCollector(JavaParserFacade javaParserFacade, Map<AbstractNode, Set<AbstractNode>> relationships, boolean includeExternalSymbols) {
        this.subtypingRelationships = relationships;
        TypeSolver typeSolver = javaParserFacade.getTypeSolver();
        this.symbolResolver = new JavaSymbolSolver(typeSolver);
    }

    private void addSubtypingRelation(AbstractNode moreGeneralType, AbstractNode moreSpecificType) {
        if(moreGeneralType == null || moreSpecificType == null) {
            return;
        }
        subtypingRelationships.computeIfAbsent(moreGeneralType, k -> new HashSet<>()).add(moreSpecificType);
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);
        ResolvedMethodDeclaration methodDecl = resolveMethod(n);
        if (methodDecl != null && visitedMethods.add(methodDecl)) {
            addAllMethods(methodDecl);
            for (Expression argument : n.getArguments()) {
                ResolvedParameterDeclaration parameter = determineParameter(n, argument, methodDecl);
                AbstractNode nodeSymbol = getNodeSymbol(argument);
                if (nodeSymbol != null) {
                    addSubtypingRelation(new VariableSymbol(parameter, argument.getRange().map(r -> r.begin.toString()).orElse(null)), nodeSymbol);
                }
            }
        }
    }

    private void addAllMethods(ResolvedMethodDeclaration methodDecl) {
        if (!isUsedSymbol(methodDecl) || !visitedMethods.add(methodDecl)) {
            return;
        }
        String location = null;
        Optional<MethodDeclaration> methodDeclaration = methodDecl.toAst();
        if(methodDeclaration.isPresent()) {
            location = methodDeclaration.get().getRange().map(r -> r.begin.toString()).orElse(null);
        }

        for (ResolvedReferenceType iface : methodDecl.declaringType().getAllAncestors()){
            for (MethodUsage ifaceMethod : iface.getDeclaredMethods()) {
                if (methodDecl.declaringType().getAllMethods().contains(ifaceMethod)) {
                    Optional<MethodDeclaration> interfaceMethod = ifaceMethod.getDeclaration().toAst();
                    if(interfaceMethod.isPresent()){
                        ResolvedMethodDeclaration resolvedInterfaceMethod = symbolResolver.resolveDeclaration(interfaceMethod.get(), ResolvedMethodDeclaration.class);
                        addSubtypingRelation(new MethodReturnSymbol(resolvedInterfaceMethod, interfaceMethod.get().getRange().map(r -> r.begin.toString()).orElse(null)),
                                new MethodReturnSymbol(methodDecl, location));
                    }
                }
            }
        }

        for (ResolvedMethodDeclaration ancestorMethod : methodDecl.declaringType().getDeclaredMethods()) {
            Optional<MethodDeclaration> ancestorMethodDecl = ancestorMethod.toAst();
            if(ancestorMethodDecl.isPresent()) {
                addSubtypingRelation(new MethodReturnSymbol(ancestorMethod, ancestorMethodDecl.get().getRange().map(r -> r.begin.toString()).orElse(null)),
                        new MethodReturnSymbol(methodDecl, location));
            }
        }
    }

    @Override
    public void visit(AssignExpr n, Void arg) {
        super.visit(n, arg);
        ResolvedValueDeclaration varDecl = resolveVariable(n.getTarget());
        ResolvedValueDeclaration valueDecl = resolveVariable(n.getValue());
        if (varDecl == null || valueDecl == null) {
            return;
        }
        addSubtypingRelation(new VariableSymbol(varDecl, n.getTarget().getRange().map(r -> r.begin.toString()).orElse(null)), new VariableSymbol(valueDecl, n.getValue().getRange().map(r -> r.begin.toString()).orElse(null)));
    }

    @Override
    public void visit(VariableDeclarator n, Void arg) {
        super.visit(n, arg);
        ResolvedValueDeclaration varDecl = resolveVariable(n);
        if (varDecl == null) {
            return;
        }

        Expression initializer = n.getInitializer().orElse(null);
        if (initializer != null) {
            ResolvedValueDeclaration initDecl = resolveVariable(initializer);
            if (initDecl != null) {
                addSubtypingRelation(new VariableSymbol(varDecl, n.getRange().map(r -> r.begin.toString()).orElse(null)), new VariableSymbol(initDecl, initializer.getRange().map(r -> r.begin.toString()).orElse(null)));
            }
        }
    }

    private AbstractNode getNodeSymbol(Node node) {
        if (node instanceof CastExpr) {
            node = ((CastExpr) node).getExpression();
        }
        ResolvedValueDeclaration valueDecl = resolveVariable(node);
        if (valueDecl != null) {
            return new VariableSymbol(valueDecl, node.getRange().map(r -> r.begin.toString()).orElse(null));
        }
        return null;
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        ResolvedMethodDeclaration methodDecl = symbolResolver.resolveDeclaration(n, ResolvedMethodDeclaration.class);
        if (methodDecl != null) {
            addAllMethods(methodDecl);
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(ReturnStmt n, Void arg) {
        super.visit(n, arg);
        Expression expr = n.getExpression().orElse(null);
        if (expr != null) {
            AbstractNode nodeSymbol = getNodeSymbol(expr);
            if (nodeSymbol != null) {
                Node parentNode = n;
                while (parentNode != null && !(parentNode instanceof MethodDeclaration) && !(parentNode instanceof LambdaExpr)) {
                    parentNode = parentNode.getParentNode().orElse(null);
                }
                if (parentNode != null) {
                    ResolvedMethodDeclaration methodDeclRef = symbolResolver.resolveDeclaration(parentNode, ResolvedMethodDeclaration.class);
                    String location = parentNode.getRange().map(r -> r.begin.toString()).orElse(null);
                    AbstractNode moreGeneralType = new MethodReturnSymbol(methodDeclRef, location);
                    addSubtypingRelation(moreGeneralType, nodeSymbol);
                }
            }
        }
    }

    private boolean isUsedSymbol(ResolvedDeclaration symbol) {
        return symbol != null;
    }

    private ResolvedParameterDeclaration determineParameter(Node node, Expression argument, ResolvedMethodDeclaration symbol) {
        if (node instanceof NodeWithArguments) {
            for (int i = 0; i < ((NodeWithArguments<?>) node).getArguments().size(); i++) {
                if (((NodeWithArguments<?>) node).getArguments().get(i) == argument) {
                    return symbol.getParam(i);
                }
            }
        }
        return null;
    }

    private ResolvedMethodDeclaration resolveMethod(Node node) {
        try {
            return symbolResolver.resolveDeclaration(node, ResolvedMethodDeclaration.class);
        } catch (Exception e) {
            return null;
        }
    }

    private ResolvedValueDeclaration resolveVariable(Node node) {
        try {
            return symbolResolver.resolveDeclaration(node, ResolvedValueDeclaration.class);
        } catch (Exception e) {
            return null;
        }
    }
}
