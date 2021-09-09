package com.noble.models;

import org.w3c.dom.Node;

import java.util.*;

public class SliceProfile {
    public final String fileName;
    public final String functionName;
    public final String varName;
    public final String typeName;
    public final String definedPosition;
    public final List<SliceVariableAccess> usedPositions = new ArrayList<>();
    public final Set<NamePos> dependentVars = new HashSet<>();
    public final Set<CFunction> cfunctions = new HashSet<>();
    public final Node functionNode;

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
        this.functionNode = null;
    }

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition, Node functionNode) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
        this.functionNode = functionNode;
    }
}
