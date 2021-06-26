package com.noble;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.noble.util.XmlUtil.asList;

final class cFunction{
    private final int arg_pos_index;
    private final String current_function_name;
    private final Node current_function_node;

    cFunction(int arg_pos_index, String current_function_name, Node current_function_node) {
        this.arg_pos_index = arg_pos_index;
        this.current_function_name = current_function_name;
        this.current_function_node = current_function_node;
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
}
final class NamePos {
    private final String name;
    private final String type;
    private final String pos;
    private final boolean is_pointer;
    NamePos(String name, String type, String pos, boolean is_pointer) {
        this.name = name;
        this.type = type;
        this.pos = pos;
        this.is_pointer = is_pointer;
    }

    public String getName() {
        return name;
    }

    public String getPos() {
        return pos;
    }

    public boolean is_pointer() {
        return is_pointer;
    }

    public String getType() {
        return type;
    }
}

public class SliceGenerator {
    String GLOBAL;
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
//        this.current_function_node;
        this.GLOBAL = "GLOBAL";
    }

    private NamePos getNamePosTextPair(Node init_node) {
        NodeList nodeList = init_node.getChildNodes();
        NamePos namePos = new NamePos("", "", "", false);
        boolean is_pointer = false;
        Set<String> names = new HashSet<String>();
            names.add("decl");
//        System.out.println(init_node.getNodeName()+nodeList.getLength()+nodeList.item(0).getNodeName());
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);

            if (tempNode.getNodeType() == Node.ELEMENT_NODE
                    && tempNode.hasAttributes()
                    && tempNode.hasChildNodes()) {
                if (tempNode.getNodeName().equals("name")) {
                    String linePos = getNodePos(tempNode);
                    if(tempNode.getNextSibling()!=null && tempNode.getNextSibling().getNodeType() == Node.ELEMENT_NODE)
                        if (((Element) tempNode.getNextSibling()).getTagName().equals("modifier"))
                            is_pointer = tempNode.getNextSibling().getNodeValue().equals("*")||tempNode.getNextSibling().getNodeValue().equals("&");
                    StringBuilder varType = new StringBuilder();
                    try {
//                        NodeList typeList = tempNode.getParentNode().getChildNodes().item(0).getChildNodes();
                        List<Node> typNode = getNodeByName(tempNode.getParentNode(),"type");
                        if (!(typNode.size()<1)){
                            NodeList typeList = typNode.get(0).getChildNodes();
                            for (int c = 0; c < typeList.getLength(); c++) {
                                Node tempType = typeList.item(c);
                                if(tempType.getNodeName().equals("name")){
                                    String Filler = "~";
                                    if(varType.toString().equals("")) Filler = "";
//                                if(tempType.getChildNodes().getLength()) std :: String [ERR]
                                    if(tempType.getLastChild().getNodeType()==Node.ELEMENT_NODE)
                                        varType.append(Filler).append(tempType.getLastChild().getFirstChild().getNodeValue());
                                    else
                                        varType.append(Filler).append(tempType.getLastChild().getNodeValue());
                                }
                            }
                        }

//                      varType = tempNode.getParentNode().getNextSibling().getNextSibling().getChildNodes().item(0).getNodeValue();
                    }
                    catch (NullPointerException | IndexOutOfBoundsException e){
                        varType = new StringBuilder();
                        e.printStackTrace();
                    }
                    if(tempNode.getFirstChild().getNodeType()==Node.ELEMENT_NODE)
//                        tempNode.getFirstChild().getFirstChild().getNodeValue()
                    {
                        List<Node> nameChildren = getNodeByName(tempNode, "name");
                        namePos = new NamePos(nameChildren.get(nameChildren.size()-1).getTextContent(), varType.toString(), linePos, is_pointer);
                    } else
                        namePos = new NamePos(tempNode.getFirstChild().getNodeValue(), varType.toString(), linePos, is_pointer);
                    break;
                }
                else if (tempNode.getNodeName().equals("literal")){
                    return new NamePos(tempNode.getTextContent(),tempNode.getAttributes().getNamedItem("type").getNodeValue(),getNodePos(tempNode),false);
                }
                else if(names.contains(tempNode.getNodeName())){
                    return getNamePosTextPair(tempNode);
                }
            }
        }
        if (init_node.getNodeName().equals("name")&&namePos.getName().equals(""))
            namePos = new NamePos(init_node.getFirstChild().getNodeValue(),"",getNodePos(init_node),false);
        return namePos;
    }

    private String getNodePos(Node tempNode) {
        return tempNode.getAttributes().item(0).getNodeValue().split(":")[0];
    }
