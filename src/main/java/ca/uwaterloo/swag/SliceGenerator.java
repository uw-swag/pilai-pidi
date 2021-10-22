package ca.uwaterloo.swag;

import static ca.uwaterloo.swag.util.XmlUtil.DataAccessType;
import static ca.uwaterloo.swag.util.XmlUtil.asList;
import static ca.uwaterloo.swag.util.XmlUtil.getFunctionNamePos;
import static ca.uwaterloo.swag.util.XmlUtil.getNamePosTextPair;
import static ca.uwaterloo.swag.util.XmlUtil.getNodeByName;
import static ca.uwaterloo.swag.util.XmlUtil.getNodePos;
import static ca.uwaterloo.swag.util.XmlUtil.isEmptyTextNode;
import static ca.uwaterloo.swag.util.XmlUtil.nodeAtIndex;

import ca.uwaterloo.swag.models.ArgumentNamePos;
import ca.uwaterloo.swag.models.CFunction;
import ca.uwaterloo.swag.models.DataTuple;
import ca.uwaterloo.swag.models.FunctionNamePos;
import ca.uwaterloo.swag.models.NamePos;
import ca.uwaterloo.swag.models.SliceProfile;
import ca.uwaterloo.swag.models.SliceProfilesInfo;
import ca.uwaterloo.swag.models.SliceVariableAccess;
import ca.uwaterloo.swag.util.XmlUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SliceGenerator {

    public static final String IDENTIFIER_SEPARATOR = "[^\\w]+";
    private static final String GLOBAL = "GLOBAL";
    private static final List<String> ARITHMETIC_OPRTS = Arrays.asList("+", "-", "*", "/");
    private final String fileName;
    private final Node unitNode;
    private final Map<FunctionNamePos, Node> functionNodes;
    private final Map<String, SliceProfile> sliceProfiles;
    private final Map<String, List<FunctionNamePos>> functionDeclMap;
    private final Map<String, SliceProfile> globalVariables;
    private Map<String, SliceProfile> localVariables;
    private String currentFunctionName;
    private Node currentFunctionNode;
    private boolean isLhsExpr;
    private boolean withinDeclStmt;

    public SliceGenerator(Node unitNode, String fileName) {
        this.unitNode = unitNode;
        this.fileName = fileName;
        this.sliceProfiles = new Hashtable<>();
        this.functionNodes = new Hashtable<>();
        this.functionDeclMap = new Hashtable<>();
        this.localVariables = new Hashtable<>();
        this.globalVariables = new Hashtable<>();
        this.currentFunctionName = "";
        this.currentFunctionNode = null;
    }

//    private static Hashtable<FunctionNamePos, Node> findFunctionNodes(Node unitNode) {
//        Hashtable<FunctionNamePos, Node> functionNodes = new Hashtable<>();
//        List<Node> functions = getNodeByName(unitNode, "function", true);
//        List<Node> funcDecls = getNodeByName(unitNode, "function_decl", true);
//        List<Node> constructors = getNodeByName(unitNode, "constructor", true);
//        List<Node> destructors = getNodeByName(unitNode, "destructor", true);
//        List<Node> macros = getMacros(unitNode);
//
//        List<Node> funcList = Stream.of(functions, funcDecls, constructors, destructors, macros)
//            .flatMap(Collection::stream)
//            .collect(Collectors.toList());
//
//        for (Node node : funcList) {
//            functionNodes.put(XmlUtil.getFunctionNamePos(node), node);
//        }
//        return functionNodes;
//    }

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
                case "expr_stmt":
                    this.analyzeExprStmt(node);
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
                case "macro":
                    this.analyzeMacro(node);
                    break;
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
        Node block = nodeAtIndex(getNodeByName(namespaceNode, "block"), 0);
        if (block == null) {
            return;
        }
        analyzeCPPSource(block);
    }

    private void analyzeStruct(Node structNode) {
        if (structNode == null) {
            return;
        }

        NamePos structNamePos = getNamePosTextPair(structNode);
        String structName = structNamePos.getName();
        String structTypeName = structNamePos.getType();
        Node structVarNameNode = nodeAtIndex(getNodeByName(structNode, "decl"), 0);
        NamePos structVarNamePos = getNamePosTextPair(structVarNameNode);
        if (!structVarNamePos.getName().equals("")) {
            structName = structVarNamePos.getName();
        }

        boolean isPointer = structNamePos.isPointer() || structVarNamePos.isPointer();
        String structPos = structVarNamePos.getPos();
        String sliceKey = structName + "%" + structPos + "%" + GLOBAL + "%" + fileName;
        SliceProfile profile = new SliceProfile(fileName, GLOBAL, structName, structTypeName, structPos,
            isPointer);
        sliceProfiles.put(sliceKey, profile);
        globalVariables.put(structName, profile);

        //analyze struct body

        Node structNodeBlock = nodeAtIndex(getNodeByName(structNode, "block"), 0);

        if (structNodeBlock == null) {
            return;
        }

        analyzeCppClassBlockContent(getNodeByName(structNodeBlock, "private"));
        analyzeCppClassBlockContent(getNodeByName(structNodeBlock, "protected"));
        analyzeCppClassBlockContent(getNodeByName(structNodeBlock, "public"));
    }

    private void analyzeCppClass(Node classNode) {
        if (classNode == null) {
            return;
        }

        Node cppClassBlock = nodeAtIndex(getNodeByName(classNode, "block"), 0);

        if (cppClassBlock == null) {
            return;
        }

        analyzeCppClassBlockContent(getNodeByName(cppClassBlock, "private"));
        analyzeCppClassBlockContent(getNodeByName(cppClassBlock, "protected"));
        analyzeCppClassBlockContent(getNodeByName(cppClassBlock, "public"));
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
        NamePos namePos = getNamePosTextPair(globalDeclNode);
        String sliceKey =
            namePos.getName() + "%" + namePos.getPos() + "%" + GLOBAL + "%" + fileName;
        SliceProfile sliceProfile = new SliceProfile(fileName, GLOBAL, namePos.getName(), namePos.getType(),
            namePos.getPos(), namePos.isPointer());
        sliceProfiles.put(sliceKey, sliceProfile);
        globalVariables.put(namePos.getName(), sliceProfile);

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = GLOBAL;
        this.currentFunctionNode = null;
        List<Node> decls = getNodeByName(globalDeclNode, "decl");
        for (Node decl : decls) {
            List<Node> nodeList = getNodeByName(decl, "block");
            for (Node block : nodeList) {
                analyzeGlobaDeclBlock(block);
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
        analyzeBlock(nodeAtIndex(getNodeByName(staticBlock, "block"), 0));
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

        if (!withinDeclStmt) {
            this.localVariables = new Hashtable<>();
        }

        FunctionNamePos functionNamePos = getFunctionNamePos(macro);

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = functionNamePos.getName();
        Node macroBody = null;
        Node block = nodeAtIndex(getNodeByName(macro.getParentNode(), "block"), 0);
        if (block != null) {
            Node blockContent = nodeAtIndex(getNodeByName(block, "block_content"), 0);
            if (blockContent != null && blockContent.getParentNode() == block) {
                macroBody = blockContent;
            } else {
                macroBody = block;
            }
        }

        this.currentFunctionNode = macroBody;
        List<Node> argumentList = XmlUtil.getArgumentList(macro);
        List<ArgumentNamePos> argumentNames = new ArrayList<>();
        for (Node argument : argumentList) {
            ArgumentNamePos paramNamePos = analyzeArgumentOfMacro(argument);
            if (paramNamePos == null) {
                continue;
            }
            argumentNames.add(paramNamePos);
        }
        functionNamePos.setArguments(argumentNames);
        this.functionNodes.put(functionNamePos, macro);
        analyzeBlockContent(macroBody);
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
    }

    private void analyzeFunction(Node function) {
        if (function == null) {
            return;
        }
        FunctionNamePos functionNamePos = getFunctionNamePos(function);
        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;
        this.currentFunctionName = functionNamePos.getName();
        this.currentFunctionNode = function;
        List<Node> params = XmlUtil.getFunctionParamList(function);
        List<ArgumentNamePos> argumentNames = new ArrayList<>();
        for (Node param : params) {
            ArgumentNamePos paramNamePos = analyzeParam(param);
            if (paramNamePos == null) {
                continue;
            }
            argumentNames.add(paramNamePos);
        }
        functionNamePos.setArguments(argumentNames);
        this.functionNodes.put(functionNamePos, function);
        analyzeMemberInitList(function);
        analyzeBlock(nodeAtIndex(getNodeByName(function, "block"), 0));
        updateFunctionArgDependencies(functionNamePos);
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
    }

    private void updateFunctionArgDependencies(NamePos functionNamePos) {
        String functionName = functionNamePos.getName();
        String functionPos = functionNamePos.getPos();
        addFunctionNameSliceProfile(functionName, functionPos);
        for (String localVarName : localVariables.keySet()) {
            updateDVarSliceProfile(localVarName, functionName, globalVariables);
        }
    }

    private void analyzeMemberInitList(Node functionNode) {
        List<Node> memberInitList = getNodeByName(functionNode, "member_init_list");
        for (Node memberInit : memberInitList) {
            for (Node expr : asList(memberInit.getChildNodes())) {
                analyzeExpr(expr);
            }
        }
    }

    private void analyzeGlobaDeclBlock(Node block) {
        if (block == null) {
            return;
        }
        Node blockContent = nodeAtIndex(getNodeByName(block, "block_content"), 0);
        if (blockContent != null) {
            NodeList childNodes = blockContent.getChildNodes();
            for (Node stmt : asList(childNodes)) {
                String stmtTag = stmt.getNodeName();
                switch (stmtTag) {
                    case "expr_stmt":
                        analyzeExprStmt(stmt);
                        break;
                    case "decl_stmt":
                        analyzeGlobalDecl(stmt);
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

    private void analyzeBlock(Node block) {
        if (block == null) {
            return;
        }
        Node blockContent = nodeAtIndex(getNodeByName(block, "block_content"), 0);
        analyzeBlockContent(blockContent);
    }

    private void analyzeBlockContent(Node blockContent) {
        if (blockContent == null) {
            return;
        }

        NodeList childNodes = blockContent.getChildNodes();
        for (Node stmt : asList(childNodes)) {
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

    private void analyzeDeclStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        this.withinDeclStmt = true;
        analyzeDecl(nodeAtIndex(getNodeByName(stmt, "decl"), 0));
        this.withinDeclStmt = false;
    }

    private NamePos analyzeDecl(Node decl) {
        if (decl == null) {
            return null;
        }
        NamePos namePos = getNamePosTextPair(decl);
        String varName = namePos.getName();
        String varPos = namePos.getPos();
        String sliceKey = varName + "%" + varPos + "%" + this.currentFunctionName + "%" + this.fileName;
        SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName, varName,
            namePos.getType(), varPos, namePos.isPointer(), this.currentFunctionNode);
        sliceProfiles.put(sliceKey, sliceProfile);
        localVariables.put(varName, sliceProfile);

        Node init = nodeAtIndex(getNodeByName(decl, "init"), 0);
        if (init != null) {
            List<Node> initExprs = getNodeByName(init, "expr");
            Node initNode = nodeAtIndex(initExprs, 0);
            if (initNode != null) {
                List<Node> initExpr = asList(initNode.getChildNodes());
                if (initExpr.size() > 0) {
                    NamePos initExprNamePos = evaluateExprs(initExpr);
                    checkAndUpdateDVarSliceProfile(namePos, initExprNamePos);
                }
            }
        }

        Node argumentListNode = nodeAtIndex(getNodeByName(decl, "argument_list"), 0);
        if (argumentListNode == null) {
            return namePos;
        }

        List<Node> argumentList = getNodeByName(argumentListNode, "argument");
        for (Node argument : argumentList) {
            List<Node> argExprList = getNodeByName(argument, "expr");
            Node argExpr = nodeAtIndex(argExprList, 0);
            if (argExpr == null) {
                continue;
            }
            for (Node expr : asList(argExpr.getChildNodes())) {
                analyzeExprAndUpdateDVar(namePos, expr);
            }
        }

        String typeName = namePos.getType();
        if (typeName != null && !typeName.isEmpty() && init == null) {
            NamePos typeNamePos = new NamePos(typeName, null, namePos.getPos(), false);
            String cfunctionName = typeNamePos.getName();
            String cfunctionPos = typeNamePos.getPos();
            boolean isPointer = namePos.isPointer() || typeNamePos.isPointer();
            addSliceProfile(cfunctionName, cfunctionPos, isPointer);
            analyzeCallArgumentList(decl, cfunctionName, cfunctionPos, cfunctionName);
            checkAndUpdateDVarSliceProfile(namePos, typeNamePos);
        }

        return namePos;
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
            updateDVarSliceProfile(namePos.getName(), exprVarName, localVariables);
        } else if (globalVariables.containsKey(exprVarName)) {
            updateDVarSliceProfile(namePos.getName(), exprVarName, globalVariables);
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
                case "name":
                    return analyzeNameExpr(expr);
                case "cast":
                    analyzeCastExpr(expr);
                    break;
                case "macro":
                    analyzeMacro(expr);
                    break;
            }
        }
        return new NamePos.DefaultNamePos();
    }

    private NamePos analyzeNameExpr(Node expr) {
        NamePos namePos = getNamePosTextPair(expr);
        String varName = namePos.getName();
        if (isArrayAccessExpr(expr) && !isLhsExpr) {
            SliceProfile varProfile = null;
            if (localVariables.containsKey(varName)) {
                varProfile = localVariables.get(varName);
            } else if (globalVariables.containsKey(varName)) {
                varProfile = globalVariables.get(varName);
            }

            if (varProfile != null) {
                DataTuple bufferWriteData = new DataTuple(DataAccessType.BUFFER_READ, namePos);
                SliceVariableAccess varAccess = new SliceVariableAccess();
                varAccess.addWritePosition(bufferWriteData);
                varProfile.usedPositions.add(varAccess);
            }
        }
        return namePos;
    }

    private NamePos analyzeLiteralExpr(Node literal) {
        String literalVal = literal.getTextContent();
        String typeName = literal.getAttributes().getNamedItem("type").getNodeValue();
        String pos = getNodePos(literal);
        String sliceKey = literalVal + "%" + pos + "%" + currentFunctionName + "%" + fileName;
        SliceProfile profile = new SliceProfile(fileName, currentFunctionName, literalVal, typeName,
            pos, false,
            currentFunctionNode);
        sliceProfiles.put(sliceKey, profile);
        localVariables.put(literalVal, profile);
        return new NamePos(literalVal, typeName, pos, false);
    }

    private NamePos analyzeOperatorExpr(Node expr) {
        String text;
        Node specificOpNode = nodeAtIndex(getNodeByName(expr.getParentNode(), "name"), 0);
        if (specificOpNode == null) {
            text = getNamePosTextPair(expr.getParentNode()).getName();
        } else {
            text = specificOpNode.getTextContent();
        }
        return new NamePos(text.split(IDENTIFIER_SEPARATOR)[0], "", getNodePos(expr), false);
    }

    private void analyzeTryBlock(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeBlock(nodeAtIndex(getNodeByName(stmt, "block"), 0));
        analyzeCatchBlock(nodeAtIndex(getNodeByName(stmt, "catch"), 0));
    }

    private void analyzeCatchBlock(Node catchBlock) {
        if (catchBlock == null) {
            return;
        }
        List<Node> param = getNodeByName(catchBlock, "parameter");
        for (Node node : param) {
            analyzeParam(node);
        }
        analyzeBlock(nodeAtIndex(getNodeByName(catchBlock, "block"), 0));
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
        NamePos cfunctionDetails = getNamePosTextPair(call);
        String cfunctionName = cfunctionDetails.getName();
        String cfunctionPos = cfunctionDetails.getPos();
        String cfunctionIdentifier = call.getTextContent().split(IDENTIFIER_SEPARATOR)[0];
        addSliceProfile(cfunctionIdentifier, cfunctionPos, cfunctionDetails.isPointer());
        analyzeCallArgumentList(call, cfunctionName, cfunctionPos, cfunctionIdentifier);
        return new NamePos(cfunctionIdentifier, "", cfunctionPos, false);
    }

    private void addSliceProfile(String varName, String position, boolean isPointer) {
        if (!localVariables.containsKey(varName) && !globalVariables.containsKey(varName)) {
            String sliceIdentifier = varName + "%" + position;
            String sliceKey = sliceIdentifier + "%" + currentFunctionName + "%" + fileName;
            SliceProfile cfunctionProfile = new SliceProfile(fileName, currentFunctionName, varName, null,
                position, isPointer, currentFunctionNode);
            sliceProfiles.put(sliceKey, cfunctionProfile);
            localVariables.put(varName, cfunctionProfile);
        }
    }

    private void addFunctionNameSliceProfile(String functionName, String functionPosition) {
        if (!globalVariables.containsKey(functionName)) {
            String sliceIdentifier = functionName + "%" + functionPosition;
            String sliceKey = sliceIdentifier + "%" + currentFunctionName + "%" + fileName;
            SliceProfile cfunctionProfile = new SliceProfile(fileName, currentFunctionName, functionName, null,
                functionPosition, currentFunctionNode, true);
            sliceProfiles.put(sliceKey, cfunctionProfile);
            globalVariables.put(functionName, cfunctionProfile);
        }
    }

    private void analyzeCallArgumentList(Node call, String cfunctionName, String cfunctionPos,
                                         String cfunctionIdentifier) {
        Node argumentNode = nodeAtIndex(getNodeByName(call, "argument_list"), 0);
        if (argumentNode == null) {
            return;
        }
        List<Node> argumentList = getNodeByName(argumentNode, "argument");
        if (argumentList.size() == 0) {
            for (String localVarName : localVariables.keySet()) {
                if (localVarName.equals("")) {
                    continue;
                }
                String sliceKey = localVarName + "%" + cfunctionPos + "%" + currentFunctionName + "%" + fileName;
                updateCFunctionsSliceProfile(localVarName, cfunctionName, cfunctionPos, -1, sliceKey,
                    localVariables, argumentList);
            }
            return;
        }
        for (int argPosIndex = 0; argPosIndex < argumentList.size(); argPosIndex++) {
            Node argExpr = argumentList.get(argPosIndex);
            Node argExprNode = nodeAtIndex(getNodeByName(argExpr, "expr"), 0);
            if (argExprNode == null) {
                return;
            }
            for (Node expr : asList(argExprNode.getChildNodes())) {
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
                    updateCFunctionsSliceProfile(varName, cfunctionName, cfunctionPos, argPosIndex, sliceKey,
                        localVariables, argumentList);
                    updateDVarSliceProfile(cfunctionIdentifier, varName, localVariables);
                } else if (globalVariables.containsKey(varName)) {
                    updateCFunctionsSliceProfile(varName, cfunctionName, cfunctionPos, argPosIndex, sliceKey,
                        globalVariables, argumentList);
                    updateDVarSliceProfile(cfunctionIdentifier, varName, globalVariables);
                } else if (isLiteralExpr(expr)) {
                    String typeName = varNamePos.getType();
                    SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName,
                        varName, typeName, varPos, varNamePos.isPointer(), this.currentFunctionNode);
                    CFunction cFun = new CFunction(argPosIndex, currentFunctionName, currentFunctionNode,
                        argumentList.size());
                    sliceProfile.cfunctions.add(cFun);
                    sliceProfiles.put(sliceKey, sliceProfile);
                }
            }
        }
    }

    private void analyzeCastExpr(Node castExpr) {
        if (castExpr == null) {
            return;
        }
        for (Node argumentList : getNodeByName(castExpr, "argument_list", true)) {
            for (Node argument : getNodeByName(argumentList, "argument")) {
                Node argExprNode = nodeAtIndex(getNodeByName(argument, "expr"), 0);
                if (argExprNode != null) {
                    for (Node expr : asList(argExprNode.getChildNodes())) {
                        analyzeExpr(expr);
                    }
                }
            }
        }
    }

    private void updateCFunctionsSliceProfile(String varName, String cfunctionName, String cfunctionPos,
                                              int argPosIndex, String sliceKey,
                                              Map<String, SliceProfile> sliceVariables,
                                              List<Node> argumentList) {
        SliceProfile sliceProfile = sliceVariables.get(varName);
        CFunction cFun = new CFunction(cfunctionName, cfunctionPos, argPosIndex, currentFunctionName,
            currentFunctionNode, argumentList.size());
        sliceProfile.cfunctions.add(cFun);
        sliceProfiles.put(sliceKey, sliceProfile);
    }

    private void analyzeIfStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        List<Node> ifBlocks = getNodeByName(stmt, "if");
        for (Node ifBlock : ifBlocks) {
            analyzeIfBlock(ifBlock);
        }

        analyzeElseBlock(nodeAtIndex(getNodeByName(stmt, "else"), 0));
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
        analyzeCompoundExpr(nodeAtIndex(getNodeByName(stmt, "condition"), 0));
        analyzeBlock(nodeAtIndex(getNodeByName(stmt, "block"), 0));
    }

    private void analyzeReturnStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeCompoundExpr(stmt);
    }

    private void analyzeElseBlock(Node node) {
        if (node == null) {
            return;
        }
        analyzeBlock(nodeAtIndex(getNodeByName(node, "block"), 0));
    }

    private void analyzeForStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        analyzeControl(nodeAtIndex(getNodeByName(stmt, "control"), 0));
        analyzeBlock(nodeAtIndex(getNodeByName(stmt, "block"), 0));
    }

    private void analyzeControl(Node control) {
        if (control == null) {
            return;
        }
        Node init = nodeAtIndex(getNodeByName(control, "init"), 0);
        if (init != null) {
            analyzeDecl(nodeAtIndex(getNodeByName(init, "decl"), 0));
        }
        analyzeConditionExpr(nodeAtIndex(getNodeByName(control, "condition"), 0));
        analyzeExpr(nodeAtIndex(getNodeByName(control, "incr"), 0));
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
        NamePos conditionNamePos = analyzeConditionExpr(nodeAtIndex(getNodeByName(expr, "condition"), 0));
        NamePos thenNamePos = analyzeCompoundExpr(nodeAtIndex(getNodeByName(expr, "then"), 0));
        NamePos elseNamePos = analyzeCompoundExpr(nodeAtIndex(getNodeByName(expr, "else"), 0));
        checkAndUpdateDVarSliceProfile(conditionNamePos, thenNamePos);
        checkAndUpdateDVarSliceProfile(conditionNamePos, elseNamePos);
        return conditionNamePos;
    }

    private ArgumentNamePos analyzeArgumentOfMacro(Node param) {
        if (param == null) {
            return null;
        }

        String paramNameWithType = param.getTextContent();
        if (paramNameWithType == null || paramNameWithType.isBlank()) {
            return null;
        }
        String[] parts = paramNameWithType.split("\\s+");

        if (parts.length < 2) {
            return null;
        }

        String type = parts[parts.length - 2];
        String name = parts[parts.length - 1];
        String pos = getNodePos(param);
        boolean isPointer = type.endsWith("*") || name.endsWith("*") || type.endsWith("&") || name.endsWith("&");
        String sliceKey = name + "%" + pos + "%" + this.currentFunctionName + "%" + this.fileName;
        SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName, name, type, pos,
            isPointer, this.currentFunctionNode);
        sliceProfiles.put(sliceKey, sliceProfile);
        localVariables.put(name, sliceProfile);

        return new ArgumentNamePos(name, type, pos, isPointer, false);
    }

    private ArgumentNamePos analyzeParam(Node param) {
        if (param == null) {
            return null;
        }
        Node decl = nodeAtIndex(getNodeByName(param, "decl"), 0);
        NamePos namePos = analyzeDecl(decl);
        if (namePos == null) {
            return null;
        }
        boolean isOptional = getNodeByName(decl, "init").size() > 0;
        return new ArgumentNamePos(namePos, isOptional);
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
        Node exprNode = nodeAtIndex(getNodeByName(compoundExpr, "expr"), 0);
        if (exprNode != null) {
            List<Node> exprs = asList(exprNode.getChildNodes());
            if (exprs.size() > 0) {
                if (isAssignmentExpr(exprs)) {
                    analyzeAssignmentExpr(exprs);
                } else {
                    return evaluateExprs(exprs);
                }
            }
        }

        return new NamePos.DefaultNamePos();
//      TODO: check for pointers and update slice profiles
    }

    private NamePos evaluateExprs(List<Node> exprNodes) {

        Stack<NamePos> exprs = new Stack<>();
        Stack<String> ops = new Stack<>();

        for (Node currentNode : exprNodes) {
            if (isEmptyTextNode(currentNode)) {
                continue;
            }

            if (isOpenBracketOperator(currentNode)) {
                ops.push("(");
            } else if (isCloseBracketOperator(currentNode)) {
                while (!ops.peek().equals("(")) {
                    ops.pop();
                    if (exprs.size() < 2) {
                        continue;
                    }
                    NamePos rhs = exprs.pop();
                    NamePos lhs = exprs.pop();
                    exprs.push(analyzeBinaryExpr(lhs, rhs));
                }
                ops.pop();
            } else if (isArithmeticOperator(currentNode)) {
                String operatorToken = currentNode.getFirstChild().getNodeValue();
                while (!ops.empty() && hasPrecedence(operatorToken, ops.peek())) {
                    ops.pop();
                    if (exprs.size() < 2) {
                        continue;
                    }
                    NamePos rhs = exprs.pop();
                    NamePos lhs = exprs.pop();
                    exprs.push(analyzeBinaryExpr(lhs, rhs));
                }
                ops.push(operatorToken);
            } else {
                exprs.push(analyzeExpr(currentNode));
            }
        }

        while (!ops.empty()) {
            ops.pop();
            if (exprs.size() < 2) {
                continue;
            }
            NamePos rhs = exprs.pop();
            NamePos lhs = exprs.pop();
            exprs.push(analyzeBinaryExpr(lhs, rhs));
        }

        if (exprs.size() == 0) {
            return new NamePos.DefaultNamePos();
        }

        return exprs.pop();
    }

    private NamePos analyzeBinaryExpr(NamePos lhsExprNamePos, NamePos rhsExprNamePos) {
        String lhsExprVarName = lhsExprNamePos.getName();
        String rhsExprVarName = rhsExprNamePos.getName();

        if (!lhsExprVarName.equals(rhsExprVarName)) { // TODO check for lhs == rhs
            updateDataWriteDVarAccess(lhsExprVarName, rhsExprVarName);
        }

        return lhsExprNamePos;
    }

    private void analyzeAssignmentExpr(List<Node> exprs) {
        if (exprs.size() < 5) {
            return;
        }
        Node lhsExpr = exprs.get(0);
        Node rhsExpr = exprs.get(4);

        isLhsExpr = true;
        NamePos lhsExprNamePos = analyzeExpr(lhsExpr);
        isLhsExpr = false;
        NamePos rhsExprNamePos = analyzeExpr(rhsExpr);

        String lhsExprVarName = lhsExprNamePos.getName();
        String rhsExprVarName = rhsExprNamePos.getName();

        if (lhsExprVarName == null || rhsExprVarName == null) {
            return;
        }

        if (isLhsExprFunctionPointer(lhsExprVarName)) { // this is track static function pointer load
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

        boolean isBufferWrite = isArrayAccessExpr(lhsExpr);
        if (!isBufferWrite && lhsExprVarName.equals(rhsExprVarName)) {
            return;
        }

        if (!lhsExprVarName.equals(rhsExprVarName)) {
            if (localVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, localVariables);
            } else if (globalVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, globalVariables);
            }
        }

        if (!isBufferWrite) {
            return;
        }

        SliceProfile lhsVarProfile = null;
        if (localVariables.containsKey(lhsExprVarName)) {
            lhsVarProfile = localVariables.get(lhsExprVarName);
        } else if (globalVariables.containsKey(lhsExprVarName)) {
            lhsVarProfile = globalVariables.get(lhsExprVarName);
        }

        if (lhsVarProfile == null) {
            return;
        }

        SliceProfile rhsVarProfile = null;
        if (localVariables.containsKey(rhsExprVarName)) {
            rhsVarProfile = localVariables.get(rhsExprVarName);
        } else if (globalVariables.containsKey(rhsExprVarName)) {
            rhsVarProfile = globalVariables.get(rhsExprVarName);
        }

        if (rhsVarProfile == null) {
            return;
        }

        DataTuple bufferWriteData = new DataTuple(XmlUtil.DataAccessType.BUFFER_WRITE, lhsExprNamePos);
        SliceVariableAccess varAccess = new SliceVariableAccess();
        varAccess.addWritePosition(bufferWriteData);
