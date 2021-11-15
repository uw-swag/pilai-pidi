package ca.uwaterloo.swag.pilaipidi.models;

public class DataAccess {

    public final DataAccessType accessType;
    public final NamePos accessedExprNamePos;
    public final Value accessedExprValue;

    public DataAccess(DataAccessType accessType, NamePos accessedExprNamePos) {
        this.accessType = accessType;
        this.accessedExprNamePos = accessedExprNamePos;
        this.accessedExprValue = null;
    }

    public DataAccess(DataAccessType accessType, NamePos accessedExprNamePos, Value accessedExprValue) {
        this.accessType = accessType;
        this.accessedExprNamePos = accessedExprNamePos;
        this.accessedExprValue = accessedExprValue;
    }

    public enum DataAccessType {
        @SuppressWarnings("unused") BUFFER_READ, BUFFER_WRITE, DATA_WRITE
    }
}


