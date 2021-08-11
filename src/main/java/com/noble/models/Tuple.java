package com.noble.models;

import com.noble.util.XmlUtil;

public class Tuple {
    public XmlUtil.DataAccessType access_type = null;
    public String access_pos = null;
    public int inDegree = -1;
    public Encl_name_pos_tuple node = null;
    public String a = null;
    public String b = null;

    public Tuple(XmlUtil.DataAccessType access_type, String access_pos) {
        this.access_type = access_type;
        this.access_pos = access_pos;
    }

    public Tuple(String a, String b){
        this.a = a;
        this.b = b;
    }

    @SuppressWarnings("unused")
    public Tuple(int inDegree, Encl_name_pos_tuple node) {
        this.inDegree = inDegree;
        this.node = node;
    }
}

