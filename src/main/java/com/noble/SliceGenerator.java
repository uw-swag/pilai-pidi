package com.noble;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.noble.util.XmlUtil.*;
import static com.noble.util.XmlUtil.getNamePosTextPair;

public class SliceGenerator {
    String GLOBAL;
    String src_type;
    String identifier_separator;
    Node unit_node;
    Hashtable<String, SliceProfile> slice_profiles;
    Hashtable<String, Hashtable<String, SliceProfile>>  local_variables;
    Hashtable<String, Hashtable<String, SliceProfile>> global_variables;
    String [] declared_pointers;
    String file_name;
    String current_function_name;
    Node current_function_node;

    enum DataAccessType
    {
        BUFFER_READ, BUFFER_WRITE
    }

    public SliceGenerator(Node unit_node, String file_name, Hashtable<String, SliceProfile> slice_profiles){
        this.unit_node = unit_node;
        this.slice_profiles = slice_profiles;
        this.local_variables = new Hashtable<>();
        this.global_variables = new Hashtable<>();
        this.declared_pointers = new String[]{};
        this.file_name = file_name;
        this.current_function_name = "";
        this.current_function_node = null;
        this.GLOBAL = "GLOBAL";
        this.identifier_separator = "[^\\w]+";
        this.src_type = null;
    }

    public void generate(){
        String langAttribute = this.unit_node.getAttributes().getNamedItem("language").getNodeValue();
        src_type = langAttribute;
        if (langAttribute.equals("Java")) {
            this.analyzeJavaSource();
        }
        else if (langAttribute.equals("C++") || langAttribute.equals("C")) {
            this.analyzeCPPSource(unit_node);
        }
    }

    private void analyzeJavaSource() {
        for(Node class_node:find_all_nodes(this.unit_node,"class"))
            this.analyzeJavaClass(class_node);
    }

    public void analyzeCPPSource(Node unit_node) {
        if(unit_node==null)
            unit_node= this.unit_node;
        NodeList doc = unit_node.getChildNodes();
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            switch (node_tag) {
                case "decl_stmt":
                    this.analyzeGlobalDecl(node);
                    break;
                case "extern":
                    this.analyzeExternFunction(node);
                    break;
                case "namespace":
                    this.analyzeNamespace(node);
                    break;
//                case "class":
//                    this.analyzeCppClass(node);
//                    break;
                case "struct":
                    this.analyzeStruct(node);
                    break;
//                case "typedef":
//                    this.analyzeTypeDef(node);
//                    break;
                case "function_decl":
                case "function":
                    this.local_variables = new Hashtable<>();
                    this.analyzeFunction(node);
                    break;
            }
        }
    }

    private void analyzeNamespace(Node namespace_node){
        List<Node> block = getNodeByName(namespace_node, "block");
        analyzeCPPSource(block.get(0));
    }

    private void analyzeStruct(Node struct_node){
//        List<Node> block = getNodeByName(struct_node, "block");
//      TODO analyze struct body
        NamePos struct_type_name_pos = getNamePosTextPair(struct_node);
        String struct_type_name = struct_type_name_pos.getType();
        if (struct_type_name.equals("")) return;
        Node struct_var_name_pos_temp = getNodeByName(struct_node, "decl").get(0);
        NamePos struct_var_name_pos = getNamePosTextPair(struct_var_name_pos_temp);
        if(struct_var_name_pos.getName().equals("")) return;
        String struct_var_name = struct_var_name_pos.getName();
        String struct_pos = struct_var_name_pos.getPos();
        String slice_key = struct_var_name + "%" + struct_pos + "%" + GLOBAL + "%" + file_name;
        SliceProfile profile = new SliceProfile(file_name, GLOBAL, struct_var_name, struct_type_name, struct_pos);
        slice_profiles.put(slice_key,profile);
        Hashtable<String, SliceProfile> structProfile = new Hashtable<>();
        structProfile.put(struct_var_name,profile);
        global_variables.put(struct_var_name,structProfile);
    }

