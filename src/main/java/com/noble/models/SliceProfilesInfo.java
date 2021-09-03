package com.noble.models;

import org.w3c.dom.Node;

import java.util.Hashtable;

public class SliceProfilesInfo {
    public Hashtable<String, SliceProfile> sliceProfiles;
    public Hashtable<NamePos, Node> functionNodes;
    public Node unitNode;

    public SliceProfilesInfo(Hashtable<String, SliceProfile> sliceProfiles, Hashtable<NamePos, Node> functionNodes,
                             Node unitNode) {
        this.sliceProfiles = sliceProfiles;
        this.functionNodes = functionNodes;
        this.unitNode = unitNode;
    }
}

