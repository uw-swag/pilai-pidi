package ca.uwaterloo.swag.pilaipidi.models;

public class Value {

    public final int literal;
    public final SliceProfile referencedProfile;

    public Value(int literal) {
        this.literal = literal;
        this.referencedProfile = null;
    }

    public Value(SliceProfile referencedProfile) {
        this.literal = 0;
        this.referencedProfile = referencedProfile;
    }

    public boolean isReferenced() {
        return this.referencedProfile != null;
    }
}
