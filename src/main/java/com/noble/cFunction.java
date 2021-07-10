package com.noble;

import org.w3c.dom.Node;

import java.util.List;

final class cFunction {
    private final int arg_pos_index;
    private final String current_function_name;
    private final String current_function_pos;
    private final Node current_function_node;
    private final List<Node> func_args;

    cFunction(int arg_pos_index, String current_function_name, String current_function_pos, Node current_function_node) {
        this.arg_pos_index = arg_pos_index;
        this.current_function_name = current_function_name;
        this.current_function_node = current_function_node;
        this.current_function_pos = current_function_pos;
        this.func_args = null;
    }

    public cFunction(int arg_pos_index, String current_function_name, String current_function_pos,  Node current_function_node, List<Node> param) {
        this.arg_pos_index = arg_pos_index;
        this.current_function_name = current_function_name;
        this.current_function_node = current_function_node;
        this.current_function_pos = current_function_pos;
        this.func_args = param;
    }

    public cFunction(int arg_pos_index, String current_function_name, Node current_function_node) {
        this.arg_pos_index = arg_pos_index;
        this.current_function_name = current_function_name;
        this.current_function_node = current_function_node;
        this.current_function_pos = null;
        this.func_args = null;
    }

    public Node getCurrent_function_node() {
        return current_function_node;
    }

    public String getCurrent_function_name() {
        return current_function_name;
    }

    public int getArg_pos_index() {
        return arg_pos_index;
    }

    public List<Node> getFunc_args() { return func_args; }

    public String getCurrent_function_pos() {
        return current_function_pos;
    }
}
