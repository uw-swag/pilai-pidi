package com.noble.models;

import org.w3c.dom.Node;

import java.util.ArrayList;

public final class CFunction {

    private final int argPosIndex;
    private final String cfunctionPos;
    private final String cFunctionName;
    private final Node cFunctionNode;
    private final ArrayList<ArgumentNamePos> funcArgs;

    public CFunction(int argPosIndex, String cFunctionName, String cfunctionPos, Node cFunctionNode) {
        this.argPosIndex = argPosIndex;
        this.cFunctionName = cFunctionName;
        this.cFunctionNode = cFunctionNode;
        this.cfunctionPos = cfunctionPos;
        this.funcArgs = null;
    }

    public CFunction(int argPosIndex, String cFunctionName, String cfunctionPos, Node cFunctionNode,
                     ArrayList<ArgumentNamePos> funcArgs) {
        this.argPosIndex = argPosIndex;
        this.cFunctionName = cFunctionName;
        this.cFunctionNode = cFunctionNode;
        this.cfunctionPos = cfunctionPos;
        this.funcArgs = funcArgs;
    }

    public CFunction(int argPosIndex, String cFunctionName, Node cFunctionNode) {
        this.argPosIndex = argPosIndex;
        this.cFunctionName = cFunctionName;
        this.cFunctionNode = cFunctionNode;
        this.cfunctionPos = null;
        this.funcArgs = null;
    }

    public Node getCFunctionNode() {
        return cFunctionNode;
    }

    public String getCFunctionName() {
        return cFunctionName;
    }

    public int getArgPosIndex() {
        return argPosIndex;
    }

    public ArrayList<ArgumentNamePos> getFuncArgs() {
        return funcArgs;
    }

    public String getCfunctionPos() {
        return cfunctionPos;
    }
}
