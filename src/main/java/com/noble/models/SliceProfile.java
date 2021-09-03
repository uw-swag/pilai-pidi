package com.noble.models;

import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Hashtable;

public class SliceProfile {
    public final String fileName;
    public final String functionName;
    public final String varName;
    public final String typeName;
    public final String definedPosition;
    public ArrayList<SliceVariableAccess> usedPositions = new ArrayList<>();
    public NamePos[] dependentVars = new NamePos[]{};
    public Hashtable<String, cFunction> cfunctions = new Hashtable<>();
    public Node functionNode;

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
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
