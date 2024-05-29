package example.pdgextractor;

import com.github.javaparser.Range;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.ast.Node;

public class MethodExitNode {
    private final ResolvedMethodDeclaration methodSymbol;

    public MethodExitNode(ResolvedMethodDeclaration symbol) {
        this.methodSymbol = symbol;
    }

    public ResolvedMethodDeclaration getMethodSymbol() {
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
        MethodExitNode that = (MethodExitNode) obj;
        return methodSymbol.equals(that.methodSymbol);
    }

    @Override
    public String toString() {
        return "Exit";
    }

    public String toSpan() {
        if (methodSymbol instanceof ResolvedMethodDeclaration) {
            Node method = (Node) methodSymbol;
            if (method.getRange().isPresent()) {
                Range range = method.getRange().get();
                return String.format("%d-%d", range.begin.line, range.end.line);
            }
        }
        return "";
    }
}
