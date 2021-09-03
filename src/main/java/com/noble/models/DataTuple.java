package com.noble.models;

import com.noble.util.XmlUtil;

public class DataTuple {
    public XmlUtil.DataAccessType access_type = null;
    public String access_pos = null;
    public int inDegree = -1;
    public EnclNamePosTuple node = null;
    public String a = null;
    public String b = null;

    public DataTuple(XmlUtil.DataAccessType access_type, String access_pos) {
        this.access_type = access_type;
        this.access_pos = access_pos;
    }

    public DataTuple(String a, String b){
        this.a = a;
        this.b = b;
    }

    @SuppressWarnings("unused")
    public DataTuple(int inDegree, EnclNamePosTuple node) {
        this.inDegree = inDegree;
        this.node = node;
    }
}

