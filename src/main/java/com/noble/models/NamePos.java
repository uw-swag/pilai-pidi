package com.noble.models;

public class NamePos {
    private final String name;
    private final String type;
    private final String pos;
    private final boolean isPointer;

    public NamePos(String name, String type, String pos, boolean isPointer) {
        this.name = name;
        this.type = type;
        this.pos = pos;
        this.isPointer = isPointer;
    }

    public String getName() {
        return name;
    }

    public String getPos() {
        return pos;
    }

    @SuppressWarnings("unused")
    public boolean isPointer() {
        return isPointer;
    }

    public String getType() {
        return type;
    }
}

