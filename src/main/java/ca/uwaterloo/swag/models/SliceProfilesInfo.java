package ca.uwaterloo.swag.models;

import java.util.List;
import java.util.Map;
import org.w3c.dom.Node;

public class SliceProfilesInfo {

    public final Map<String, SliceProfile> sliceProfiles;
    public final Map<FunctionNamePos, Node> functionNodes;
    public final Map<String, List<FunctionNamePos>> functionDeclMap;
    public final Node unitNode;

    public SliceProfilesInfo(Map<String, SliceProfile> sliceProfiles,
                             Map<FunctionNamePos, Node> functionNodes,
                             Map<String, List<FunctionNamePos>> functionDeclMap,
                             Node unitNode) {
        this.sliceProfiles = sliceProfiles;
        this.functionNodes = functionNodes;
        this.functionDeclMap = functionDeclMap;
        this.unitNode = unitNode;
    }


}

