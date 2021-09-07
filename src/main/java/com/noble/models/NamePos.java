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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NamePos)) {
            return false;
        }
        NamePos other = (NamePos) obj;
        return this.name.equals(other.name) && this.type.equals(other.type) &&
                this.pos.equals(other.pos) && this.isPointer == other.isPointer;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + this.name.hashCode();
        result = 31 * result + this.type.hashCode();
        result = 31 * result + this.pos.hashCode();
        return result;
    }

}

