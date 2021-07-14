package com.noble;

import org.w3c.dom.Node;

import java.util.Hashtable;

public class SliceProfilesInfo {
    Hashtable<String, SliceProfile> slice_profiles;
    Hashtable<NamePos, Node> function_nodes;
//    List<Node> function_nodes;
    Node unit_node;
    public SliceProfilesInfo(Hashtable<String, SliceProfile> slice_profiles, Hashtable<NamePos, Node> function_nodes, Node unit_node) {
        this.slice_profiles = slice_profiles;
        this.function_nodes = function_nodes;
        this.unit_node = unit_node;
    }
}

