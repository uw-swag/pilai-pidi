package com.noble.models;

import java.util.ArrayList;

@SuppressWarnings("unused")
public class SliceVariableAccess {
    public ArrayList<Tuple> read_positions = new ArrayList<>();
    public ArrayList<Tuple> write_positions = new ArrayList<>();

    public void addWrite_positions(Tuple write){
        write_positions.add(write);
    }
    public void addRead_positions(Tuple read){
        write_positions.add(read);
    }
}
