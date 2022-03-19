package ca.uwaterloo.swag.pilaipidi.models;

import java.util.ArrayList;

public class DFGNode {

    private final String varName;
    private final String functionName;
    private final String fileName;
    private final String definedPosition;
    private final boolean isFunctionNamePos;
    private final String varType;
    protected boolean isCFunctionNode;

    public DFGNode(String varName, String functionName, String fileName,
                   String definedPosition, String varType) {
        assert functionName != null;
        this.varName = varName;
        this.functionName = functionName;
        this.fileName = fileName;
        this.definedPosition = definedPosition;
        this.isFunctionNamePos = false;
        this.varType = varType;
        this.isCFunctionNode = false;
    }

    public DFGNode(String varName, String functionName, String fileName,
                   String definedPosition, boolean isFunctionNamePos, String varType) {
        assert functionName != null;
        this.varName = varName;
        this.functionName = functionName;
        this.fileName = fileName;
        this.definedPosition = definedPosition;
        this.isFunctionNamePos = isFunctionNamePos;
        this.varType = varType;
        this.isCFunctionNode = false;
    }

    @Override
    public String toString() {
        return this.varName + "," + this.functionName + "," + this.fileName + "," + this.definedPosition;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DFGNode)) {
            return false;
        }
        DFGNode other = (DFGNode) obj;
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

    public String varType() {
        return this.varType;
    }

    @SuppressWarnings("unused")
    public String definedPosition() {
        return this.definedPosition;
    }

    public boolean isFunctionNamePos() {
        return isFunctionNamePos;
    }

    public boolean isCFunctionNode() {
        return isCFunctionNode;
    }
}