//        XPath xpath = XPathFactory.newInstance().newXPath();
//        try {
//            Node result = (Node) xpath.evaluate("//*[local-name()='name']",init_node, XPathConstants.NODE);
//        } catch (XPathExpressionException e) {
//            e.printStackTrace();
//        }
//
//    private void parseValues(String name, NodeList nodeList) {
//        for (int count = 0; count < nodeList.getLength(); count++) {
//            Node tempNode = nodeList.item(count);
//            if (tempNode.getNodeType() == Node.ELEMENT_NODE
//                    && tempNode.hasAttributes()) {
//                NamedNodeMap attributes = tempNode.getAttributes();
//                Node nameNode = attributes.getNamedItem("name");
//                if (nameNode != null) {
//                    Node valueNode = attributes.getNamedItem("value");
//                    if (valueNode != null) {
//                        try {
//                            String nodeValue = valueNode.getNodeValue();
//                            nameNode.getNodeValue();
//                        } catch (NumberFormatException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            }
//        }
//    }
//    private boolean isDescendant(Element parent, Element child){
//        Node node = child.getParentNode();
//        while(node!= null){
//            if(node == parent){
//                return true;
//            }
//            node = node.getParentNode();
//        }
//        return false;
//    }
    private List<Node> find_all_nodes(Node unit_node, String tag) {
        Element eElement;
        eElement = (Element) unit_node;
        NodeList allChilds = eElement.getElementsByTagName(tag);
        return asList(allChilds);
    }

    private List<Node> getNodeByName(Node parent, String tag){
        NodeList children = parent.getChildNodes();
//        Set<Node> targetElements = new HashSet<Node>();
        List<Node> namedNodes = new LinkedList<Node>(asList(children));

        for(int x = namedNodes.size() - 1; x >= 0; x--)
        {
            if(!namedNodes.get(x).getNodeName().equals(tag))
            namedNodes.remove(x);
        }
        if(namedNodes.size()<1){
        for (int count = 0; count < children.getLength(); count++) {
            Node childDeep = children.item(count);
            if(childDeep.getNodeType()==Node.ELEMENT_NODE) {
                NodeList deepChildren;
                deepChildren = childDeep.getChildNodes();
                List<Node> namedDeepNodes = new LinkedList<Node>(asList(deepChildren));
                for(int x = namedDeepNodes.size() - 1; x >= 0; x--)
                {
                    if(!namedDeepNodes.get(x).getNodeName().equals(tag))
                        namedDeepNodes.remove(x);
                }
                if(namedDeepNodes.size()>=1)
                    return namedDeepNodes;
            }
        }
        }

        return  namedNodes;
    }

    public void generate(){
        String langAttribute = this.unit_node.getAttributes().getNamedItem("language").getNodeValue();
        if (langAttribute.equals("Java")) {
            this.analyzeJavaSource();
        }
        else if (langAttribute.equals("C++") || langAttribute.equals("C")) {
            this.analyzeCPPSource();
        }
    }

    private void analyzeJavaSource() {
        for(Node class_node:find_all_nodes(this.unit_node,"class"))
            this.analyzeJavaClass(class_node);
    }

    public void analyzeCPPSource() {
        NodeList doc = this.unit_node.getChildNodes();
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
            switch (node_tag) {
                case "decl_stmt":
//                  [TODO]  check if type returned correctly
                    this.analyzeGlobalDecl(node);
                    break;
                case "extern":
                    this.analyzeExternFunction(node);
                    break;
//                case "struct":
//                    this.analyzeStuct(node);
//                    break;
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
//        System.out.println("jdoc"+doc.getLength());
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String node_tag = node.getNodeName();
//            System.out.println(node_tag);
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
        NamePos namePos = this.getNamePosTextPair(nodeTemp);
        String slice_key = namePos.getName() + "%" + this.GLOBAL + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.GLOBAL, namePos.getName(), namePos.getType(), namePos.getPos());
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<String, SliceProfile>();
        nameProfile.put(namePos.getName(),slice_profile);
        this.global_variables.put(namePos.getName(),nameProfile);
    }
