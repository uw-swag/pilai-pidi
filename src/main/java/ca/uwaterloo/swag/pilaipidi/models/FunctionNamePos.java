package ca.uwaterloo.swag.pilaipidi.models;

import java.util.List;

public class FunctionNamePos extends NamePos {

    private final String functionDeclName;
    private List<ArgumentNamePos> arguments;

    public FunctionNamePos(NamePos namePosTextPair, String functionDeclName) {
        super(namePosTextPair.getName(), namePosTextPair.getType(), namePosTextPair.getPos(),
            namePosTextPair.isPointer());
        this.functionDeclName = functionDeclName;
    }

    public String getFunctionDeclName() {
        return functionDeclName;
    }

    public List<ArgumentNamePos> getArguments() {
        return arguments;
    }

    public void setArguments(List<ArgumentNamePos> arguments) {
        this.arguments = arguments;
    }
}
