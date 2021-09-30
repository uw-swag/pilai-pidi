package ca.uwaterloo.swag.models;

public final class EnclNamePosTuple {

    private final String varName;
    private final String functionName;
    private final String fileName;
    private final String definedPosition;

    public EnclNamePosTuple(String varName, String functionName, String fileName,
                            String definedPosition) {
        assert functionName != null;
        this.varName = varName;
        this.functionName = functionName;
        this.fileName = fileName;
        this.definedPosition = definedPosition;
    }

    @Override
    public String toString() {
        String mode = "not_testing";
        String ret_test = "XXXX" + this.varName + "XXXX" + this.functionName + "XXXX" +
            this.fileName.replaceAll(":", "COLON").replaceAll("\\.", "DOT").
                replaceAll("/", "SLASH") + "XXXX" + this.definedPosition;
        //noinspection ConstantConditions
        if (!mode.equals("testing")) {
            return this.varName + "," + this.functionName + "," + this.fileName + ","
                + this.definedPosition;
        } else {
            return ret_test.replaceAll("\\W", "");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EnclNamePosTuple)) {
            return false;
        }
        EnclNamePosTuple other = (EnclNamePosTuple) obj;
        return this.varName.equals(other.varName) && this.functionName.equals(other.functionName) &&
            this.fileName.equals(other.fileName) && this.definedPosition.equals(other.definedPosition);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + this.varName.hashCode();
        result = 31 * result + this.functionName.hashCode();
        result = 31 * result + this.fileName.hashCode();
        result = 31 * result + this.definedPosition.hashCode();
        return result;
    }

    public String varName() {
        return this.varName;
    }

    public String functionName() {
        return this.functionName;
    }

    public String fileName() {
        return this.fileName;
    }

    @SuppressWarnings("unused")
    public String definedPosition() {
        return this.definedPosition;
    }
}
