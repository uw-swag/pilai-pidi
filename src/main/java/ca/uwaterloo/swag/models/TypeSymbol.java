package ca.uwaterloo.swag.models;

public class TypeSymbol {

    public final String name;
    public final String type;
    public final TypeSymbol parent;
    public final String position;

    public TypeSymbol(String name, String type, TypeSymbol parent, String position) {
        this.name = name;
        this.type = type;
        this.parent = parent;
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TypeSymbol)) {
            return false;
        }

        TypeSymbol other = (TypeSymbol) obj;
        assert this.name != null;
        if (!this.name.equals(other.name)) {
            return false;
        }

        if (!this.type.equals(other.type)) {
            return false;
        }

        if (this.parent == null && other.parent == null) {
            return true;
        }

        if (this.parent != null && other.parent != null) {
            return this.parent.equals(other.parent);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + this.name.hashCode();
        result = 31 * result + this.type.hashCode();
        return result;
    }
}
