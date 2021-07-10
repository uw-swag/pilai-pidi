package com.noble;

import java.util.ArrayList;

public class SliceVariableAccess {
    public ArrayList<Tuple> read_positions;
    public ArrayList<Tuple> write_positions;

    public void addWrite_positions(Tuple write){
        write_positions.add(write);
    }
    public void addRead_positions(Tuple read){
        write_positions.add(read);
    }
}
