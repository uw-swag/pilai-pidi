package ca.uwaterloo.swag.models;

import org.w3c.dom.Node;

import java.util.*;

public class SliceProfile {
    public final String fileName;
    public final String functionName;
    public final String varName;
    public final String typeName;
    public final String definedPosition;
    public final boolean isPointer;
    public final List<SliceVariableAccess> usedPositions = new ArrayList<>();
    public final Set<NamePos> dependentVars = new HashSet<>();
    public final Set<CFunction> cfunctions = new HashSet<>();
    public final Node functionNode;

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition, boolean isPointer) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
        this.isPointer = isPointer;
        this.functionNode = null;
    }

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition, boolean isPointer, Node functionNode) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
        this.isPointer = isPointer;
        this.functionNode = functionNode;
    }
}
