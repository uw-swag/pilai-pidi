package ca.uwaterloo.swag.models;

import java.util.List;

public class FunctionNamePos extends NamePos {

    private final String functionDeclName;
    private List<NamePos> arguments;

    public FunctionNamePos(NamePos namePosTextPair, String functionDeclName) {
        super(namePosTextPair.getName(), namePosTextPair.getType(), namePosTextPair.getPos(),
            namePosTextPair.isPointer());
        this.functionDeclName = functionDeclName;
    }

    public String getFunctionDeclName() {
        return functionDeclName;
    }

    public void setArguments(List<NamePos> arguments) {
        this.arguments = arguments;
    }

    public List<NamePos> getArguments() {
        return arguments;
    }
}