//        XPath xPath = XPathFactory.newInstance().newXPath();
//        try {
//            Node node = (Node) xPath.evaluate("//*[local-name()='name']", nodeTemp, XPathConstants.NODE);
//
//        } catch (XPathExpressionException e) {
//            e.printStackTrace();
//        }
    private void analyzeStaticBlock(Node node) {
        getNodePos(node);
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
//         printUserData no block
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
//        [TODO] Check if type returned correctly
        NamePos namePos = getNamePosTextPair(decl);
        String slice_key = namePos.getName() + "%" + this.current_function_name + "%" + this.file_name;
        SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, namePos.getName(), namePos.getType(), namePos.getPos(), this.current_function_node);
        this.slice_profiles.put(slice_key,slice_profile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<String, SliceProfile>();
        nameProfile.put(namePos.getName(),slice_profile);
        this.local_variables.put(namePos.getName(),nameProfile);
        List<Node> init_expr = getNodeByName(decl, "init");
        if (init_expr.size()>0){
            analyzeCompoundExpr(init_expr.get(0));
        }

    }

    private void analyzeExpr(Node expr) {
        for(Node expr_e:asList(expr.getChildNodes())){
            String expr_tag = expr_e.getNodeName();
            switch (expr_tag) {
                case "literal":
                    analyzeLiteralExpr(expr_e);
                case "operator":
//                    operator(=) call issues
                    analyzeOperatorExpr(expr_e);
                    break;
                case "ternary":
                    analyzeTernaryExpr(expr_e);
                    break;
                case "call":
                    analyzeCallExpr(expr_e);
                    break;
                case "name":
                    getNamePosTextPair(expr_e);
                    break;
            }
        }
    }
