package com.noble;

final class Encl_name_pos_tuple {
    private final String var_name;
    private final String function_name;
    private final String file_name;
    private final String defined_position;

    @Override
    public String toString() {
        return this.var_name + "," + this.function_name + "," + this.file_name + "," + this.defined_position;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Encl_name_pos_tuple)) return false;
        Encl_name_pos_tuple other = (Encl_name_pos_tuple) obj;
        return this.var_name.equals(other.var_name) && this.function_name.equals(other.function_name) && this.file_name.equals(other.file_name) && this.defined_position.equals(other.defined_position);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + this.var_name.hashCode();
        result = 31 * result + this.function_name.hashCode();
        result = 31 * result + this.file_name.hashCode();
        result = 31 * result + this.defined_position.hashCode();
        return result;
    }

    public Encl_name_pos_tuple(String var_name, String function_name, String file_name, String defined_position) {
        this.var_name = var_name;
        this.function_name = function_name;
        this.file_name = file_name;
        this.defined_position = defined_position;
    }

    public String getVar_name() {
        return var_name;
    }

    public String getFunction_name() {
        return function_name;
    }

    public String getFile_name() {
        return file_name;
    }

    public String getDefined_position() {
        return defined_position;
    }
}
