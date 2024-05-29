package example.pdgextractor;

public class UnkMethodEntryNode {
    private final String methodSymbol;

    public UnkMethodEntryNode(String symbol) {
        this.methodSymbol = symbol;
    }

    public String getMethodSymbol() {
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
        UnkMethodEntryNode that = (UnkMethodEntryNode) obj;
        return methodSymbol.equals(that.methodSymbol);
    }

    @Override
    public String toString() {
        return "Entry";
    }

    public String toSpan() {
        return "";
    }
}
