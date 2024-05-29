package example.pdgextractor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;



public class MethodReturnSymbol extends AbstractNode {
    private final ResolvedMethodDeclaration symbol;
    private final String location;

    public MethodReturnSymbol(ResolvedMethodDeclaration symbol, String location){
        this.symbol = symbol;
        this.location = location;
    }

    public ResolvedMethodDeclaration getSymbol() {
        return symbol;
    }

    @Override
    public boolean isSymbol() {
        return true;
    }

    @Override
    public boolean isLiteral() {
        return false;
    }

    public boolean isConstructor() {
        return symbol.getName().equals("<init>");
    }

    @Override
    public String getName() {
        return symbol.getName().equals("<init>") ? symbol.declaringType().getName() : symbol.getName();
    }

    @Override
    public String getLocation() {
        return location != null ? location : "Unknown Location";
    }

    @Override
    public String getType() {
        ResolvedType returnType = symbol.getReturnType();
        return returnType != null ? returnType.describe() : "Unknown";
    }

    @Override
    public int hashCode() {
        return symbol.getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MethodReturnSymbol that = (MethodReturnSymbol) obj;
        return symbol.getName().equals(that.symbol.getName()) && getLocation().equals(that.getLocation());
    }

    @Override
    public String toString() {
        return symbol.getQualifiedSignature() + " : " + getType();
    }

    @Override
    public String toDotString() {
        return "*" + getName() + "\n<B>" + getType() + "</B>";
    }
}
