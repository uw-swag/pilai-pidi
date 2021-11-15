package ca.uwaterloo.swag.pilaipidi.models;

public class DataTuple {

    public final DataAccessType accessType;
    public final NamePos accessVarNamePos;

    public DataTuple(DataAccessType accessType, NamePos accessVarNamePos) {
        this.accessType = accessType;
        this.accessVarNamePos = accessVarNamePos;
    }

    public enum DataAccessType {
        @SuppressWarnings("unused") BUFFER_READ, BUFFER_WRITE, DATA_READ, DATA_WRITE
    }
}


