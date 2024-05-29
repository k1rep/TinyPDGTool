package example.pdgextractor;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;

import java.util.Optional;

public class MethodEntryNode {
    private final ResolvedMethodLikeDeclaration methodSymbol;

    public MethodEntryNode(ResolvedMethodLikeDeclaration symbol) {
        this.methodSymbol = symbol;
    }

    public ResolvedMethodLikeDeclaration getMethodSymbol() {
        return methodSymbol;
    }

    @Override
    public int hashCode() {
        return methodSymbol.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MethodEntryNode that = (MethodEntryNode) obj;
        return methodSymbol.equals(that.methodSymbol);
    }

    @Override
    public String toString() {
        return "Entry";
    }

    public String toSpan() {
        if (methodSymbol instanceof ResolvedMethodDeclaration) {
            Optional<MethodDeclaration> method = ((ResolvedMethodDeclaration) methodSymbol).toAst();
            if(method.isPresent()) {
                if (method.get().getRange().isPresent()) {
                    Range range = method.get().getRange().get();
                    return String.format("%d-%d", range.begin.line, range.end.line);
                }
            }
        }
        return "";
    }
}
