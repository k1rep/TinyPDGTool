package example.pdgextractor;

public class LiteralSymbol extends AbstractNode {
    private final Object constant;
    private final String location;

    public LiteralSymbol(Object constant, String location) {
        this.constant = constant;
        this.location = location;
    }

    public Object getConstant() {
        return constant;
    }

    @Override
    public boolean isSymbol() {
        return false;
    }

    @Override
    public boolean isLiteral() {
        return true;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getType() {
        if (constant == null) {
            return "null";
        } else if (constant instanceof String) {
            return "string";
        } else if (constant instanceof Integer) {
            return "int";
        } else {
            return constant.getClass().getSimpleName();
        }
    }

    @Override
    public int hashCode() {
        return constant == null ? 0 : constant.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LiteralSymbol that = (LiteralSymbol) obj;
        return (constant == null && that.constant == null) || (constant != null && constant.equals(that.constant));
    }

    @Override
    public String toString() {
        return constant == null ? "const:null" : "const:" + constant + " : " + getType();
    }

    @Override
    public String toDotString() {
        if (constant == null) {
            return "const\n<B>null</B>";
        } else {
            return "const:" + constant + "\n<B>" + getType() + "</B>";
        }
    }
}