//        rhsVarProfile.usedPositions.add(varAccess);
        lhsVarProfile.usedPositions.add(varAccess);
    }

    private boolean isLhsExprFunctionPointer(String lhsExprVarName) {
        return functionNodes.keySet().stream()
            .anyMatch(namePos -> namePos.getName().equals(lhsExprVarName));
    }

    private boolean isArrayAccessExpr(Node expr) {
        if (!expr.getNodeName().equals("name")) {
            return false;
        }
        Node compTag = nodeAtIndex(getNodeByName(expr, "index"), 0);
        if (compTag == null) {
            return false;
        }
        List<Node> comp = getNodeByName(compTag, "expr");
        if (comp.size() > 0) {
            if (comp.size() == 1) {
                return !isLiteralExpr(comp.get(0));
            }
            return true;
        }
        return false;
    }

    private void checkAndUpdateDVarSliceProfile(NamePos lhsExprVarNamePos, NamePos rhsExprVarNamePos) {
        if (lhsExprVarNamePos == null || lhsExprVarNamePos.getName().isEmpty()) {
            return;
        }
        String lhsExprVarName = lhsExprVarNamePos.getName();
        if (rhsExprVarNamePos != null && !rhsExprVarNamePos.getName().isEmpty()) {
            String rhsExprVarName = rhsExprVarNamePos.getName();
            if (localVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, localVariables);
            } else if (globalVariables.containsKey(rhsExprVarName)) {
                updateDVarSliceProfile(lhsExprVarName, rhsExprVarName, globalVariables);
            }
        }
    }

    private void updateDataWriteDVarAccess(String lVarName, String rVarName) {
        if (localVariables.containsKey(rVarName)) {
            addDataWriteAccess(lVarName, localVariables.get(rVarName));
        } else if (globalVariables.containsKey(rVarName)) {
            addDataWriteAccess(lVarName, globalVariables.get(rVarName));
        }
    }

    private void addDataWriteAccess(String lVarName, SliceProfile rVarProfile) {
        SliceProfile lVarProfile;
        String lVarEnclFunctionName = currentFunctionName;
        if (globalVariables.containsKey(lVarName)) {
            lVarEnclFunctionName = GLOBAL;
            lVarProfile = globalVariables.get(lVarName);
        } else if (localVariables.containsKey(lVarName)) {
            lVarProfile = localVariables.get(lVarName);
        } else {
            return;
        }

        NamePos dataWriteVarNamePos = new NamePos(lVarName, lVarEnclFunctionName,
            lVarProfile.definedPosition,
            lVarProfile.isPointer);

        DataTuple dataWrite = new DataTuple(DataAccessType.DATA_WRITE, dataWriteVarNamePos);
        SliceVariableAccess varAccess = new SliceVariableAccess();
        varAccess.addWritePosition(dataWrite);
        rVarProfile.dataAccess.add(varAccess);
    }

    private void updateDVarSliceProfile(String lVarName, String rVarName,
                                        Map<String, SliceProfile> sliceVariables) {
        if ((lVarName == null || lVarName.isBlank()) && (rVarName == null || rVarName.isBlank())) {
            return;
        }

        SliceProfile profile = sliceVariables.get(rVarName);
        String lVarEnclFunctionName = currentFunctionName;

        SliceProfile lVarProfile;
        String lVarDefinedPos;
        if (globalVariables.containsKey(lVarName)) {
            lVarEnclFunctionName = GLOBAL;
            lVarProfile = globalVariables.get(lVarName);
        } else if (localVariables.containsKey(lVarName)) {
            lVarProfile = localVariables.get(lVarName);
        } else {
            return;
        }

        lVarDefinedPos = lVarProfile.definedPosition;

        NamePos dvarNamePos = new NamePos(lVarName, lVarEnclFunctionName, lVarDefinedPos, false);
        profile.dependentVars.add(dvarNamePos);
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
        if (expr == null) {
            return false;
        }

        if (expr.getFirstChild() == null) {
            return false;
        }

        return "literal".equals(expr.getFirstChild().getNodeName());
    }

    private static boolean hasPrecedence(String op1, String op2) {
        if (op2.equals("(") || op2.equals(")")) {
            return false;
        }
        return (!op1.equals("*") && !op1.equals("/")) ||
            (!op2.equals("+") && !op2.equals("-"));
    }

    private static boolean isArithmeticOperator(Node expr) {
        return expr.getNodeName().equals("operator") &&
            ARITHMETIC_OPRTS.contains(expr.getFirstChild().getNodeValue());
    }

    private static boolean isOpenBracketOperator(Node expr) {
        return expr.getNodeName().equals("operator") &&
            "(".equals(expr.getFirstChild().getNodeValue());
    }

    private static boolean isCloseBracketOperator(Node expr) {
        return expr.getNodeName().equals("operator") &&
            ")".equals(expr.getFirstChild().getNodeValue());
    }
}
