package example.pdgextractor;


public abstract class AbstractNode  {

    public abstract boolean isSymbol();

    public abstract boolean isLiteral();

    public abstract String getName();

    public abstract String getLocation();

    public abstract String getType();

    public abstract String toDotString();
}
