package ca.uwaterloo.swag.models;

public class ArgumentNamePos extends NamePos {

    private final boolean isOptional;

    public ArgumentNamePos(String name, String type, String pos, boolean isPointer, boolean isOptional) {
        super(name, type, pos, isPointer);
        this.isOptional = isOptional;
    }

    public ArgumentNamePos(NamePos namePosTextPair, boolean isOptional) {
        super(namePosTextPair.getName(), namePosTextPair.getType(), namePosTextPair.getPos(),
            namePosTextPair.isPointer());
        this.isOptional = isOptional;
    }

    public boolean isOptional() {
        return isOptional;
    }
}
