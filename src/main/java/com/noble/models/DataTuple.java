package com.noble.models;

import com.noble.util.XmlUtil;

public class DataTuple {
    public XmlUtil.DataAccessType accessType = null;
    public String accessPos = null;
    public int inDegree = -1;
    public EnclNamePosTuple node = null;
    public String a = null;
    public String b = null;

    public DataTuple(XmlUtil.DataAccessType accessType, String accessPos) {
        this.accessType = accessType;
        this.accessPos = accessPos;
    }

    @SuppressWarnings("unused")
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