// [TODO] Return reason
    private void analyzeLiteralExpr(Node expr) {
        expr.getTextContent();
        getNodePos(expr);
    }

    private void analyzeOperatorExpr(Node expr) {
        expr.getTextContent();
        getNodePos(expr);
    }

    private static boolean isIndexOutOfBounds(final List<Node> list, int index) {
        return index < 0 || index >= list.size();
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

    private void analyzeCallExpr(Node call) {
        String cfunction_name = getCFunctionName(call);
        String cfunction_pos = getNodePos(call);
        List<Node> argument_list = getNodeByName(call, "argument");
        int arg_pos_index = 0;
        for(Node arg_expr:argument_list){
            arg_pos_index = arg_pos_index + 1;
            for(Node expr:getNodeByName(arg_expr,"expr")){
                analyzeExpr(expr);
                NamePos var_name_pos_pair = getNamePosTextPair(expr);
                String var_name = var_name_pos_pair.getName();
                String var_pos = var_name_pos_pair.getPos();
                if(var_name.equals("")) return;
                if (local_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name,cfunction_name,arg_pos_index,local_variables);
                }
                else if(global_variables.containsKey(var_name)){
                    updateCFunctionsSliceProfile(var_name,cfunction_name,arg_pos_index,global_variables);
                }
                else if(var_name_pos_pair.getType().equals("literal")){
                    String slice_key = var_name + "%" + this.current_function_name + "%" + this.file_name;
                    String type_name = var_name_pos_pair.getType();
                    SliceProfile slice_profile = new SliceProfile(this.file_name, this.current_function_name, var_name, type_name, var_pos,this.current_function_node);
                    int n = slice_profile.cfunctions.length;
                    cFunction[] arrlist = new cFunction[n+1];
                    System.arraycopy(slice_profile.cfunctions, 0, arrlist, 0, n);
                    cFunction cFun = new cFunction(arg_pos_index,current_function_name,current_function_node);
                    arrlist[n] = cFun;
                    slice_profile.cfunctions = arrlist;
                    this.slice_profiles.put(slice_key,slice_profile);
                }
            }
        }
    }

    private void updateCFunctionsSliceProfile(String var_name, String cfunction_name, int arg_pos_index, Hashtable<String, Hashtable<String, SliceProfile>> local_variables) {

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
        analyzeCompoundExpr(getNodeByName(stmt,"condition").get(0));
        analyzeBlock(getNodeByName(stmt,"block").get(0));

    }

    private void analyzeReturnStmt(Node stmt) {
        analyzeExpr(getNodeByName(stmt,"expr").get(0));
    }

    private void analyzeElseBlock(Node node) {
        analyzeBlock(getNodeByName(node,"block").get(0));
    }

    private void analyzeForStmt(Node stmt) {
        analyzeControl(getNodeByName(stmt,"control").get(0));
        analyzeBlock(getNodeByName(stmt,"block").get(0));
    }

    private void analyzeControl(Node control) {
        analyzeDecl(getNodeByName(control,"decl").get(0));
        analyzeConditionExpr(getNodeByName(control,"condition").get(0));
        analyzeExpr(getNodeByName(control,"incr").get(0));
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
        List<Node> exprs = getNodeByName(init_expr, "expr");
        if(is_assignment_expr(exprs)){
            analyzeAssignmentExpr(exprs);
        }
        else{
            for(Node expr:exprs){
                analyzeExpr(expr);
            }
        }
    }

    private void analyzeAssignmentExpr(List<Node> exprs) {
        NamePos lhs_expr_name_pos_pair = getNamePosTextPair(exprs.get(0).getFirstChild());
        NamePos rhs_expr_name_pos_pair = getNamePosTextPair(exprs.get(0).getLastChild());
        String lhs_expr_var_name = lhs_expr_name_pos_pair.getName();
        String rhs_expr_var_name = rhs_expr_name_pos_pair.getName();
        if(lhs_expr_var_name == null || rhs_expr_var_name == null || lhs_expr_var_name.equals(rhs_expr_var_name)) return;
        if (local_variables.containsKey(rhs_expr_var_name)){
            updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,rhs_expr_name_pos_pair.getPos(),local_variables);
        }
        else if(global_variables.containsKey(rhs_expr_var_name)){
            updateDVarSliceProfile(lhs_expr_var_name,rhs_expr_var_name,rhs_expr_name_pos_pair.getPos(),global_variables);
        }
    }

    private void updateDVarSliceProfile(String lhs_expr_var_name, String rhs_expr_var_name, String pos, Hashtable<String, Hashtable<String, SliceProfile>> local_variables) {
        SliceProfile profile = local_variables.get(rhs_expr_var_name).get(rhs_expr_var_name);
        String l_var_encl_function_name = current_function_name;
        if (global_variables.containsKey(lhs_expr_var_name))
        l_var_encl_function_name = GLOBAL;
        int n = profile.dependent_vars.length;
        NamePos[] arrlist = new NamePos[n+1];
        System.arraycopy(profile.dependent_vars, 0, arrlist, 0, n);
        NamePos dvar_pos_pair = new NamePos(lhs_expr_var_name,l_var_encl_function_name,pos,false);
        arrlist[n] = dvar_pos_pair;
        profile.dependent_vars = arrlist;
    }


    private boolean is_assignment_expr(List<Node> exprs) {
        List<Node> expr = getNodeByName(exprs.get(0),"operator");
        if (exprs.get(0).getLastChild().getNodeName().equals("call"))return false;
        if (expr.size()<1){
            return false;
        }
        Node operator_expr = expr.get(0);
        return operator_expr.getNodeName().equals("operator")&& operator_expr.getFirstChild().getNodeValue().equals("=");
    }

    private String getCFunctionName(Node call) {
//        StringBuilder name ="";
        return getNamePosTextPair(call).getName();
    }

}
