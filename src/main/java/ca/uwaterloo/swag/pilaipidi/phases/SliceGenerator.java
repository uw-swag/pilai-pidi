package ca.uwaterloo.swag.pilaipidi.phases;

import ca.uwaterloo.swag.pilaipidi.models.ArgumentNamePos;
import ca.uwaterloo.swag.pilaipidi.models.CFunction;
import ca.uwaterloo.swag.pilaipidi.models.DataAccess;
import ca.uwaterloo.swag.pilaipidi.models.DataAccess.DataAccessType;
import ca.uwaterloo.swag.pilaipidi.models.FunctionNamePos;
import ca.uwaterloo.swag.pilaipidi.models.NamePos;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfile;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfilesInfo;
import ca.uwaterloo.swag.pilaipidi.models.SliceVariableAccess;
import ca.uwaterloo.swag.pilaipidi.models.TypeSymbol;
import ca.uwaterloo.swag.pilaipidi.models.Value;
import ca.uwaterloo.swag.pilaipidi.util.XmlUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * {@link SliceGenerator} goes through all the source units from srcML and generates slice profiles by perfroming
 * forward source slicing.
 *
 * @since 0.0.1
 */
public class SliceGenerator {

    public static final String IDENTIFIER_SEPARATOR = "[^\\w]+";
    private static final String GLOBAL = "GLOBAL";
    private static final List<String> ARITHMETIC_OPRTS = Arrays.asList("+", "-", "*", "/", "<", ">", "<=", ">=");
    private final String fileName;
    private final Node unitNode;
    private final Set<TypeSymbol> typeSymbols;
    private final Map<FunctionNamePos, Node> functionNodes;
    private final Map<String, SliceProfile> sliceProfiles;
    private final Map<String, List<FunctionNamePos>> functionDeclMap;
    private final Map<String, SliceProfile> globalVariables;
    private Map<String, SliceProfile> localVariables;
    private String currentStructName;
    private String currentFunctionName;
    private Node currentFunctionNode;
    private boolean isLhsExpr;
    private boolean withinDeclStmt;
    private SliceProfile enclConditionProfile;

    public SliceGenerator(Node unitNode, String fileName, Set<TypeSymbol> typeSymbols) {
        this.unitNode = unitNode;
        this.fileName = fileName;
        this.sliceProfiles = new Hashtable<>();
        this.typeSymbols = typeSymbols;
        this.functionNodes = new Hashtable<>();
        this.functionDeclMap = new Hashtable<>();
        this.localVariables = new Hashtable<>();
        this.globalVariables = new Hashtable<>();
        this.currentStructName = "";
        this.currentFunctionName = "";
        this.currentFunctionNode = null;
    }

    private static boolean hasPrecedence(String op1, String op2) {
        if (op2.equals("(") || op2.equals(")")) {
            return false;
        }
        return (!op1.equals("*") && !op1.equals("/")) ||
                (!op2.equals("+") && !op2.equals("-"));
    }

    private static boolean isArithmeticOperator(Node expr) {
        return expr.getNodeName().equals("operator") && ARITHMETIC_OPRTS.contains(expr.getFirstChild().getNodeValue());
    }

    private static boolean isOpenBracketOperator(Node expr) {
        return expr.getNodeName().equals("operator") && "(".equals(expr.getFirstChild().getNodeValue());
    }

    private static boolean isCloseBracketOperator(Node expr) {
        return expr.getNodeName().equals("operator") && ")".equals(expr.getFirstChild().getNodeValue());
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
        NamePos structNamePos = XmlUtil.getNameAndPos(structNode);
        String structName = structNamePos.getName();
        String structTypeName = structNamePos.getType();
        Node structVarNameNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(structNode, "decl"), 0);
        NamePos structVarNamePos = XmlUtil.getNameAndPos(structVarNameNode);
        if (!structVarNamePos.getName().equals("")) {
            structName = structVarNamePos.getName();
        }
        boolean isPointer = structNamePos.isPointer() || structVarNamePos.isPointer();
        String structPos = structVarNamePos.getPos();
        String sliceKey = structName + "%" + structPos + "%" + GLOBAL + "%" + fileName;
        SliceProfile profile = new SliceProfile(fileName, GLOBAL, structName, structTypeName, structPos, isPointer);
        sliceProfiles.put(sliceKey, profile);
        globalVariables.put(structName, profile);

