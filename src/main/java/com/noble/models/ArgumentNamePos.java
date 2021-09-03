package com.noble.models;

public class ArgumentNamePos extends NamePos {
    private final boolean isOptional;

    public ArgumentNamePos(String name, String type, String pos, boolean is_pointer, boolean isOptional) {
        super(name, type, pos, is_pointer);
        this.isOptional = isOptional;
    }

    public ArgumentNamePos(NamePos namePosTextPair, boolean isOptional) {
        super(namePosTextPair.getName(), namePosTextPair.getType(), namePosTextPair.getPos(),
                namePosTextPair.is_pointer());
        this.isOptional = isOptional;
    }

    public boolean isOptional() {
        return isOptional;
    }
}
