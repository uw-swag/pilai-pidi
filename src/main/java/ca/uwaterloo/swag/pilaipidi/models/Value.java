package ca.uwaterloo.swag.pilaipidi.models;

public class Value {

    public final int literal;
    public final SliceProfile referencedProfile;
    private Value bufferSize = null;

    public Value(int literal) {
        this.literal = literal;
        this.referencedProfile = null;
    }

    public Value(SliceProfile referencedProfile) {
        this.literal = 0;
        this.referencedProfile = referencedProfile;
    }

    public Value(int literal, SliceProfile referencedProfile) {
        this.literal = literal;
        this.referencedProfile = referencedProfile;
    }

    public boolean isReferenced() {
        return this.referencedProfile != null;
    }

    public Value getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(Value bufferSize) {
        this.bufferSize = bufferSize;
    }
}
