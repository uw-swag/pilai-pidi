package com.ca.uwaterloo.swag.models;

import java.util.ArrayList;

@SuppressWarnings("unused")
public class SliceVariableAccess {
    public ArrayList<DataTuple> readPositions = new ArrayList<>();
    public ArrayList<DataTuple> writePositions = new ArrayList<>();

    public void addWritePosition(DataTuple write){
        writePositions.add(write);
    }
    public void addReadPosition(DataTuple read){
        writePositions.add(read);
    }
}
