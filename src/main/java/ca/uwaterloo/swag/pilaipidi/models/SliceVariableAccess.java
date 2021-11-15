package ca.uwaterloo.swag.pilaipidi.models;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class SliceVariableAccess {

    public List<DataAccess> readPositions = new ArrayList<>();
    public List<DataAccess> writePositions = new ArrayList<>();

    public void addWritePosition(DataAccess write) {
        writePositions.add(write);
    }

    public void addReadPosition(DataAccess read) {
        readPositions.add(read);
    }
}