        // analyze struct body
        Node structNodeBlock = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(structNode, "block"), 0);
        if (structNodeBlock == null) {
            return;
        }
        String previousStructName = currentStructName;
        this.currentStructName = structName;
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(structNodeBlock, "private"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(structNodeBlock, "protected"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(structNodeBlock, "public"));
        analyzeCppSourceContent(structNodeBlock.getChildNodes());
        this.currentStructName = previousStructName;
    }

    private void analyzeCppClass(Node classNode) {
        if (classNode == null) {
            return;
        }
        NamePos classNamePos = XmlUtil.getNameAndPos(classNode);
        String className = classNamePos.getName();
        Node cppClassBlock = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(classNode, "block"), 0);
        if (cppClassBlock == null) {
            return;
        }
        String previousStructName = currentStructName;
        this.currentStructName = className;
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(cppClassBlock, "private"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(cppClassBlock, "protected"));
        analyzeCppClassBlockContent(XmlUtil.getNodeByName(cppClassBlock, "public"));
        this.currentStructName = previousStructName;
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

    private NamePos analyzeGlobalDecl(Node globalDeclNode) {
        if (globalDeclNode == null) {
            return null;
        }
        NamePos namePos = XmlUtil.getNameAndPos(globalDeclNode);
        String sliceKey = namePos.getName() + "%" + namePos.getPos() + "%" + GLOBAL + "%" + fileName;
        SliceProfile sliceProfile = new SliceProfile(fileName, GLOBAL, namePos.getName(), namePos.getType(),
                namePos.getPos(), namePos.isPointer());
        sliceProfiles.put(sliceKey, sliceProfile);
        globalVariables.put(namePos.getName(), sliceProfile);

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = GLOBAL;
        this.currentFunctionNode = null;
        List<Node> decls = XmlUtil.getNodeByName(globalDeclNode, "decl");
        for (Node decl : decls) {
            List<Node> nodeList = XmlUtil.getNodeByName(decl, "block");
            for (Node block : nodeList) {
                analyzeGlobaDeclBlock(block);
            }
        }
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
        return namePos;
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

        if (!withinDeclStmt) {
            this.localVariables = new Hashtable<>();
        }

        FunctionNamePos functionNamePos = XmlUtil.getFunctionNamePos(macro);
        if (functionNamePos.getType() == null || functionNamePos.getType().isBlank()) {
            String typeName = findTypeOfMacro(macro);
            functionNamePos = new FunctionNamePos(new NamePos(functionNamePos.getName(), typeName,
                    functionNamePos.getPos(), functionNamePos.isPointer()), functionNamePos.getFunctionDeclName());
        }

        String previousFunctionName = currentFunctionName;
        Node previousFunctionNode = currentFunctionNode;

        this.currentFunctionName = functionNamePos.getName();
        Node macroBody = null;
        Node block = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(macro.getParentNode(), "block"), 0);
        if (block != null) {
            Node blockContent = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(block, "block_content"), 0);
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

    private String findTypeOfMacro(Node macro) {
        Node previousSibling = macro.getPreviousSibling();
        if (XmlUtil.isEmptyTextNode(previousSibling)) {
            return findTypeOfMacro(previousSibling);
        }
        return XmlUtil.getNameAndPos(previousSibling).getName();
    }

    private FunctionNamePos analyzeFunction(Node function) {
        if (function == null) {
            return null;
        }
        FunctionNamePos functionNamePos = XmlUtil.getFunctionNamePos(function);
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
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(function, "block"), 0));
        updateFunctionArgDependencies(functionNamePos);
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
        return functionNamePos;
    }

    private void updateFunctionArgDependencies(NamePos functionNamePos) {
        SliceProfile functionNameProfile = addFunctionNameSliceProfile(functionNamePos);
        String functionName = functionNamePos.getName();
        if (functionName == null || functionName.isBlank()) {
            return;
        }
        for (String lVarName : localVariables.keySet()) {
            if (lVarName == null || lVarName.isBlank()) {
                continue;
            }

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
            functionNameProfile.dependentVars.add(dvarNamePos);
        }
    }

    private void analyzeMemberInitList(Node functionNode) {
        List<Node> memberInitList = XmlUtil.getNodeByName(functionNode, "member_init_list");
        for (Node memberInit : memberInitList) {
            for (Node expr : XmlUtil.asList(memberInit.getChildNodes())) {
                analyzeExpr(expr);
            }
        }
    }

    private void analyzeGlobaDeclBlock(Node block) {
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
        Node blockContent = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(block, "block_content"), 0);
        analyzeBlockContent(blockContent);
    }

    private void analyzeBlockContent(Node blockContent) {
        if (blockContent == null) {
            return;
        }

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

    private void analyzeDeclStmt(Node stmt) {
        if (stmt == null) {
            return;
        }
        this.withinDeclStmt = true;
        analyzeDecl(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "decl"), 0));
        this.withinDeclStmt = false;
    }

    private NamePos analyzeDecl(Node decl) {
        if (decl == null) {
            return null;
        }
        NamePos namePos = XmlUtil.getNameAndPos(decl);
        String varName = namePos.getName();
        String varPos = namePos.getPos();
        boolean isBuffer = namePos.isBuffer();
        String sliceKey = varName + "%" + varPos + "%" + this.currentFunctionName + "%" + this.fileName;
        SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName, varName,
                namePos.getType(), varPos, namePos.isPointer(), this.currentFunctionNode, isBuffer);
        sliceProfiles.put(sliceKey, sliceProfile);
        localVariables.put(varName, sliceProfile);

        if (enclConditionProfile != null) {
            sliceProfile.setEnclosingConditionProfile(enclConditionProfile);
        }

        if (isBuffer) {
            setBufferValueSize(namePos.getBufferSize(), sliceProfile);
        }

        Node init = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(decl, "init"), 0);
        if (init != null) {
            List<Node> initExprs = XmlUtil.getNodeByName(init, "expr");
            Node initNode = XmlUtil.nodeAtIndex(initExprs, 0);
            if (initNode != null) {
                List<Node> initExpr = XmlUtil.asList(initNode.getChildNodes());
                if (initExpr.size() > 0) {
                    NamePos initExprNamePos = evaluateExprs(initExpr);
                    checkAndUpdateDVarSliceProfile(namePos, initExprNamePos);
                    if (sliceProfile.isBuffer) {
                        if (initExprNamePos.isBuffer()) {
                            setVarValueToProfile(initExprNamePos.getBufferSize(), sliceProfile);
                        }
                    } else {
                        setVarValueToProfile(initExprNamePos, sliceProfile);
                    }
                }
            }
        }

        Node argumentListNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(decl, "argument_list"), 0);
        if (argumentListNode == null) {
            return namePos;
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
            boolean isPointer = namePos.isPointer() || typeNamePos.isPointer();
            addSliceProfile(cfunctionName, cfunctionPos, isPointer);
            analyzeCallArgumentList(decl, cfunctionName, cfunctionPos, cfunctionName);
            checkAndUpdateDVarSliceProfile(namePos, typeNamePos);
        }

        return namePos;
    }

    private void setBufferValueSize(NamePos bufferSizeNamePos, SliceProfile sliceProfile) {
        setVarValueToProfile(bufferSizeNamePos, sliceProfile);
        if (bufferSizeNamePos != null && XmlUtil.isNumeric(bufferSizeNamePos.getName())) {
            sliceProfile.getCurrentValue().setBufferSize(new Value(Integer.parseInt(bufferSizeNamePos.getName())));
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
        NamePos namePos = XmlUtil.getNameAndPos(expr);
        String varName = namePos.getName();
        if (isArrayAccessExpr(expr) && !isLhsExpr) {
            NamePos bufferSize = namePos.getBufferSize();
            SliceProfile varProfile = null;
            if (localVariables.containsKey(varName)) {
                varProfile = localVariables.get(varName);
            } else if (globalVariables.containsKey(varName)) {
                varProfile = globalVariables.get(varName);
            }
            if (varProfile != null) {
                DataAccess bufferRead = new DataAccess(DataAccessType.BUFFER_READ, namePos,
                        getBufferSizeAsValue(bufferSize));
                SliceVariableAccess varAccess = new SliceVariableAccess();
                varAccess.addReadPosition(bufferRead);
                varProfile.usedPositions.add(varAccess);
            }
        }
        return checkForIdentifierSeperatorAndUpdate(namePos, varName);
    }

    private NamePos analyzeLiteralExpr(Node literal) {
        String literalVal = literal.getTextContent();
        String typeName = literal.getAttributes().getNamedItem("type").getNodeValue();
        String pos = XmlUtil.getNodePos(literal);
        String sliceKey = literalVal + "%" + pos + "%" + currentFunctionName + "%" + fileName;
        SliceProfile profile = new SliceProfile(fileName, currentFunctionName, literalVal, typeName, pos,
                false, currentFunctionNode);
        sliceProfiles.put(sliceKey, profile);
        localVariables.put(literalVal, profile);
        NamePos literalExprNamePos = new NamePos(literalVal, typeName, pos, false);
        setVarValueToProfile(literalExprNamePos, profile);
        return literalExprNamePos;
    }

    private NamePos analyzeOperatorExpr(Node expr) {
        String text;
        Node specificOpNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr.getParentNode(), "name"), 0);
        if (specificOpNode == null) {
            text = XmlUtil.getNameAndPos(expr.getParentNode()).getName();
        } else {
            text = specificOpNode.getTextContent();
        }
        String[] split = text.split(IDENTIFIER_SEPARATOR);
        if (split.length == 0) {
            return null;
        }
        return new NamePos(split[0], "", XmlUtil.getNodePos(expr), false);
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
        NamePos cfunctionDetails = XmlUtil.getNameAndPos(call);
        String cfunctionName = cfunctionDetails.getName();
        String cfunctionPos = cfunctionDetails.getPos();
        String cfunctionIdentifier = cfunctionName.split(IDENTIFIER_SEPARATOR)[0];
        NamePos namePos = checkForCallFunctionAndUpdate(cfunctionDetails, cfunctionName);
        analyzeCallArgumentList(call, namePos.getName(), cfunctionPos, cfunctionIdentifier);
        return namePos;
    }

    private NamePos checkForCallFunctionAndUpdate(NamePos namePos, String varName) {
        String[] varNameParts = varName.split(IDENTIFIER_SEPARATOR);
        if (varNameParts.length > 1) {
            for (int i = 0; i < varNameParts.length; i++) {
                String varNamePartCurrent = varNameParts[i];
//                addSliceProfile(varNamePartCurrent, namePos.getPos(), namePos.isPointer());
                namePos = new NamePos(varNamePartCurrent, namePos.getType(), namePos.getPos(), namePos.isPointer(),
                    false);
                if (i + 1 >= varNameParts.length) {
                    continue;
                }
                String varNamePartNext = varNameParts[i + 1];
//                addSliceProfile(varNamePartNext, namePos.getPos(), namePos.isPointer());
//                updateDVarSliceProfile(varNamePartNext, varNamePartCurrent, localVariables);
                namePos = new NamePos(varNamePartNext, namePos.getType(), namePos.getPos(), namePos.isPointer(), false);
            }
        }
        return namePos;
    }

    private NamePos checkForIdentifierSeperatorAndUpdate(NamePos namePos, String varName) {
        String[] varNameParts = varName.split(IDENTIFIER_SEPARATOR);
        if (varNameParts.length > 1) {
            for (int i = 0; i < varNameParts.length; i++) {
                String varNamePartCurrent = varNameParts[i];
                addSliceProfile(varNamePartCurrent, namePos.getPos(), namePos.isPointer());
                namePos = new NamePos(varNamePartCurrent, namePos.getType(), namePos.getPos(), namePos.isPointer(),
                        false);
                if (i + 1 >= varNameParts.length) {
                    continue;
                }
                String varNamePartNext = varNameParts[i + 1];
                addSliceProfile(varNamePartNext, namePos.getPos(), namePos.isPointer());
                updateDVarSliceProfile(varNamePartNext, varNamePartCurrent, localVariables);
                namePos = new NamePos(varNamePartNext, namePos.getType(), namePos.getPos(), namePos.isPointer(), false);
            }
        }
        return namePos;
    }

    private SliceProfile addSliceProfile(String varName, String position, boolean isPointer) {
        SliceProfile sliceProfile;
        if (!localVariables.containsKey(varName)) {
            String sliceIdentifier = varName + "%" + position;
            String sliceKey = sliceIdentifier + "%" + currentFunctionName + "%" + fileName;
            Optional<TypeSymbol> typeSymbol = typeSymbols.stream()
                    .filter(symbol -> symbol.name.equals(varName))
                    .findFirst();
            String typeName = typeSymbol.map(symbol -> symbol.type).orElse(null);
            sliceProfile = new SliceProfile(fileName, currentFunctionName, varName, typeName, position, isPointer,
                    currentFunctionNode);
            sliceProfiles.put(sliceKey, sliceProfile);
            localVariables.put(varName, sliceProfile);
        } else {
            sliceProfile = localVariables.get(varName);
        }
        return sliceProfile;
    }

    private SliceProfile addFunctionNameSliceProfile(NamePos functionNamePos) {
        String functionName = functionNamePos.getName();
        String functionPosition = functionNamePos.getPos();
        String sliceIdentifier = functionName + "%" + functionPosition;
        String sliceKey = sliceIdentifier + "%" + currentFunctionName + "%" + fileName;
        if (sliceProfiles.containsKey(sliceKey)) {
            return sliceProfiles.get(sliceKey);
        }
        SliceProfile functionNameProfile = new SliceProfile(fileName, currentFunctionName, functionName,
                functionNamePos.getType(), functionPosition, currentFunctionNode, true);
        sliceProfiles.put(sliceKey, functionNameProfile);
        return functionNameProfile;
    }

    private void analyzeCallArgumentList(Node call, String cfunctionName, String cfunctionPos,
                                         String cfunctionIdentifier) {
        Node argumentNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(call, "argument_list"), 0);
        if (argumentNode == null) {
            return;
        }
        List<Node> argumentList = XmlUtil.getNodeByName(argumentNode, "argument");
        if (argumentList.size() == 0) {
            for (String localVarName : localVariables.keySet()) {
                if (localVarName.equals("")) {
                    continue;
                }
                String sliceKey = localVarName + "%" + cfunctionPos + "%" + currentFunctionName + "%" + fileName;
                SliceProfile sliceProfile = localVariables.get(localVarName);
                CFunction cFun = new CFunction(cfunctionName, cfunctionPos, cfunctionIdentifier, -1,
                        currentFunctionName, currentFunctionNode, argumentList.size(), new ArrayList<>());
                sliceProfile.cfunctions.add(cFun);
                sliceProfiles.put(sliceKey, sliceProfile);
            }
            return;
        }
        Map<String, SliceProfile> argProfiles = new LinkedHashMap<>();
        for (Node argExpr : argumentList) {
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
                    argProfiles.put(sliceKey, localVariables.get(varName));
                    SliceProfile argProfile = updateDVarSliceProfile(cfunctionIdentifier, varName, localVariables);
                    if (argProfile != null) {
                        argProfile.setEnclosingConditionProfile(enclConditionProfile);
                    }
                } else if (globalVariables.containsKey(varName)) {
                    argProfiles.put(sliceKey, globalVariables.get(varName));
                    SliceProfile argProfile = updateDVarSliceProfile(cfunctionIdentifier, varName, globalVariables);
                    if (argProfile != null) {
                        argProfile.setEnclosingConditionProfile(enclConditionProfile);
                    }
                } else if (XmlUtil.isLiteralExpr(expr)) {
                    String typeName = varNamePos.getType();
                    SliceProfile sliceProfile = new SliceProfile(this.fileName, this.currentFunctionName,
                            varName, typeName, varPos, varNamePos.isPointer(), this.currentFunctionNode);
                    setVarValueToProfile(varNamePos, sliceProfile);
                    sliceProfiles.put(sliceKey, sliceProfile);
                    argProfiles.put(sliceKey, sliceProfile);
                }
            }
        }
        int argPosIndex = 0;
        for (Entry<String, SliceProfile> entry : argProfiles.entrySet()) {
            updateCFunctionsSliceProfile(cfunctionName, cfunctionPos, cfunctionIdentifier, argPosIndex,
                    entry.getKey(), argProfiles, argumentList);
            argPosIndex++;
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

    private void updateCFunctionsSliceProfile(String cfunctionName, String cfunctionPos, String cfunctionIdentifier,
                                              int argPosIndex,
                                              String sliceKey, Map<String, SliceProfile> sliceVariables,
                                              List<Node> argumentList) {
        SliceProfile sliceProfile = sliceVariables.get(sliceKey);
        CFunction cFun = new CFunction(cfunctionName, cfunctionPos, cfunctionIdentifier, argPosIndex,
                currentFunctionName, currentFunctionNode, argumentList.size(),
                new ArrayList<>(sliceVariables.values()));
        sliceProfile.cfunctions.add(cFun);
        sliceProfiles.put(sliceKey, sliceProfile);
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
        NamePos conditionExpr = analyzeCompoundExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "condition"),
            0));
        assert conditionExpr != null;
        SliceProfile currentEnclCondProfile = enclConditionProfile;
        enclConditionProfile = getProfileFromNameExpr(conditionExpr);
        analyzeBlock(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(stmt, "block"), 0));
        enclConditionProfile = currentEnclCondProfile;
    }

    private SliceProfile getProfileFromNameExpr(NamePos namePos) {
        String exprName = namePos.getName();
        SliceProfile lhsVarProfile = null;
        if (localVariables.containsKey(exprName)) {
            lhsVarProfile = localVariables.get(exprName);
        } else if (globalVariables.containsKey(exprName)) {
            lhsVarProfile = globalVariables.get(exprName);
        }
        return lhsVarProfile;
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
        NamePos conditionNamePos = analyzeConditionExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr,
                "condition"), 0));
        NamePos thenNamePos = analyzeCompoundExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "then"),
                0));
        NamePos elseNamePos = analyzeCompoundExpr(XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "else"),
                0));
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
        String pos = XmlUtil.getNodePos(param);
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
        Node decl = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(param, "decl"), 0);
        NamePos namePos = analyzeDecl(decl);
        if (namePos == null) {
            return null;
        }
        boolean isOptional = XmlUtil.getNodeByName(decl, "init").size() > 0;
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
        Node exprNode = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(compoundExpr, "expr"), 0);
        if (exprNode != null) {
            List<Node> exprs = XmlUtil.asList(exprNode.getChildNodes());
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
            if (XmlUtil.isEmptyTextNode(currentNode)) {
                continue;
            }

            if (isOpenBracketOperator(currentNode)) {
                ops.push("(");
            } else if (isCloseBracketOperator(currentNode)) {
                while (!ops.peek().equals("(")) {
                    String op = ops.pop();
                    if (exprs.size() < 2) {
                        continue;
                    }
                    NamePos rhs = exprs.pop();
                    NamePos lhs = exprs.pop();
                    exprs.push(analyzeBinaryExpr(lhs, rhs, op));
                }
                ops.pop();
            } else if (isArithmeticOperator(currentNode)) {
                String operatorToken = currentNode.getFirstChild().getNodeValue();
                while (!ops.empty() && hasPrecedence(operatorToken, ops.peek())) {
                    String op = ops.pop();
                    if (exprs.size() < 2) {
                        continue;
                    }
                    NamePos rhs = exprs.pop();
                    NamePos lhs = exprs.pop();
                    exprs.push(analyzeBinaryExpr(lhs, rhs, op));
                }
                ops.push(operatorToken);
            } else {
                exprs.push(analyzeExpr(currentNode));
            }
        }

        while (!ops.empty()) {
            String op = ops.pop();
            if (exprs.size() < 2) {
                continue;
            }
            NamePos rhs = exprs.pop();
            NamePos lhs = exprs.pop();
            exprs.push(analyzeBinaryExpr(lhs, rhs, op));
        }

        if (exprs.size() == 0) {
            return new NamePos.DefaultNamePos();
        }

        return exprs.pop();
    }

    private NamePos analyzeBinaryExpr(NamePos lhsExprNamePos, NamePos rhsExprNamePos, String operator) {
        String lhsExprVarName = lhsExprNamePos.getName();
        String rhsExprVarName = rhsExprNamePos.getName();

        if (!lhsExprVarName.equals(rhsExprVarName)) { // TODO check for lhs == rhs
            updateDataWriteDVarAccess(lhsExprVarName, rhsExprVarName);
        }

        if ("<".equals(operator) || "<=".equals(operator)) {
            SliceProfile lhsVarProfile = null;
            if (localVariables.containsKey(lhsExprVarName)) {
                lhsVarProfile = localVariables.get(lhsExprVarName);
            } else if (globalVariables.containsKey(lhsExprVarName)) {
                lhsVarProfile = globalVariables.get(lhsExprVarName);
            }

            if (lhsVarProfile != null) {
                setVarValueToProfile(rhsExprNamePos, lhsVarProfile);
                if (enclConditionProfile != null) {
                    lhsVarProfile.setEnclosingConditionProfile(enclConditionProfile);
                }
            }
        } else if (">".equals(operator) || ">=".equals(operator)) {
            SliceProfile lhsVarProfile = null;
            if (localVariables.containsKey(lhsExprVarName)) {
                lhsVarProfile = localVariables.get(lhsExprVarName);
            } else if (globalVariables.containsKey(lhsExprVarName)) {
                lhsVarProfile = globalVariables.get(lhsExprVarName);
            }

            if (lhsVarProfile != null) {
                setVarValueToProfile(rhsExprNamePos, lhsVarProfile);
                if (enclConditionProfile != null) {
                    lhsVarProfile.setEnclosingConditionProfile(enclConditionProfile);
                }
            }
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
            List<FunctionNamePos> alias = new ArrayList<>();
            if (functionDeclMap.containsKey(lhsExprVarName)) {
                alias = functionDeclMap.get(lhsExprVarName);
            }

            FunctionNamePos rhsFunctionPointerName = XmlUtil.getFunctionNamePos(rhsExpr);
            alias.add(rhsFunctionPointerName);
            functionDeclMap.put(lhsExprVarName, alias);
        }

        boolean isBufferWrite = isArrayAccessExpr(lhsExpr);

        SliceProfile lhsVarProfile = null;
        if (localVariables.containsKey(lhsExprVarName)) {
            lhsVarProfile = localVariables.get(lhsExprVarName);
        } else if (globalVariables.containsKey(lhsExprVarName)) {
            lhsVarProfile = globalVariables.get(lhsExprVarName);
        }

        if (lhsVarProfile != null && !isBufferWrite) {
            setVarValueToProfile(rhsExprNamePos, lhsVarProfile);
        }

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

        if (!lhsExprVarName.equals(rhsExprVarName)) {
            lhsVarProfile.setAssignedProfile(rhsVarProfile);
        }

        if (enclConditionProfile != null) {
            lhsVarProfile.setEnclosingConditionProfile(enclConditionProfile);
            rhsVarProfile.setEnclosingConditionProfile(enclConditionProfile);
        }

        if (!isBufferWrite) {
            return;
        }

        DataAccess bufferWrite = new DataAccess(DataAccessType.BUFFER_WRITE, lhsExprNamePos,
                getBufferSizeAsValue(lhsExprNamePos.getBufferSize()));
        SliceVariableAccess varAccess = new SliceVariableAccess();
        varAccess.addWritePosition(bufferWrite);
        lhsVarProfile.usedPositions.add(varAccess);
    }

    private void setVarValueToProfile(NamePos rhsExprNamePos, SliceProfile lhsVarProfile) {
        if (rhsExprNamePos == null) {
            return;
        }
        if (XmlUtil.isNumeric(rhsExprNamePos.getName())) { // if literal expr, then set the value
            lhsVarProfile.setCurrentValue(new Value(Integer.parseInt(rhsExprNamePos.getName())));
        } else { // else this is a referenced value
            SliceProfile rhsVarProfile = null;
            String rhsExprVarName = rhsExprNamePos.getName();
            if (localVariables.containsKey(rhsExprVarName)) {
                rhsVarProfile = localVariables.get(rhsExprVarName);
            } else if (globalVariables.containsKey(rhsExprVarName)) {
                rhsVarProfile = globalVariables.get(rhsExprVarName);
            }
            if (rhsVarProfile != null) {
                lhsVarProfile.setCurrentValue(new Value(rhsVarProfile));
            }
        }
    }

    private Value getBufferSizeAsValue(NamePos bufferSizeNamePos) {
        if (bufferSizeNamePos == null) {
            return null;
        }
        String exprName = bufferSizeNamePos.getName();
        if (XmlUtil.isNumeric(exprName)) { // if literal expr, then set the value
            return new Value(Integer.parseInt(exprName));
        } else { // else this is a referenced value
            SliceProfile rhsVarProfile = null;
            if (localVariables.containsKey(exprName)) {
                rhsVarProfile = localVariables.get(exprName);
            } else if (globalVariables.containsKey(exprName)) {
                rhsVarProfile = globalVariables.get(exprName);
            }
            if (rhsVarProfile != null) {
                return new Value(rhsVarProfile);
            }
        }
        return null;
    }

    private boolean isLhsExprFunctionPointer(String lhsExprVarName) {
        return functionNodes.keySet().stream()
                .anyMatch(namePos -> namePos.getName().equals(lhsExprVarName));
    }

    private boolean isArrayAccessExpr(Node expr) {
        if (!expr.getNodeName().equals("name")) {
            return false;
        }
        Node compTag = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(expr, "index"), 0);
        if (compTag == null) {
            return false;
        }
        List<Node> comp = XmlUtil.getNodeByName(compTag, "expr");
        if (comp.size() > 0) {
            if (comp.size() == 1) {
                return !XmlUtil.isLiteralExpr(comp.get(0));
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

        NamePos dataWriteVarNamePos = new NamePos(lVarName, lVarEnclFunctionName, lVarProfile.definedPosition,
                lVarProfile.isPointer);

        DataAccess dataWrite = new DataAccess(DataAccessType.DATA_WRITE, dataWriteVarNamePos);
        SliceVariableAccess varAccess = new SliceVariableAccess();
        varAccess.addWritePosition(dataWrite);
        rVarProfile.dataAccess.add(varAccess);
    }

    private SliceProfile updateDVarSliceProfile(String lVarName, String rVarName,
                                        Map<String, SliceProfile> sliceVariables) {
        if ((lVarName == null || lVarName.isBlank()) && (rVarName == null || rVarName.isBlank())) {
            return null;
        }

        SliceProfile rVarProfile = sliceVariables.get(rVarName);
        String dVarEnclFunctionName = currentFunctionName;

        SliceProfile dVarProfile;
        String dVarDefinedPos;
        if (globalVariables.containsKey(lVarName)) {
            dVarEnclFunctionName = GLOBAL;
            dVarProfile = globalVariables.get(lVarName);
        } else if (localVariables.containsKey(lVarName)) {
            dVarProfile = localVariables.get(lVarName);
        } else {
            return rVarProfile;
        }

        dVarDefinedPos = dVarProfile.definedPosition;

        NamePos dvarNamePos = new NamePos(lVarName, dVarEnclFunctionName, dVarDefinedPos, false);
        rVarProfile.dependentVars.add(dvarNamePos);

        return rVarProfile;
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
}
