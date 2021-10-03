package ca.uwaterloo.swag.models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Node;


public class SliceProfile {

    public final String fileName;
    public final String functionName;
    public final String varName;
    public final String typeName;
    public final String definedPosition;
    public final boolean isPointer;
    public final List<SliceVariableAccess> usedPositions = new ArrayList<>();
    public final List<SliceVariableAccess> dataAccess = new ArrayList<>();
    public final Set<NamePos> dependentVars = new HashSet<>();
    public final Set<CFunction> cfunctions = new HashSet<>();
    public final Node functionNode;
    public final boolean isFunctionNameProfile;

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition, boolean isPointer) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
        this.isPointer = isPointer;
        this.functionNode = null;
        this.isFunctionNameProfile = false;
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
        this.isFunctionNameProfile = false;
    }

    public SliceProfile(String fileName, String functionName, String varName, String typeName,
                        String definedPosition, Node functionNode, boolean isFunctionNameProfile) {
        this.fileName = fileName;
        this.functionName = functionName;
        this.varName = varName;
        this.typeName = typeName;
        this.definedPosition = definedPosition;
        this.isPointer = false;
        this.functionNode = functionNode;
        this.isFunctionNameProfile = isFunctionNameProfile;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SliceProfile)) {
            return false;
        }
        SliceProfile other = (SliceProfile) obj;

        assert this.varName != null;
        if (!this.varName.equals(other.varName)) {
            return false;
        }
        return this.functionName.equals(other.functionName) &&
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

    public String toString() {
        return varName + "," + functionName + "," + fileName + ":" + definedPosition;
    }
}
