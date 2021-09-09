package com.ca.uwaterloo.swag;

import com.ca.uwaterloo.swag.models.*;
import com.ca.uwaterloo.swag.util.XmlUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ca.uwaterloo.swag.util.XmlUtil.getNodeByName;

public class SliceGenerator {

    private final String fileName;
    private final Node unitNode;
    private final Hashtable<FunctionNamePos, Node> functionNodes;
    private final Hashtable<String, SliceProfile> sliceProfiles;
    private final Hashtable<String, List<FunctionNamePos>> functionDeclMap;
    private final Hashtable<String, Hashtable<String, SliceProfile>> globalVariables;
    private Hashtable<String, Hashtable<String, SliceProfile>> localVariables;
    private String currentFunctionName;
    private Node currentFunctionNode;

    private static final String GLOBAL = "GLOBAL";
    public static final String IDENTIFIER_SEPARATOR = "[^\\w]+";

    public SliceGenerator(Node unitNode, String fileName) {
        this.unitNode = unitNode;
        this.fileName = fileName;
        this.sliceProfiles = new Hashtable<>();
        this.functionNodes = findFunctionNodes(unitNode);
        this.functionDeclMap = new Hashtable<>();
        this.localVariables = new Hashtable<>();
        this.globalVariables = new Hashtable<>();
        this.currentFunctionName = "";
        this.currentFunctionNode = null;
    }

    public SliceProfilesInfo generate() {
        String langAttribute = this.unitNode.getAttributes().getNamedItem("language").getNodeValue();
        if (langAttribute.equals("Java")) {
            analyzeJavaSource(unitNode);
        } else if (langAttribute.equals("C++") || langAttribute.equals("C")) {
            analyzeCPPSource(unitNode);
        }

        return new SliceProfilesInfo(sliceProfiles, functionNodes, functionDeclMap, unitNode);
    }

    private void analyzeJavaSource(Node unitNode) {
        for (Node classNode : XmlUtil.findAllNodes(unitNode, "class")) {
            analyzeJavaClass(classNode);
        }
    }

    public void analyzeCPPSource(Node unitNode) {
        if (unitNode == null) {
            unitNode = this.unitNode;
        }
        analyzeCppSourceContent(unitNode.getChildNodes());
    }

    private void analyzeCppSourceContent(NodeList cppContent) {
        for (int count = 0; count < cppContent.getLength(); count++) {
            Node node = cppContent.item(count);
            String nodeTag = node.getNodeName();
            switch (nodeTag) {
                case "decl_stmt":
                    this.analyzeGlobalDecl(node);
                    break;
                case "extern":
                    this.analyzeExternFunction(node);
                    break;
                case "namespace":
                    this.analyzeNamespace(node);
                    break;
                case "class":
                    this.analyzeCppClass(node);
                    break;
                case "struct":
                    this.analyzeStruct(node);
                    break;
//                case "typedef":
//                    this.analyzeTypeDef(node);
//                    break;
//                case "macro":
//                    this.localVariables = new Hashtable<>();
//                    this.analyzeMacro(node);
//                    break;
                case "function_decl":
                case "function":
                case "constructor":
                case "destructor":
                    this.localVariables = new Hashtable<>();
                    this.analyzeFunction(node);
                    break;
            }
        }
    }

