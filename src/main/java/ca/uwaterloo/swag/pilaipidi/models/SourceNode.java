package ca.uwaterloo.swag.pilaipidi.models;

public class SourceNode {
    public String functionName;
    public boolean possibleParamMatch;
    public boolean possibleFrameworkCall;

    public SourceNode(String functionName, boolean possibleParamMatch, boolean possibleFrameworkCall) {
        this.functionName = functionName;
        this.possibleParamMatch = possibleParamMatch;
        this.possibleFrameworkCall = possibleFrameworkCall;
    }
}
