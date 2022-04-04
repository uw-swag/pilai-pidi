package ca.uwaterloo.swag.pilaipidi.models;

public final class DFGNodeCFunction extends DFGNode {
    private final boolean isLocalCall;
    private final int numArguments;

    public DFGNodeCFunction(String varName, String functionName, String fileName, String definedPosition,
                            boolean isFunctionNamePos, String varType, boolean isLocalCall, int numParameters) {
        super(varName, functionName, fileName, definedPosition, isFunctionNamePos, varType);
        this.isLocalCall = isLocalCall;
        this.numArguments = numParameters;
        this.isCFunctionNode = true;
    }

    public DFGNodeCFunction(String varName, String functionName, String fileName, String definedPosition,
                            String varType, boolean isLocalCall, int numParameters) {
        super(varName, functionName, fileName, definedPosition, varType);
        this.isLocalCall = isLocalCall;
        this.numArguments = numParameters;
        this.isCFunctionNode = true;
    }

    public boolean isLocalCall() {
        return this.isLocalCall;
    }

    public int numArguments() {
        return this.numArguments;
    }
}
