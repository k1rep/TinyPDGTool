package example.pdgextractor;

import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

public class VariableSymbol extends AbstractNode {
    private final ResolvedValueDeclaration symbol;
    private final String location;

    public VariableSymbol(ResolvedValueDeclaration symbol, String location) {
        this.symbol = symbol;
        this.location = location;
    }

    public ResolvedValueDeclaration getSymbol() {
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

    @Override
    public String getName() {
        return symbol.getName();
    }

    @Override
    public String getLocation() {
        return location != null ? location : "Unknown Location";
    }

    @Override
    public String getType() {
        ResolvedType type = symbol.getType();
        return type.describe();
    }

    @Override
    public int hashCode() {
        return symbol.getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VariableSymbol that = (VariableSymbol) obj;
        return symbol.getName().equals(that.symbol.getName()) && getLocation().equals(that.getLocation());
    }

    @Override
    public String toString() {
        return getName() + " : " + getType();
    }

    @Override
    public String toDotString() {
        return getName() + "\n<B>" + getType() + "</B>";
    }
}