    private void analyzeNamespace(Node namespaceNode) {
        if (namespaceNode == null) {
            return;
        }
        Node block = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(namespaceNode, "block"), 0);
        if (block == null) {
            return;
        }
        analyzeCPPSource(block);
    }

    private void analyzeStruct(Node structNode) {
        if (structNode == null) {
            return;
        }

        NamePos structTypeNamePos = XmlUtil.getNamePosTextPair(structNode);
        String structTypeName = structTypeNamePos.getType();
        if (structTypeName.equals("")) {
            return;
        }
        Node structVarNameNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(structNode, "decl"), 0);
        NamePos structVarNamePos = XmlUtil.getNamePosTextPair(structVarNameNode);
        if (structVarNamePos.getName().equals("")) {
            return;
        }
        String structVarName = structVarNamePos.getName();
        String structPos = structVarNamePos.getPos();
        String sliceKey = structVarName + "%" + structPos + "%" + GLOBAL + "%" + fileName;
        SliceProfile profile = new SliceProfile(fileName, GLOBAL, structVarName, structTypeName, structPos);
        sliceProfiles.put(sliceKey, profile);
        Hashtable<String, SliceProfile> structProfile = new Hashtable<>();
        structProfile.put(structVarName, profile);
        globalVariables.put(structVarName, structProfile);

        //analyze struct body

        Node structNodeBlock = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(structNode, "block"), 0);

        if (structNodeBlock == null) {
            return;
        }

        analyzeCppClassBlockContent(XmlUtil.getNodeByName(structNodeBlock, "private"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(structNodeBlock, "protected"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(structNodeBlock, "public"));
    }

    private void analyzeCppClass(Node classNode) {
        if (classNode == null) {
            return;
        }

        Node cppClassBlock = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(classNode, "block"), 0);

        if (cppClassBlock == null) {
            return;
        }

        analyzeCppClassBlockContent(XmlUtil.getNodeByName(cppClassBlock, "private"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(cppClassBlock, "protected"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(cppClassBlock, "public"));
    }

    private void analyzeCppClassBlockContent(List<Node> blockContent) {
        for (Node content : blockContent) {
            NodeList nodeList = content.getChildNodes();
            analyzeCppSourceContent(nodeList);
        }
    }

    private void analyzeJavaClass(Node classNode) {
        if (classNode == null) {
            return;
        }
        NodeList nodeList = classNode.getChildNodes();
        NodeList doc = null;
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node node = nodeList.item(count);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.hasChildNodes()) {
                doc = node.getChildNodes();
            }
        }
        assert doc != null;
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String nodeTag = node.getNodeName();
            switch (nodeTag) {
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
                    this.localVariables = new Hashtable<>();
                    this.analyzeFunction(node);
                    break;
            }
        }
    }

    private void analyzeGlobalDecl(Node globalDeclNode) {
        if (globalDeclNode == null) {
            return;
        }
        NamePos namePos = XmlUtil.getNamePosTextPair(globalDeclNode);
        String sliceKey = namePos.getName() + "%" + namePos.getPos() + "%" + GLOBAL + "%" + this.fileName;
        SliceProfile sliceProfile = new SliceProfile(this.fileName, GLOBAL, namePos.getName(),
                namePos.getType(), namePos.getPos());
        this.sliceProfiles.put(sliceKey, sliceProfile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(), sliceProfile);
        this.globalVariables.put(namePos.getName(), nameProfile);

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = GLOBAL;
        this.currentFunctionNode = null;
        List<Node> decls = XmlUtil.getNodeByName(globalDeclNode, "decl");
        for (Node decl : decls) {
            List<Node> nodeList = XmlUtil.getNodeByName(decl, "block");
            for (Node block : nodeList) {
                analyzeBlock(block);
            }
        }
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
    }

    private void analyzeStaticBlock(Node staticBlock) {
        if (staticBlock == null) {
            return;
        }

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        currentFunctionName = GLOBAL;
        currentFunctionNode = staticBlock;
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(staticBlock, "block"), 0));
        currentFunctionName = previousFunctionName;
        currentFunctionNode = previousFunctionNode;
    }

    private void analyzeExternFunction(Node externNode) {
        if (externNode == null) {
            return;
        }
        NodeList doc = externNode.getChildNodes();
        for (int count = 0; count < doc.getLength(); count++) {
            Node node = doc.item(count);
            String nodeTag = node.getNodeName();
            if (nodeTag.equals("function_decl") || nodeTag.equals("function")) {
                this.localVariables = new Hashtable<>();
                this.analyzeFunction(node);
            }
        }
    }

    private void analyzeMacro(Node macro) {
        if (macro == null) {
            return;
        }
        NamePos functionNamePos = XmlUtil.getNamePosTextPair(macro);

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = functionNamePos.getName();
        this.currentFunctionNode = macro;
        List<Node> argumentList = XmlUtil.getArgumentList(macro);
        for (Node argument : argumentList) {
            analyzeParam(argument);
        }
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(macro, "block"), 0));
        if ("block".equals(macro.getNextSibling().getNodeName())) {
            analyzeBlock(macro.getNextSibling());
        }
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
    }

    private void analyzeFunction(Node function) {
        if (function == null) {
            return;
        }
        NamePos functionNamePos = XmlUtil.getNamePosTextPair(function);

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = functionNamePos.getName();
        this.currentFunctionNode = function;
        List<Node> param = XmlUtil.getFunctionParamList(function);
        for (Node node : param) {
            analyzeParam(node);
        }
        analyzeMemberInitList(function);
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(function, "block"), 0));
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
    }

    private void analyzeMemberInitList(Node functionNode) {
        List<Node> memberInitList = XmlUtil.getNodeByName(functionNode, "member_init_list");
        for (Node memberInit : memberInitList) {
            for (Node expr : XmlUtil.asList(memberInit.getChildNodes())) {
                analyzeExpr(expr);
            }
        }
    }

    private void analyzeBlock(Node block) {
        if (block == null) {
            return;
        }
        Node blockContent = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(block, "block_content"), 0);
        if (blockContent != null) {
            NodeList childNodes = blockContent.getChildNodes();
            for (Node stmt : XmlUtil.asList(childNodes)) {
                String stmtTag = stmt.getNodeName();
                switch (stmtTag) {
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
                    case "function":
                    case "function_decl":
                        analyzeFunction(stmt);
                        break;
                }
            }
        }
    }

    private void analyzeDeclStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeDecl(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "decl"), 0));
    }

    private void analyzeDecl(Node decl) {
        if (decl == null) {
            return;
        }
        NamePos namePos = XmlUtil.getNamePosTextPair(decl);
        String sliceKey = namePos.getName() + "%" + namePos.getPos() + "%" +
                this.currentFunctionName + "%" + this.fileName;
        SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName,
                namePos.getName(), namePos.getType(), namePos.getPos(), this.currentFunctionNode);
        this.sliceProfiles.put(sliceKey, sliceProfile);
        Hashtable<String, SliceProfile> nameProfile = new Hashtable<>();
        nameProfile.put(namePos.getName(), sliceProfile);
        localVariables.put(namePos.getName(), nameProfile);

        Node init = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(decl, "init"), 0);
        if (init != null) {
            List<Node> initExprs = XmlUtil.getNodeByName(init, "expr");
            Node initNode = XmlUtil.nodeAtIndex(initExprs, 0);
            if (initNode != null) {
                List<Node> initExpr = XmlUtil.asList(initNode.getChildNodes());
                for (Node expr : initExpr) {
                    analyzeExprAndUpdateDVar(namePos, expr);
                }
            }
        }

        Node argumentListNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(decl, "argument_list"), 0);
        if (argumentListNode == null) {
            return;
        }

        List<Node> argumentList = XmlUtil.getNodeByName(argumentListNode, "argument");
        for (Node argument : argumentList) {
            List<Node> argExprList = XmlUtil.getNodeByName(argument, "expr");
            Node argExpr = XmlUtil.nodeAtIndex(argExprList, 0);
            if (argExpr == null) {
                continue;
            }
            for (Node expr : XmlUtil.asList(argExpr.getChildNodes())) {
                analyzeExprAndUpdateDVar(namePos, expr);
            }
        }

        String typeName = namePos.getType();
        if (typeName != null && !typeName.isEmpty() && init == null) {
            NamePos typeNamePos = new NamePos(typeName, null, namePos.getPos(), false);
            String cfunctionName = typeNamePos.getName();
            String cfunctionPos = typeNamePos.getPos();

            if (!localVariables.containsKey(cfunctionName) && !globalVariables.containsKey(cfunctionName)) {
                String cfunctionSliceIdentifier = cfunctionName + "%" + cfunctionPos;
                String cfuncSliceKey = cfunctionSliceIdentifier + "%" + currentFunctionName + "%" + fileName;
                SliceProfile cfunctionProfile = new SliceProfile(fileName, currentFunctionName, cfunctionName,
                        null, cfunctionPos, currentFunctionNode);
                sliceProfiles.put(cfuncSliceKey, cfunctionProfile);
                Hashtable<String, SliceProfile> cfprofile = new Hashtable<>();
                cfprofile.put(cfunctionName, cfunctionProfile);
                localVariables.put(cfunctionName, cfprofile);
            }

            analyzeArgumentList(decl, cfunctionName, cfunctionPos, cfunctionName);
            checkAndUpdateDVarSliceProfile(namePos, typeNamePos);
        }
    }

    private void analyzeExprAndUpdateDVar(NamePos namePos, Node expr) {
        NamePos exprVarNamePos = analyzeExpr(expr);

        if (exprVarNamePos == null) {
            return;
        }

        String exprVarName = exprVarNamePos.getName();
        if (exprVarName.equals("")) {
            return;
        }
        if (localVariables.containsKey(exprVarName)) {
            updateDVarSliceProfile(namePos.getName(), exprVarName, "local_variables");
        } else if (globalVariables.containsKey(exprVarName)) {
            updateDVarSliceProfile(namePos.getName(), exprVarName, "global_variables");
        }
    }

    private NamePos analyzeExpr(Node expr) {
        if (expr != null) {
            String exprTag = expr.getNodeName();
            switch (exprTag) {
                case "literal":
                    return analyzeLiteralExpr(expr);
                case "operator":
                    return analyzeOperatorExpr(expr);
                case "ternary":
                    return analyzeTernaryExpr(expr);
                case "call":
                    return analyzeCallExpr(expr);
                case "cast":
                    analyzeCastExpr(expr);
                    break;
                case "name":
                    return XmlUtil.getNamePosTextPair(expr);
            }
        }
        return new NamePos("", "", "", false);
    }

    private NamePos analyzeLiteralExpr(Node literal) {
        String literalVal = literal.getTextContent();
        String typeName = literal.getAttributes().getNamedItem("type").getNodeValue();
        String pos = XmlUtil.getNodePos(literal);
        String sliceKey = literalVal + "%" + pos + "%" + currentFunctionName + "%" + fileName;
        SliceProfile profile = new SliceProfile(fileName, currentFunctionName, literalVal, typeName, pos,
                currentFunctionNode);
        sliceProfiles.put(sliceKey, profile);
        Hashtable<String, SliceProfile> lvar = new Hashtable<>();
        lvar.put(literalVal, profile);
        localVariables.put(literalVal, lvar);
        return new NamePos(literalVal, typeName, pos, false);
    }

    private NamePos analyzeOperatorExpr(Node expr) {
//        TODO needs checking
        String text;
        Node specificOpNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr.getParentNode(), "name"), 0);
        if (specificOpNode == null) {
            text = XmlUtil.getNamePosTextPair(expr.getParentNode()).getName();
        } else {
            text = specificOpNode.getTextContent();
        }
        return new NamePos(text.split(IDENTIFIER_SEPARATOR)[0], "", XmlUtil.getNodePos(expr), false);
    }

    private void analyzeTryBlock(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "block"), 0));
        analyzeCatchBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "catch"), 0));
    }

    private void analyzeCatchBlock(Node catchBlock) {
        if (catchBlock == null) {
            return;
        }
        List<Node> param = XmlUtil.getNodeByName(catchBlock, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(catchBlock, "block"), 0));
    }

    private void analyzeSwitchStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeConditionBlock(stmt);
    }

    private void analyzeCaseStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeCompoundExpr(stmt);
    }

    private NamePos analyzeCallExpr(Node call) {
        NamePos cfunctionDetails = XmlUtil.getNamePosTextPair(call);
        String cfunctionName = cfunctionDetails.getName();
        String cfunctionPos = cfunctionDetails.getPos();

        String cfunctionIdentifier = call.getTextContent().split(IDENTIFIER_SEPARATOR)[0];
        if (!localVariables.containsKey(cfunctionIdentifier) &&
                !globalVariables.containsKey(cfunctionIdentifier)) {
            String cfunctionSliceIdentifier = cfunctionIdentifier + "%" + cfunctionPos;
            String cfuncSliceKey = cfunctionSliceIdentifier + "%" + currentFunctionName + "%" + fileName;
            SliceProfile cfunctionProfile = new SliceProfile(fileName, currentFunctionName,
                    cfunctionIdentifier, null, cfunctionPos, currentFunctionNode);
            sliceProfiles.put(cfuncSliceKey, cfunctionProfile);
            Hashtable<String, SliceProfile> cfprofile = new Hashtable<>();
            cfprofile.put(cfunctionIdentifier, cfunctionProfile);
            localVariables.put(cfunctionIdentifier, cfprofile);
        }

        analyzeArgumentList(call, cfunctionName, cfunctionPos, cfunctionIdentifier);
        return new NamePos(cfunctionIdentifier, "", cfunctionPos, false);
    }

    private void analyzeArgumentList(Node call, String cfunctionName, String cfunctionPos,
                                     String cfunctionIdentifier) {
        Node argumentNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(call, "argument_list"), 0);
        if (argumentNode == null) {
            return;
        }
        List<Node> argumentList = XmlUtil.getNodeByName(argumentNode, "argument");
        int argPosIndex = 0;
        List<NamePos> argsList = new ArrayList<>();
        for (Node argExpr : argumentList) {
            argPosIndex = argPosIndex + 1;
            Node argExprNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(argExpr, "expr"), 0);
            if (argExprNode == null) {
                return;
            }

            for (Node expr : XmlUtil.asList(argExprNode.getChildNodes())) {
                NamePos varNamePos = analyzeExpr(expr);
                if (varNamePos == null) {
                    continue;
                }
                String varName = varNamePos.getName();
                String varPos = varNamePos.getPos();
                String sliceKey = varName + "%" + varPos + "%" + this.currentFunctionName + "%" + this.fileName;
                if (varName.equals("")) {
                    continue;
                }
                if (localVariables.containsKey(varName)) {
                    updateCFunctionsSliceProfile(varName, cfunctionName, cfunctionPos, argPosIndex,
                            "local_variables", sliceKey);
                    if (!cfunctionIdentifier.isEmpty()) {
                        updateDVarSliceProfile(cfunctionIdentifier, varName, "local_variables");
                    }
//                    if (local_variables.containsKey(cfunction_identifier)) {
//                        updateDVarSliceProfile(var_name, cfunction_identifier, "local_variables");
//                    }
                } else if (globalVariables.containsKey(varName)) {
                    updateCFunctionsSliceProfile(varName, cfunctionName, cfunctionPos, argPosIndex,
                            "global_variables", sliceKey);
                    if (!cfunctionIdentifier.isEmpty()) {
                        updateDVarSliceProfile(cfunctionIdentifier, varName, "global_variables");
                    }
//                    if (global_variables.containsKey(cfunction_identifier)) {
//                        updateDVarSliceProfile(var_name, cfunction_identifier, "global_variables");
//                    }
                } else if (isLiteralExpr(expr)) {
                    String typeName = varNamePos.getType();
                    SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName,
                            varName, typeName, varPos, this.currentFunctionNode);
                    CFunction cFun = new CFunction(argPosIndex, currentFunctionName, currentFunctionNode);
                    sliceProfile.cfunctions.add(cFun);
                    sliceProfiles.put(sliceKey, sliceProfile);
                }

                argsList.add(varNamePos);
            }
        }

        if (argsList.size() < 2) {
            return;
        }

        for (int i = argsList.size() - 1; (i - 1) >= 0; i--) {
            NamePos rhs = argsList.get(i);
            NamePos lhs = argsList.get(i - 1);
//            checkAndUpdateDVarSliceProfile(lhs, rhs);
        }
    }

    private void analyzeCastExpr(Node castExpr) {
        if (castExpr == null) {
            return;
        }
        for (Node argumentList : XmlUtil.getNodeByName(castExpr, "argument_list", true)) {
            for (Node argument : XmlUtil.getNodeByName(argumentList, "argument")) {
                Node argExprNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(argument, "expr"), 0);
                if (argExprNode != null) {
                    for (Node expr : XmlUtil.asList(argExprNode.getChildNodes())) {
                        analyzeExpr(expr);
                    }
                }
            }
        }
    }

    private void updateCFunctionsSliceProfile(String varName, String cfunctionName, String cfunctionPos,
                                              int argPosIndex, String sliceVariablesString, String sliceKey) {
        Hashtable<String, Hashtable<String, SliceProfile>> sliceVariables;
        if (sliceVariablesString.equals("local_variables")) {
            sliceVariables = localVariables;
        } else {
            sliceVariables = globalVariables;
        }
        SliceProfile sliceProfile = sliceVariables.get(varName).get(varName);
        CFunction cFun = new CFunction(cfunctionName, cfunctionPos, argPosIndex, currentFunctionName,
                currentFunctionNode);
        sliceProfile.cfunctions.add(cFun);
        sliceProfiles.put(sliceKey, sliceProfile);
        Hashtable<String, SliceProfile> body = sliceVariables.get(varName);
        body.put(varName, sliceProfile);
        if (sliceVariablesString.equals("local_variables")) {
            localVariables.put(varName, body);
        } else {
            globalVariables.put(varName, body);
        }
    }

    private void analyzeIfStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        List<Node> ifBlocks = XmlUtil.getNodeByName(stmt, "if");
        for (Node ifBlock : ifBlocks) {
            analyzeIfBlock(ifBlock);
        }

        analyzeElseBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "else"), 0));

    }

    private void analyzeIfBlock(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeConditionBlock(stmt);
    }

    private void analyzeConditionBlock(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeCompoundExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "condition"), 0));
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "block"), 0));
    }

    private void analyzeReturnStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        Node expr = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "expr"), 0);
        if (expr != null) {
            analyzeExpr(expr.getChildNodes().item(0));
        }
    }

    private void analyzeElseBlock(Node node) {
        if (node == null) {
            return;
        }
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(node, "block"), 0));
    }

    private void analyzeForStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeControl(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "control"), 0));
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "block"), 0));
    }

    private void analyzeControl(Node control) {
        if (control == null) {
            return;
        }
        Node init = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(control, "init"), 0);
        if (init != null) {
            analyzeDecl(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(init, "decl"), 0));
        }
        analyzeConditionExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(control, "condition"), 0));
        analyzeExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(control, "incr"), 0));
    }

    private void analyzeWhileStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeConditionBlock(stmt);
    }

    private NamePos analyzeConditionExpr(Node condition) {
        if (condition == null) {
            return null;
        }
        return analyzeCompoundExpr(condition);
    }

    private NamePos analyzeTernaryExpr(Node expr) {
        if (expr == null) {
            return null;
        }
        NamePos conditionNamePos = analyzeConditionExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "condition"), 0));
        NamePos thenNamePos = analyzeCompoundExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "then"), 0));
        NamePos elseNamePos = analyzeCompoundExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "else"), 0));
        checkAndUpdateDVarSliceProfile(conditionNamePos, thenNamePos);
        checkAndUpdateDVarSliceProfile(conditionNamePos, elseNamePos);

        return conditionNamePos;
    }

    private void analyzeParam(Node param) {
        if (param == null) {
            return;
        }
        analyzeDecl(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(param, "decl"), 0));
    }

    private void analyzeExprStmt(Node exprStmt) {
        if (exprStmt == null) {
            return;
        }
        analyzeCompoundExpr(exprStmt);
    }

    private NamePos analyzeCompoundExpr(Node compoundExpr) {
        if (compoundExpr == null) {
            return null;
        }
        Node exprNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(compoundExpr, "expr"), 0);
        if (exprNode != null) {
            List<Node> exprs = XmlUtil.asList(exprNode.getChildNodes());
            if (isAssignmentExpr(exprs)) {
                analyzeAssignmentExpr(exprs);
            } else {
                if (exprs.size() == 1) {
                    return analyzeExpr(exprs.get(0));
                }
                for (Node expr : exprs) {
                    analyzeExpr(expr);
                }
            }
        }

        return new NamePos("", "", "", false);
