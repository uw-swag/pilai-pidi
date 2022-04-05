package ca.uwaterloo.swag.pilaipidi.models;

public class NamePos {

    private final String name;
    private final String type;
    private final String pos;
    private final boolean isPointer;
    private final boolean isBuffer;
    private final NamePos bufferSize;

    public NamePos(String name, String type, String pos, boolean isPointer) {
        this(name, type, pos, isPointer, false);
    }

    public NamePos(String name, String type, String pos, boolean isPointer, boolean isLocalCall) {
        this.name = name;
        this.type = type;
        this.pos = pos;
        this.isPointer = isPointer;
        this.isBuffer = false;
        this.bufferSize = null;
    }

    public NamePos(String name, String type, String pos, boolean isPointer, boolean isBuffer, NamePos bufferSize) {
        this.name = name;
        this.type = type;
        this.pos = pos;
        this.isPointer = isPointer;
        this.isBuffer = isBuffer;
        this.bufferSize = bufferSize;
    }

    public String getName() {
        return name;
    }

    public String getPos() {
        return pos;
    }

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

    public boolean isBuffer() {
        return isBuffer;
    }

    public NamePos getBufferSize() {
        return bufferSize;
    }

    public static class DefaultNamePos extends NamePos {

        public DefaultNamePos() {
            super("", "", "", false);
        }
    }

}

