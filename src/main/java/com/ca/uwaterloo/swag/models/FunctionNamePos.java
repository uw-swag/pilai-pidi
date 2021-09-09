package com.ca.uwaterloo.swag.models;

public class FunctionNamePos extends NamePos {
    private final String functionDeclName;

    public FunctionNamePos(NamePos namePosTextPair, String functionDeclName) {
        super(namePosTextPair.getName(), namePosTextPair.getType(), namePosTextPair.getPos(),
                namePosTextPair.isPointer());
        this.functionDeclName = functionDeclName;
    }

    public String getFunctionDeclName() {
        return functionDeclName;
    }
}