//    private void analyzeCPPClass(Node class_node){
//        TODO analyze class content
//    }

    private void analyzeJavaClass(Node class_node) {
        NodeList nodeList = class_node.getChildNodes();
        NodeList doc= null;
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node node = nodeList.item(count);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.hasChildNodes()) {
                doc = node.getChildNodes();
            }
        }
        assert doc!=null;
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            switch (node_tag) {
                case "decl_stmt":
                    this.analyzeGlobalDecl(node);
                    break;
                case "static":
                    this.analyzeStaticBlock(node);
                    break;
                case "class":
                    this.analyzeJavaClass(node);
                    break;
                case "function_decl":
                case "function":
                case "constructor":
                    this.local_variables = new Hashtable<>();
                    this.analyzeFunction(node);
                    break;
            }
        }
    }

    private void analyzeGlobalDecl(Node nodeTemp) {
        NamePos namePos = getNamePosTextPair(nodeTemp);
        String slice_key = namePos.getName() + "%" + namePos.getPos() + "%" + this.GLOBAL + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.GLOBAL, namePos.getName(), namePos.getType(), namePos.getPos());
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(),slice_profile);
        this.global_variables.put(namePos.getName(),nameProfile);
    }

    private void analyzeStaticBlock(Node static_block) {
        List<Node> block = getNodeByName(static_block, "block");
        current_function_name = GLOBAL;
        current_function_node = static_block;
        analyzeBlock(block.get(0));
        current_function_name = null;
        current_function_node = null;
    }

    private void analyzeExternFunction(Node extern_node) {
        NodeList doc = extern_node.getChildNodes();
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            if (node_tag.equals("function_decl") || node_tag.equals("function")){
                this.local_variables= new Hashtable<>();
                this.analyzeFunction(node);
            }
        }
    }

    private void analyzeFunction(Node function) {
        NamePos function_name = getNamePosTextPair(function);
        this.current_function_name = function_name.getName();
        this.current_function_node = function;
        List<Node> param = getNodeByName(function, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        List<Node> block_list = getNodeByName(function, "block");
        if(block_list.size()>0) {
            Node block = block_list.get(0);
            analyzeBlock(block);
        }
        this.current_function_name = null;
        this.current_function_node = null;
    }

    private void analyzeBlock(Node block) {
        NodeList iterBlock = getNodeByName(block, "block_content").get(0).getChildNodes();
        for(Node stmt : asList(iterBlock)){
            String stmt_tag = stmt.getNodeName();
            switch (stmt_tag) {
                case "expr_stmt":
                    analyzeExprStmt(stmt);
                    break;
                case "decl_stmt":
                    analyzeDeclStmt(stmt);
                    break;
                case "if_stmt":
                    analyzeIfStmt(stmt);
                    break;
                case "for":
                    analyzeForStmt(stmt);
                    break;
                case "while":
                    analyzeWhileStmt(stmt);
                    break;
                case "return":
                    analyzeReturnStmt(stmt);
                    break;
                case "try":
                    analyzeTryBlock(stmt);
                    break;
                case "switch":
                    analyzeSwitchStmt(stmt);
                    break;
                case "case":
                    analyzeCaseStmt(stmt);
                    break;
            }
        }
    }

    private void analyzeDeclStmt(Node stmt) {
        analyzeDecl(getNodeByName(stmt,"decl").get(0));
    }

    private void analyzeDecl(Node decl){
        NamePos namePos = getNamePosTextPair(decl);
        String slice_key = namePos.getName() + "%" + namePos.getPos() + "%" + this.current_function_name + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, namePos.getName(), namePos.getType(), namePos.getPos(), this.current_function_node);
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(),slice_profile);
        local_variables.put(namePos.getName(),nameProfile);
        List<Node> expr_temp = getNodeByName(decl, "expr");
        if(expr_temp.size() <1) return;
        List<Node> init_expr = asList(expr_temp.get(0).getChildNodes());

        for(Node expr: init_expr){
            NamePos expr_var_name_pos_pair = analyzeExpr(expr);
            String expr_var_name = expr_var_name_pos_pair.getName();
            String expr_var_pos = expr_var_name_pos_pair.getPos();
            if(expr_var_name.equals("")) return;
            if (local_variables.containsKey(expr_var_name)){
                updateDVarSliceProfile(namePos.getName(), expr_var_name, "local_variables");
            }
            else if(global_variables.containsKey(expr_var_name)){
                updateDVarSliceProfile(namePos.getName(), expr_var_name, "global_variables");
            }
        }
        List<Node> argument_list_temp = getNodeByName(decl, "argument_list");
        if(argument_list_temp.size()<1) return;
        List<Node> argument_list = getNodeByName(argument_list_temp.get(0),"argument");
        for (Node arg_expr: argument_list){
            List<Node> expr_temp_f = getNodeByName(arg_expr, "expr");
            if(expr_temp_f.size()<1)continue;
            for(Node expr: asList(expr_temp_f.get(0).getChildNodes())){
                NamePos expr_var_name_pos_pair = analyzeExpr(expr);
                String expr_var_name = expr_var_name_pos_pair.getName();
                String expr_var_pos = expr_var_name_pos_pair.getPos();
                if(expr_var_name.equals("")) return;
                if (local_variables.containsKey(expr_var_name)){
                    updateDVarSliceProfile(namePos.getName(), expr_var_name, "local_variables");
                }
                else if(global_variables.containsKey(expr_var_name)){
                    updateDVarSliceProfile(namePos.getName(), expr_var_name, "global_variables");
                }
            }
        }

    }

    private NamePos analyzeExpr(Node expr_e) {
            String expr_tag = expr_e.getNodeName();
            switch (expr_tag) {
                case "literal":
                    return analyzeLiteralExpr(expr_e);
                case "operator":
                    analyzeOperatorExpr(expr_e);
                    break;
                case "ternary":
                    analyzeTernaryExpr(expr_e);
                    break;
                case "call":
                    return analyzeCallExpr(expr_e);
                case "cast":
                    analyzeCastExpr(expr_e);
                    break;
                case "name":
                    return getNamePosTextPair(expr_e);
            }
        return new NamePos("","","",false);
    }

    private NamePos analyzeLiteralExpr(Node literal) {
        String literal_val = literal.getTextContent();
        String type_name = literal.getAttributes().getNamedItem("type").getNodeValue();
        String pos = getNodePos(literal);
        String slice_key = literal_val + "%" + pos + "%" + current_function_name + "%" + file_name;
        SliceProfile profile = new SliceProfile(file_name, current_function_name, literal_val, type_name, pos,current_function_node);
        slice_profiles.put(slice_key,profile);
        Hashtable<String, SliceProfile> lvar = new Hashtable<>();
        lvar.put(literal_val, profile);
        local_variables.put(literal_val,lvar);
        return new NamePos(literal_val,type_name,pos,false);
    }

    private void analyzeOperatorExpr(Node expr) {
//      TODO no effect
        NamePos returnable = new NamePos(expr.getTextContent(),"",getNodePos(expr),false);
    }

    private void analyzeTryBlock(Node stmt) {
        List<Node> block;
        block = getNodeByName(stmt,"block");
        if(isIndexOutOfBounds(block,0)) return;
        analyzeBlock(block.get(0));
        block = getNodeByName(stmt,"catch");
        if(isIndexOutOfBounds(block,0)) return;
        analyzeCatchBlock(block.get(0));
    }

    private void analyzeCatchBlock(Node catch_block) {
        List<Node> param = getNodeByName(catch_block, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        List<Node> block = getNodeByName(catch_block, "block");
        if(isIndexOutOfBounds(block,0)) return;
        analyzeBlock(block.get(0));
    }

    private void analyzeSwitchStmt(Node stmt) {
        analyzeConditionBlock(stmt);
    }

    private void analyzeCaseStmt(Node stmt) {
        analyzeCompoundExpr(stmt);
    }

    private NamePos analyzeCallExpr(Node call) {
        NamePos cfunction_details = getNamePosTextPair(call);
        String cfunction_name = cfunction_details.getName();
        String cfunction_pos = cfunction_details.getPos();

        String cfunction_identifier = call.getTextContent().split(identifier_separator)[0];
        if(!local_variables.containsKey(cfunction_identifier) || !global_variables.containsKey(cfunction_identifier))
        {
            String cfunction_slice_identifier = cfunction_identifier + "%" + cfunction_pos;
            String cfunc_slice_key = cfunction_slice_identifier + "%" + current_function_name + "%" + file_name;
            SliceProfile cfunction_profile = new SliceProfile(file_name, current_function_name, cfunction_identifier, null, cfunction_pos, current_function_node);
            slice_profiles.put(cfunc_slice_key,cfunction_profile);
            Hashtable<String, SliceProfile> cfprofile = new Hashtable<>();
            cfprofile.put(cfunction_identifier, cfunction_profile);
            local_variables.put(cfunction_identifier,cfprofile);
        }

        List<Node> argument_list = getNodeByName(getNodeByName(call, "argument_list").get(0),"argument");
        int arg_pos_index = 0;
        for(Node arg_expr:argument_list){
         arg_pos_index = arg_pos_index + 1;
            for(Node expr:asList(getNodeByName(arg_expr,"expr").get(0).getChildNodes())){
                NamePos var_name_pos_pair = analyzeExpr(expr);
                String var_name = var_name_pos_pair.getName();
                String var_pos = var_name_pos_pair.getPos();
                String slice_key = var_name + "%" + var_pos + "%" + this.current_function_name + "%" + this.file_name;
                if(var_name.equals("")) return var_name_pos_pair;
                if (local_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name,cfunction_name, cfunction_pos,arg_pos_index,"local_variables",slice_key);
                    if(!cfunction_identifier.equals("")) updateDVarSliceProfile(cfunction_identifier, var_name, "local_variables");
                }
                else if(global_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name,cfunction_name, cfunction_pos,arg_pos_index,"global_variables",slice_key);
                    if(!cfunction_identifier.equals("")) updateDVarSliceProfile(cfunction_identifier, var_name, "global_variables");
                }
                else if(is_literal_expr(expr)){
                    String type_name = var_name_pos_pair.getType();
                    SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, var_name, type_name, var_pos,this.current_function_node);
                    cFunction cFun = new cFunction(arg_pos_index,current_function_name ,current_function_node);
                    slice_profile.cfunctions.put(cfunction_name,cFun);
                    slice_profiles.put(slice_key,slice_profile);
                }
            }
        }
        return new NamePos(cfunction_identifier,"",cfunction_pos,false);
    }

    private void analyzeCastExpr(Node cast_expr) {
        List<Node> argument_list = getNodeByName(getNodeByName(cast_expr, "argument_list").get(0),"argument");
        for(Node arg_expr:argument_list){
            for(Node expr:asList(getNodeByName(arg_expr,"expr").get(0).getChildNodes())){
                analyzeExpr(expr);
            }
        }
    }

    private void updateCFunctionsSliceProfile(String var_name, String cfunction_name, String cfunction_pos, int arg_pos_index, String slice_variables_string, String slice_key) {
        Hashtable<String, Hashtable<String, SliceProfile>> slice_variables;
        if(slice_variables_string.equals("local_variables")) slice_variables = local_variables;
        else slice_variables = global_variables;
        SliceProfile slice_profile = slice_variables.get(var_name).get(var_name);
        cFunction cFun = new cFunction(arg_pos_index, current_function_name, cfunction_pos,current_function_node);
        slice_profile.cfunctions.put(cfunction_name,cFun);
        slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> body = slice_variables.get(var_name);
        body.put(var_name, slice_profile);
        if(slice_variables_string.equals("local_variables")) local_variables.put(var_name, body);
        else global_variables.put(var_name, body);
    }

    private void analyzeIfStmt(Node stmt) {
        analyzeIfBlock(getNodeByName(stmt,"if").get(0));
        List<Node> elseB = getNodeByName(stmt, "else");
        if(elseB.size()>0)
        analyzeElseBlock(elseB.get(0));

    }

    private void analyzeIfBlock(Node stmt){
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionBlock(Node stmt) {
        List<Node> condition = getNodeByName(stmt, "condition");
        if(condition.size()>0) analyzeCompoundExpr(condition.get(0));
        List<Node> block = getNodeByName(stmt, "block");
        if(block.size()>0) analyzeBlock(block.get(0));

    }

    private void analyzeReturnStmt(Node stmt) {
        List<Node> expr = getNodeByName(stmt, "expr");
        if(expr.size()>0)
        analyzeExpr(expr.get(0).getChildNodes().item(0));
    }

    private void analyzeElseBlock(Node node) {
        List<Node> block = getNodeByName(node, "block");
        if(block.size()>0)
        analyzeBlock(block.get(0));
    }

    private void analyzeForStmt(Node stmt) {
        analyzeControl(getNodeByName(stmt,"control").get(0));
        analyzeBlock(getNodeByName(stmt,"block").get(0));
    }

    private void analyzeControl(Node control) {
        List<Node> init = getNodeByName(control, "init");
        if(init.size()>0){
            List<Node> decl = getNodeByName(init.get(0), "decl");
            if(decl.size()>0)
                analyzeDecl(decl.get(0));
        }
        List<Node> condition = getNodeByName(control, "condition");
        if(condition.size()>0)
        analyzeConditionExpr(condition.get(0));
        List<Node> incr = getNodeByName(control, "incr");
        if(incr.size()>0)
        analyzeExpr(incr.get(0));
    }

    private void analyzeWhileStmt(Node stmt) {
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionExpr(Node condition) {
        analyzeCompoundExpr(condition);
    }

    private void analyzeTernaryExpr(Node expr) {
        analyzeConditionExpr(getNodeByName(expr,"condition").get(0));
        analyzeCompoundExpr(getNodeByName(expr,"then").get(0));
        analyzeCompoundExpr(getNodeByName(expr,"else").get(0));
    }

    private void analyzeParam(Node param) {
        analyzeDecl(getNodeByName(param,"decl").get(0));
    }

    private void analyzeExprStmt(Node expr_stmt){
        analyzeCompoundExpr(expr_stmt);
    }

    private void analyzeCompoundExpr(Node init_expr) {

        List<Node> expr1 = getNodeByName(init_expr, "expr");
        if(expr1.size()>0) {
            List<Node> exprs = asList(expr1.get(0).getChildNodes());
            if (is_assignment_expr(exprs)) {
                analyzeAssignmentExpr(exprs);
            } else {
                for (Node expr : exprs) {
                    analyzeExpr(expr);
                }
            }
        }
//      TODO check for pointers and update slice profiles
    }

    private void analyzeAssignmentExpr(List<Node> exprs) {
        NamePos lhs_expr_name_pos_pair = analyzeExpr(exprs.get(0));
        NamePos rhs_expr_name_pos_pair = analyzeExpr(exprs.get(4));
        String lhs_expr_var_name = lhs_expr_name_pos_pair.getName();
        String rhs_expr_var_name = rhs_expr_name_pos_pair.getName();
        if(lhs_expr_var_name == null || rhs_expr_var_name == null || lhs_expr_var_name.equals(rhs_expr_var_name)) return;
        if (local_variables.containsKey(rhs_expr_var_name)){
            updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,"local_variables");
        }
        else if(global_variables.containsKey(rhs_expr_var_name)){
            updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,"global_variables");
        }

        boolean is_buffer_write = isBufferWriteExpr(exprs.get(0));
        if(!is_buffer_write) return;
        SliceProfile l_var_profile = null;
        if (local_variables.containsKey(lhs_expr_var_name)){
            l_var_profile = local_variables.get(lhs_expr_var_name).get(lhs_expr_var_name);
        }
        else if(global_variables.containsKey(lhs_expr_var_name)){
            l_var_profile = global_variables.get(lhs_expr_var_name).get(lhs_expr_var_name);
        }
        if(l_var_profile==null)return;
        Tuple buffer_write_pos_tuple = new Tuple(DataAccessType.BUFFER_WRITE, lhs_expr_name_pos_pair.getPos());
        SliceVariableAccess var_access = new SliceVariableAccess();
        var_access.addWrite_positions(buffer_write_pos_tuple);
        l_var_profile.used_positions.add(var_access);
    }

    private boolean isBufferWriteExpr(Node expr) {
        if(!expr.getNodeName().equals("name")) return false;
        List<Node> comp_temp = getNodeByName(expr, "index");
        if(comp_temp.size()<1) return false;
        Node comp_tag2 = comp_temp.get(0);
        List<Node> comp = getNodeByName(comp_tag2,"expr");
        return comp.size()>0;
    }

    private void updateDVarSliceProfile(String l_var_name, String r_var_name, String slice_variables_string) {
        Hashtable<String, Hashtable<String, SliceProfile>> slice_variables;
        if(slice_variables_string.equals("local_variables")) slice_variables = local_variables;
        else slice_variables = global_variables;

        SliceProfile profile = slice_variables.get(r_var_name).get(r_var_name);
        String l_var_encl_function_name = current_function_name;

        SliceProfile l_var_profile = null;
        String l_var_defined_pos = null;
        if (global_variables.containsKey(l_var_name)){
            l_var_encl_function_name = GLOBAL;
            l_var_profile = global_variables.get(l_var_name).get(l_var_name);
        }
        else if(local_variables.containsKey(l_var_name)){
            l_var_profile = local_variables.get(l_var_name).get(l_var_name);
        }
        else return;

        l_var_defined_pos = l_var_profile.defined_position;

        NamePos dvar_pos_pair = new NamePos(l_var_name,l_var_encl_function_name,l_var_defined_pos,false);

        int n = profile.dependent_vars.length;
        NamePos[] arrlist = new NamePos[n+1];
        System.arraycopy(profile.dependent_vars, 0, arrlist, 0, n);
        arrlist[n] = dvar_pos_pair;
        profile.dependent_vars = arrlist;
        Hashtable<String, SliceProfile> body = new Hashtable<>();
        body.put(r_var_name,profile);
        if(slice_variables_string.equals("local_variables")) local_variables.put(r_var_name, body);
        else global_variables.put(r_var_name, body);
    }


    private boolean is_assignment_expr(List<Node> exprs) {
        if(exprs.size()!=5) return false;
        Node operator_expr = exprs.get(2);
        return operator_expr.getNodeName().equals("operator")&& operator_expr.getFirstChild().getNodeValue().equals("=");
    }

    private boolean is_literal_expr(Node expr) {
        return expr.getFirstChild().getNodeName().equals("literal");
    }
}
