package com.noble;

public final class NamePos {
    private final String name;
    private final String type;
    private final String pos;
    private final boolean is_pointer;

    public NamePos(String name, String type, String pos, boolean is_pointer) {
        this.name = name;
        this.type = type;
        this.pos = pos;
        this.is_pointer = is_pointer;
    }

    public String getName() {
        return name;
    }

    public String getPos() {
        return pos;
    }

    public boolean is_pointer() {
        return is_pointer;
    }

    public String getType() {
        return type;
    }
}
