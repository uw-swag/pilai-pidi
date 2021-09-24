package ca.uwaterloo.swag.models;

import ca.uwaterloo.swag.util.XmlUtil;

public class DataTuple {

    public final XmlUtil.DataAccessType accessType;
    public final NamePos accessVarNamePos;

    public DataTuple(XmlUtil.DataAccessType accessType, NamePos accessVarNamePos) {
        this.accessType = accessType;
        this.accessVarNamePos = accessVarNamePos;
    }
}

