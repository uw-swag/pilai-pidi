package ca.uwaterloo.swag.pilaipidi.models;

import ca.uwaterloo.swag.pilaipidi.util.XmlUtil;

public class DataTuple {

    public final XmlUtil.DataAccessType accessType;
    public final NamePos accessVarNamePos;

    public DataTuple(XmlUtil.DataAccessType accessType, NamePos accessVarNamePos) {
        this.accessType = accessType;
        this.accessVarNamePos = accessVarNamePos;
    }
}

