package com.noble;

import com.noble.models.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.noble.util.XmlUtil.*;
import static com.noble.util.XmlUtil.asList;
import static com.noble.util.XmlUtil.find_all_nodes;
import static com.noble.util.XmlUtil.getNamePosTextPair;
import static com.noble.util.XmlUtil.getNodeByName;
import static com.noble.util.XmlUtil.getNodePos;
import static com.noble.util.XmlUtil.noob;

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
        if(namespace_node==null) return;
        Node block = noob(getNodeByName(namespace_node, "block"), 0);
        if(block==null) return;
        analyzeCPPSource(block);
    }

    private void analyzeStruct(Node struct_node){
        if(struct_node==null) return;
//        List<Node> block = getNodeByName(struct_node, "block");
//      TODO analyze struct body
        NamePos struct_type_name_pos = getNamePosTextPair(struct_node);
        String struct_type_name = struct_type_name_pos.getType();
        if (struct_type_name.equals("")) return;
        Node struct_var_name_pos_temp = noob(getNodeByName(struct_node, "decl"),0);
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
        if(class_node==null) return;
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
        if(nodeTemp==null) return;
        NamePos namePos = getNamePosTextPair(nodeTemp);
        String slice_key = namePos.getName() + "%" + namePos.getPos() + "%" + this.GLOBAL + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.GLOBAL, namePos.getName(), namePos.getType(), namePos.getPos());
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(),slice_profile);
        this.global_variables.put(namePos.getName(),nameProfile);
    }

    private void analyzeStaticBlock(Node static_block) {
        if(static_block==null) return;
        current_function_name = GLOBAL;
        current_function_node = static_block;
        analyzeBlock(noob(getNodeByName(static_block, "block"),0));
        current_function_name = null;
        current_function_node = null;
    }

    private void analyzeExternFunction(Node extern_node) {
        if(extern_node==null) return;
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
        if(function==null) return;
        NamePos function_name = getNamePosTextPair(function);
        this.current_function_name = function_name.getName();
        this.current_function_node = function;
        List<Node> param = getNodeByName(function, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        analyzeBlock(noob(getNodeByName(function, "block"),0));
        this.current_function_name = null;
        this.current_function_node = null;
    }

    private void analyzeBlock(Node block) {
        if(block==null) return;
        Node iterNode = noob(getNodeByName(block, "block_content"), 0);
        if(iterNode!=null){
            NodeList iterBlock = iterNode.getChildNodes();
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
    }

    private void analyzeDeclStmt(Node stmt) {
        if(stmt==null) return;
        analyzeDecl(noob(getNodeByName(stmt,"decl"),0));
    }

    private void analyzeDecl(Node decl){
        if(decl==null) return;
        NamePos namePos = getNamePosTextPair(decl);
        String slice_key = namePos.getName() + "%" + namePos.getPos() + "%" + this.current_function_name + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, namePos.getName(), namePos.getType(), namePos.getPos(), this.current_function_node);
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(),slice_profile);
        local_variables.put(namePos.getName(),nameProfile);
        List<Node> expr_temp = getNodeByName(decl, "expr");
        Node init_node = noob(expr_temp, 0);
        if(init_node == null) return;
        List<Node> init_expr = asList(init_node.getChildNodes());

        for(Node expr: init_expr){
            if (analyze_update(namePos, expr)) return;
        }
        Node argument_list_temp = noob(getNodeByName(decl, "argument_list"),0);
        if(argument_list_temp==null) return;
        List<Node> argument_list = getNodeByName(argument_list_temp,"argument");
        for (Node arg_expr: argument_list){
            List<Node> expr_temp_f = getNodeByName(arg_expr, "expr");
            Node expr_temp_node = noob(expr_temp_f, 0);
            if(expr_temp_node==null)continue;
            for(Node expr: asList(expr_temp_node.getChildNodes())){
                if (analyze_update(namePos, expr)) return;
            }
        }

    }

    private boolean analyze_update(NamePos namePos, Node expr) {
        NamePos expr_var_name_pos_pair = analyzeExpr(expr);
        String expr_var_name = expr_var_name_pos_pair.getName();
//                String expr_var_pos = expr_var_name_pos_pair.getPos();
        if (expr_var_name.equals("")) return true;
        if (local_variables.containsKey(expr_var_name)) {
            updateDVarSliceProfile(namePos.getName(), expr_var_name, "local_variables");
        } else if (global_variables.containsKey(expr_var_name)) {
            updateDVarSliceProfile(namePos.getName(), expr_var_name, "global_variables");
        }
        return false;
    }

    private NamePos analyzeExpr(Node expr_e) {
        if(expr_e!=null){
            String expr_tag = expr_e.getNodeName();
            switch (expr_tag) {
                case "literal":
                    return analyzeLiteralExpr(expr_e);
                case "operator":
                    return analyzeOperatorExpr(expr_e);
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

    private NamePos analyzeOperatorExpr(Node expr) {
//        TODO needs checking
        String text;
        Node specificOpNode = noob(getNodeByName(expr.getParentNode(), "name"),0);
        if(specificOpNode==null) text = getNamePosTextPair(expr.getParentNode()).getName();
        else text = specificOpNode.getTextContent();
        return new NamePos(text.split(identifier_separator)[0],"",getNodePos(expr),false);
    }

    private void analyzeTryBlock(Node stmt) {
        if(stmt==null) return;
        analyzeBlock(noob(getNodeByName(stmt,"block"),0));
        analyzeCatchBlock(noob(getNodeByName(stmt,"catch"),0));
    }

    private void analyzeCatchBlock(Node catch_block) {
        if(catch_block==null) return;
        List<Node> param = getNodeByName(catch_block, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        analyzeBlock(noob(getNodeByName(catch_block,"block"),0));
    }

    private void analyzeSwitchStmt(Node stmt) {
        if(stmt==null) return;
        analyzeConditionBlock(stmt);
    }

    private void analyzeCaseStmt(Node stmt) {
        if(stmt==null) return;
        analyzeCompoundExpr(stmt);
    }

    private NamePos analyzeCallExpr(Node call) {
        NamePos cfunction_details = getNamePosTextPair(call);
        String cfunction_name = cfunction_details.getName();
        String cfunction_pos = cfunction_details.getPos();

        String cfunction_identifier = call.getTextContent().split(identifier_separator)[0];
        if(!local_variables.containsKey(cfunction_identifier) && !global_variables.containsKey(cfunction_identifier))
        {
            String cfunction_slice_identifier = cfunction_identifier + "%" + cfunction_pos;
            String cfunc_slice_key = cfunction_slice_identifier + "%" + current_function_name + "%" + file_name;
            SliceProfile cfunction_profile = new SliceProfile(file_name, current_function_name, cfunction_identifier, null, cfunction_pos, current_function_node);
            slice_profiles.put(cfunc_slice_key,cfunction_profile);
            Hashtable<String, SliceProfile> cfprofile = new Hashtable<>();
            cfprofile.put(cfunction_identifier, cfunction_profile);
            local_variables.put(cfunction_identifier,cfprofile);
        }

        NamePos todo_prevent_return = getNamePos(call, cfunction_name, cfunction_pos, cfunction_identifier);
        return new NamePos(cfunction_identifier,"",cfunction_pos,false);
    }

    private NamePos getNamePos(Node call, String cfunction_name, String cfunction_pos, String cfunction_identifier) {
        Node argument_node = noob(getNodeByName(call, "argument_list"),0);
        if(argument_node==null) return null;
        List<Node> argument_list = getNodeByName(argument_node,"argument");
        int arg_pos_index = 0;
        for(Node arg_expr:argument_list){
         arg_pos_index = arg_pos_index + 1;
            Node argument_c_node = noob(getNodeByName(arg_expr, "expr"),0);
            if(argument_c_node==null) return null;
            for(Node expr:asList(argument_c_node.getChildNodes())){
                NamePos var_name_pos_pair = analyzeExpr(expr);
                String var_name = var_name_pos_pair.getName();
                String var_pos = var_name_pos_pair.getPos();
                String slice_key = var_name + "%" + var_pos + "%" + this.current_function_name + "%" + this.file_name;
                if(var_name.equals("")) return var_name_pos_pair;
                if (local_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name, cfunction_name, cfunction_pos,arg_pos_index,"local_variables",slice_key);
                    if(!cfunction_identifier.equals("")) updateDVarSliceProfile(cfunction_identifier, var_name, "local_variables");
                }
                else if(global_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name, cfunction_name, cfunction_pos,arg_pos_index,"global_variables",slice_key);
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
        return null;
    }

//
//    argument_list = cast_expr.findall(
//            "./src:argument_list/src:argument", namespaces)
//            for _, arg_expr in enumerate(argument_list):
//            for expr in arg_expr.findall("./src:expr/*", namespaces):
//            self.analyzeExpr(expr)

//    if len(argument_list) == 2:
//            return self.analyzeExpr(argument_list[1].find("./src:expr/", namespaces))
//    else:
//            for index, arg_expr in enumerate(argument_list):
//            for expr in arg_expr.findall("./src:expr/*", namespaces):
//            self.analyzeExpr(expr)

    //
    private void analyzeCastExpr(Node cast_expr) {
        if(cast_expr==null) return;
        for(Node argument_list: getNodeByName(cast_expr, "argument_list",true)){
            for(Node argument: getNodeByName(argument_list, "argument")){
                Node arg_expr_node = noob(getNodeByName(argument,"expr"),0);
                if(arg_expr_node!=null)
                for(Node expr:asList(arg_expr_node.getChildNodes())){
                    analyzeExpr(expr);
                }
            }
        }
//        Node cast_node = noob(getNodeByName(cast_expr, "argument_list"),0);
//        if(cast_node==null)return;
//        List<Node> argument_list = getNodeByName(cast_node,"argument");
//        if(argument_list.size() == 2){
//            analyzeExpr(noob(getNodeByName(argument_list.get(1),"expr"),0));
//        }
//        else{
//            for(Node arg_expr:argument_list){
//                Node arg_expr_node = noob(getNodeByName(arg_expr,"expr"),0);
//                if(arg_expr_node==null)return;
//                for(Node expr:asList(arg_expr_node.getChildNodes())){
//                    analyzeExpr(expr);
//                }
//            }
//        }
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
        if(stmt==null) return;
        analyzeIfBlock(noob(getNodeByName(stmt,"if"),0));
        analyzeElseBlock(noob(getNodeByName(stmt, "else"),0));

    }

    private void analyzeIfBlock(Node stmt){
        if(stmt==null) return;
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionBlock(Node stmt) {
        if(stmt==null) return;
        analyzeCompoundExpr(noob(getNodeByName(stmt, "condition"),0));
        analyzeBlock(noob(getNodeByName(stmt, "block"),0));
    }

    private void analyzeReturnStmt(Node stmt) {
        if(stmt==null) return;
        Node expr = noob(getNodeByName(stmt, "expr"),0);
        if(expr!=null)
        analyzeExpr(expr.getChildNodes().item(0));
    }

    private void analyzeElseBlock(Node node) {
        if(node==null) return;
        analyzeBlock(noob(getNodeByName(node, "block"),0));
    }

    private void analyzeForStmt(Node stmt) {
        if(stmt==null) return;
        analyzeControl(noob(getNodeByName(stmt,"control"),0));
        analyzeBlock(noob(getNodeByName(stmt,"block"),0));
    }

    private void analyzeControl(Node control) {
        if(control==null) return;
        Node init = noob(getNodeByName(control, "init"),0);
        if(init!=null){
            analyzeDecl(noob(getNodeByName(init, "decl"),0));
        }
        analyzeConditionExpr(noob(getNodeByName(control, "condition"),0));
        analyzeExpr(noob(getNodeByName(control, "incr"),0));
    }

    private void analyzeWhileStmt(Node stmt) {
        if(stmt==null) return;
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionExpr(Node condition) {
        if(condition==null) return;
        analyzeCompoundExpr(condition);
    }

    private void analyzeTernaryExpr(Node expr) {
        if(expr==null) return;
        analyzeConditionExpr(noob(getNodeByName(expr,"condition"),0));
        analyzeCompoundExpr(noob(getNodeByName(expr,"then"),0));
        analyzeCompoundExpr(noob(getNodeByName(expr,"else"),0));
    }

    private void analyzeParam(Node param) {
        if(param==null) return;
        analyzeDecl(noob(getNodeByName(param, "decl"),0));
    }

    private void analyzeExprStmt(Node expr_stmt){
        if(expr_stmt==null) return;
        analyzeCompoundExpr(expr_stmt);
    }

    private void analyzeCompoundExpr(Node init_expr) {
        if(init_expr==null) return;
        Node expr1 = noob(getNodeByName(init_expr, "expr"),0);
        if(expr1!=null) {
            List<Node> exprs = asList(expr1.getChildNodes());
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
        if(exprs.size()<5) return;
        Node lhs_expr = exprs.get(0);
        Node rhs_expr = exprs.get(4);

        NamePos lhs_expr_name_pos_pair = analyzeExpr(lhs_expr);
        NamePos rhs_expr_name_pos_pair = analyzeExpr(rhs_expr);

        String lhs_expr_var_name = lhs_expr_name_pos_pair.getName();
        String rhs_expr_var_name = rhs_expr_name_pos_pair.getName();
        String lhs_expr_pos = lhs_expr_name_pos_pair.getPos();
//        String rhs_expr_pos = rhs_expr_name_pos_pair.getPos();

        if(lhs_expr_var_name == null || rhs_expr_var_name == null || lhs_expr_var_name.equals(rhs_expr_var_name)) return;

        if (local_variables.containsKey(rhs_expr_var_name)){
            updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,"local_variables");
        }
        else if(global_variables.containsKey(rhs_expr_var_name)){
            updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,"global_variables");
        }

        boolean is_buffer_write = isBufferWriteExpr(lhs_expr);
        if(!is_buffer_write) return;

        SliceProfile l_var_profile = null;
        if (local_variables.containsKey(lhs_expr_var_name)){
            l_var_profile = local_variables.get(lhs_expr_var_name).get(lhs_expr_var_name);
        }
        else if(global_variables.containsKey(lhs_expr_var_name)){
            l_var_profile = global_variables.get(lhs_expr_var_name).get(lhs_expr_var_name);
        }

        if(l_var_profile==null)return;

        Tuple buffer_write_pos_tuple = new Tuple(DataAccessType.BUFFER_WRITE, lhs_expr_pos);
        SliceVariableAccess var_access = new SliceVariableAccess();
        var_access.addWrite_positions(buffer_write_pos_tuple);
        l_var_profile.used_positions.add(var_access);
        l_var_profile.setUsed_positions(l_var_profile.used_positions);
    }

    private boolean isBufferWriteExpr(Node expr) {
        if(!expr.getNodeName().equals("name")) return false;
        Node comp_tag2 = noob(getNodeByName(expr, "index"),0);
        if(comp_tag2==null) return false;
        List<Node> comp = getNodeByName(comp_tag2,"expr");
        return comp.size()>0;
    }

    private void updateDVarSliceProfile(String l_var_name, String r_var_name, String slice_variables_string) {
        Hashtable<String, Hashtable<String, SliceProfile>> slice_variables;
        if(slice_variables_string.equals("local_variables")) slice_variables = local_variables;
        else slice_variables = global_variables;

        SliceProfile profile = slice_variables.get(r_var_name).get(r_var_name);
        String l_var_encl_function_name = current_function_name;

        SliceProfile l_var_profile;
        String l_var_defined_pos;
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