//      TODO check for pointers and update slice profiles
    }

    private void analyzeAssignmentExpr(List<Node> exprs) {
        if (exprs.size() < 5) {
            return;
        }
        Node lhsExpr = exprs.get(0);
        Node rhsExpr = exprs.get(4);

        NamePos lhsExprNamePos = analyzeExpr(lhsExpr);
        NamePos rhsExprNamePos = analyzeExpr(rhsExpr);

        String lhsExprVarName = lhsExprNamePos.getName();
        String rhsExprVarName = rhsExprNamePos.getName();
        String lhsExprPos = lhsExprNamePos.getPos();

        if (lhsExprVarName == null || rhsExprVarName == null) {
            return;
        }

        if (isLhsExprFunctionPointer(lhsExprVarName)) {
            List<FunctionNamePos> alias;
            if (functionDeclMap.containsKey(lhsExprVarName)) {
                alias = functionDeclMap.get(lhsExprVarName);
            } else {
                alias = new ArrayList<>();
            }

            FunctionNamePos rhsFunctionPointerName = XmlUtil.getFunctionNamePos(rhsExpr);
            alias.add(rhsFunctionPointerName);
            functionDeclMap.put(lhsExprVarName, alias);
        }

        boolean isBufferWrite = isBufferWriteExpr(lhsExpr);
        if (!isBufferWrite && lhsExprVarName.equals(rhsExprVarName)) {
            return;
        }

        if (!lhsExprVarName.equals(rhsExprVarName)) {
            if (localVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, "local_variables");
            } else if (globalVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, "global_variables");
            }
        }

        if (!isBufferWrite) {
            return;
        }

        SliceProfile lhsVarProfile = null;
        if (localVariables.containsKey(lhsExprVarName)) {
            lhsVarProfile = localVariables.get(lhsExprVarName).get(lhsExprVarName);
        } else if (globalVariables.containsKey(lhsExprVarName)) {
            lhsVarProfile = globalVariables.get(lhsExprVarName).get(lhsExprVarName);
        }

        if (lhsVarProfile == null) {
            return;
        }


        SliceProfile rhsVarProfile = null;
        if (localVariables.containsKey(rhsExprVarName)) {
            rhsVarProfile = localVariables.get(rhsExprVarName).get(rhsExprVarName);
        } else if (globalVariables.containsKey(rhsExprVarName)) {
            rhsVarProfile = globalVariables.get(rhsExprVarName).get(rhsExprVarName);
        }

        if (rhsVarProfile == null) {
            return;
        }

        DataTuple bufferWriteData = new DataTuple(XmlUtil.DataAccessType.BUFFER_WRITE, lhsExprPos);
        SliceVariableAccess varAccess = new SliceVariableAccess();
        varAccess.addWritePosition(bufferWriteData);
        rhsVarProfile.usedPositions.add(varAccess);
    }

    private boolean isLhsExprFunctionPointer(String lhsExprVarName) {
        return functionNodes.keySet().stream().anyMatch(namePos -> namePos.getName().equals(lhsExprVarName));
    }

    private boolean isBufferWriteExpr(Node expr) {
        if (!expr.getNodeName().equals("name")) {
            return false;
        }
        Node compTag = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "index"), 0);
        if (compTag == null) {
            return false;
        }
        List<Node> comp = XmlUtil.getNodeByName(compTag, "expr");
        return comp.size() > 0;
    }

    private void checkAndUpdateDVarSliceProfile(NamePos lhsExprVarNamePos, NamePos rhsExprVarNamePos) {
        if (lhsExprVarNamePos == null || lhsExprVarNamePos.getName().isEmpty()) {
            return;
        }
        String lhsExprVarName = lhsExprVarNamePos.getName();
        if (rhsExprVarNamePos != null && !rhsExprVarNamePos.getName().isEmpty()) {
            String rhsExprVarName = rhsExprVarNamePos.getName();
            if (localVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, "local_variables");
            } else if (globalVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, "global_variables");
            }
        }
    }

    private void updateDVarSliceProfile(String lVarName, String rVarName, String sliceVariablesString) {
        Hashtable<String, Hashtable<String, SliceProfile>> sliceVariables;
        if (sliceVariablesString.equals("local_variables")) {
            sliceVariables = localVariables;
        } else {
            sliceVariables = globalVariables;
        }

        SliceProfile profile = sliceVariables.get(rVarName).get(rVarName);
        String lVarEnclFunctionName = currentFunctionName;

        SliceProfile lVarProfile;
        String lVarDefinedPos;
        if (globalVariables.containsKey(lVarName)) {
            lVarEnclFunctionName = GLOBAL;
            lVarProfile = globalVariables.get(lVarName).get(lVarName);
        } else if (localVariables.containsKey(lVarName)) {
            lVarProfile = localVariables.get(lVarName).get(lVarName);
        } else {
            return;
        }

        lVarDefinedPos = lVarProfile.definedPosition;

        NamePos dvarNamePos = new NamePos(lVarName, lVarEnclFunctionName, lVarDefinedPos, false);
        profile.dependentVars.add(dvarNamePos);

        Hashtable<String, SliceProfile> body = new Hashtable<>();
        body.put(rVarName, profile);
        if (sliceVariablesString.equals("local_variables")) {
            localVariables.put(rVarName, body);
        } else {
            globalVariables.put(rVarName, body);
        }
    }

    private boolean isAssignmentExpr(List<Node> exprs) {
        if (exprs.size() != 5) {
            return false;
        }
        Node operatorExpr = exprs.get(2);
        return operatorExpr.getNodeName().equals("operator") &&
                (operatorExpr.getFirstChild().getNodeValue().equals("=") ||
                        operatorExpr.getFirstChild().getNodeValue().equals("+="));
    }

    private boolean isLiteralExpr(Node expr) {
        return expr.getFirstChild().getNodeName().equals("literal");
    }

    private static Hashtable<FunctionNamePos, Node> findFunctionNodes(Node unitNode) {
        Hashtable<FunctionNamePos, Node> functionNodes = new Hashtable<>();
        List<Node> fun1 = XmlUtil.getNodeByName(unitNode, "function", true);
        List<Node> fun2 = XmlUtil.getNodeByName(unitNode, "function_decl", true);
        List<Node> fun3 = XmlUtil.getNodeByName(unitNode, "constructor", true);
        List<Node> fun4 = XmlUtil.getNodeByName(unitNode, "destructor", true);

        List<Node> funList = Stream.of(fun1, fun2, fun3, fun4)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        for (Node node : funList) {
            functionNodes.put(XmlUtil.getFunctionNamePos(node), node);
        }
        return functionNodes;
    }
}
