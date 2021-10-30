package ca.uwaterloo.swag;

import static ca.uwaterloo.swag.util.XmlUtil.asList;
import static ca.uwaterloo.swag.util.XmlUtil.getFunctionNamePos;
import static ca.uwaterloo.swag.util.XmlUtil.getNamePosTextPair;
import static ca.uwaterloo.swag.util.XmlUtil.getNodeByName;
import static ca.uwaterloo.swag.util.XmlUtil.getNodePos;
import static ca.uwaterloo.swag.util.XmlUtil.isEmptyTextNode;
import static ca.uwaterloo.swag.util.XmlUtil.nodeAtIndex;

import ca.uwaterloo.swag.models.ArgumentNamePos;
import ca.uwaterloo.swag.models.FunctionNamePos;
import ca.uwaterloo.swag.models.NamePos;
import ca.uwaterloo.swag.models.TypeSymbol;
import ca.uwaterloo.swag.util.XmlUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SymbolEnter {

    public static final String IDENTIFIER_SEPARATOR = "[^\\w]+";
    private static final String GLOBAL = "GLOBAL";
    private static final List<String> ARITHMETIC_OPRTS = Arrays.asList("+", "-", "*", "/");
    private final String fileName;
    private final Node unitNode;
    private final Set<TypeSymbol> symbols;
    private TypeSymbol currentStructSymbol;
    private String currentFunctionName;
    private Node currentFunctionNode;

    public SymbolEnter(Node unitNode, String fileName, Set<TypeSymbol> symbols) {
        this.unitNode = unitNode;
        this.fileName = fileName;
        this.symbols = symbols;
        this.currentStructSymbol = null;
        this.currentFunctionName = "";
        this.currentFunctionNode = null;
    }

    public void invoke() {
        String langAttribute = this.unitNode.getAttributes().getNamedItem("language").getNodeValue();
        if (langAttribute.equals("Java")) {
            analyzeJavaSource(unitNode);
        } else if (langAttribute.equals("C++") || langAttribute.equals("C")) {
            analyzeCPPSource(unitNode);
        }
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
                    NamePos declNamePos = this.analyzeGlobalDecl(node);
                    TypeSymbol declSymbol = new TypeSymbol(declNamePos.getName(), declNamePos.getType(),
                        currentStructSymbol, this.fileName + ":" + declNamePos.getPos());
                    symbols.add(declSymbol);
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
                case "macro":
                    this.analyzeMacro(node);
                    break;
                case "function_decl":
                case "function":
                case "constructor":
                case "destructor":
                    FunctionNamePos functionNamePos = this.analyzeFunction(node);
                    TypeSymbol funcSymbol = new TypeSymbol(functionNamePos.getName(), functionNamePos.getType(),
                        currentStructSymbol, this.fileName + ":" + functionNamePos.getPos());
                    symbols.add(funcSymbol);
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
        TypeSymbol structSymbol = new TypeSymbol(structName, structName, currentStructSymbol,
            this.fileName + ":" + structNamePos.getPos());
        this.symbols.add(structSymbol);

        // analyze struct body
        Node structNodeBlock = nodeAtIndex(getNodeByName(structNode, "block"), 0);
        if (structNodeBlock == null) {
            return;
        }
        TypeSymbol previousSymbol = currentStructSymbol;
        this.currentStructSymbol = structSymbol;
        analyzeCppClassBlockContent(getNodeByName(structNodeBlock, "private"));
        analyzeCppClassBlockContent(getNodeByName(structNodeBlock, "protected"));
        analyzeCppClassBlockContent(getNodeByName(structNodeBlock, "public"));
        analyzeCppSourceContent(structNodeBlock.getChildNodes());
        this.currentStructSymbol = previousSymbol;
    }

    private void analyzeCppClass(Node classNode) {
        if (classNode == null) {
            return;
        }
        NamePos classNamePos = getNamePosTextPair(classNode);
        String className = classNamePos.getName();
        TypeSymbol structSymbol = new TypeSymbol(className, className, currentStructSymbol,
            this.fileName + ":" + classNamePos.getPos());
        this.symbols.add(structSymbol);
        Node cppClassBlock = nodeAtIndex(getNodeByName(classNode, "block"), 0);
        if (cppClassBlock == null) {
            return;
        }
        TypeSymbol previousSymbol = currentStructSymbol;
        this.currentStructSymbol = structSymbol;
        analyzeCppClassBlockContent(getNodeByName(cppClassBlock, "private"));
        analyzeCppClassBlockContent(getNodeByName(cppClassBlock, "protected"));
        analyzeCppClassBlockContent(getNodeByName(cppClassBlock, "public"));
        this.currentStructSymbol = previousSymbol;
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
        NamePos classNamePos = getNamePosTextPair(classNode);
        String className = classNamePos.getName();
        TypeSymbol typeSymbol = new TypeSymbol(className, className, currentStructSymbol,
            this.fileName + ":" + classNamePos.getPos());
        this.symbols.add(typeSymbol);
        TypeSymbol previousStructSymbol = currentStructSymbol;
        currentStructSymbol = typeSymbol;

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
                    NamePos declNamePos = analyzeGlobalDecl(node);
                    TypeSymbol declSymbol = new TypeSymbol(declNamePos.getName(), declNamePos.getType(),
                        currentStructSymbol, this.fileName + ":" + declNamePos.getPos());
                    symbols.add(declSymbol);
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
                    FunctionNamePos functionNamePos = this.analyzeFunction(node);
                    TypeSymbol funcSymbol = new TypeSymbol(functionNamePos.getName(), functionNamePos.getType(),
                        currentStructSymbol, this.fileName + ":" + functionNamePos.getPos());
                    symbols.add(funcSymbol);
                    break;
            }
        }

        currentStructSymbol = previousStructSymbol;
    }

    private NamePos analyzeGlobalDecl(Node globalDeclNode) {
        if (globalDeclNode == null) {
            return null;
        }
        NamePos namePos = getNamePosTextPair(globalDeclNode);

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
                this.analyzeFunction(node);
            }
        }
    }

    private void analyzeMacro(Node macro) {
        if (macro == null) {
            return;
        }

        FunctionNamePos functionNamePos = getFunctionNamePos(macro);

        if (functionNamePos.getType() == null || functionNamePos.getType().isBlank()) {
            String typeName = findTypeOfMacro(macro);
            functionNamePos = new FunctionNamePos(new NamePos(functionNamePos.getName(), typeName,
                functionNamePos.getPos(), functionNamePos.isPointer()), functionNamePos.getFunctionDeclName());
        }

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
        analyzeBlockContent(macroBody);
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
    }

    private String findTypeOfMacro(Node macro) {
        Node previousSibling = macro.getPreviousSibling();
        if (isEmptyTextNode(previousSibling)) {
            return findTypeOfMacro(previousSibling);
        }
        return getNamePosTextPair(previousSibling).getName();
    }

    private FunctionNamePos analyzeFunction(Node function) {
        if (function == null) {
            return null;
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
        analyzeMemberInitList(function);
        analyzeBlock(nodeAtIndex(getNodeByName(function, "block"), 0));
        this.currentFunctionName = previousFunctionName;
        this.currentFunctionNode = previousFunctionNode;
        return functionNamePos;
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
                    case "decl_stmt":
                        NamePos declNamePos = analyzeGlobalDecl(stmt);
                        TypeSymbol declSymbol = new TypeSymbol(declNamePos.getName(), declNamePos.getType(),
                            currentStructSymbol, this.fileName + ":" + declNamePos.getPos());
                        symbols.add(declSymbol);
                        break;
                    case "function":
                    case "function_decl":
                        FunctionNamePos functionNamePos = analyzeFunction(stmt);
                        TypeSymbol funcSymbol = new TypeSymbol(functionNamePos.getName(), functionNamePos.getType(),
                            currentStructSymbol, this.fileName + ":" + functionNamePos.getPos());
                        symbols.add(funcSymbol);
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
        analyzeDecl(nodeAtIndex(getNodeByName(stmt, "decl"), 0));
    }

    private NamePos analyzeDecl(Node decl) {
        if (decl == null) {
            return null;
        }
        NamePos namePos = getNamePosTextPair(decl);
        Node init = nodeAtIndex(getNodeByName(decl, "init"), 0);
        if (init != null) {
            List<Node> initExprs = getNodeByName(init, "expr");
            Node initNode = nodeAtIndex(initExprs, 0);
            if (initNode != null) {
                List<Node> initExpr = asList(initNode.getChildNodes());
                if (initExpr.size() > 0) {
                    evaluateExprs(initExpr);
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
                analyzeExpr(expr);
            }
        }

        return namePos;
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
        return checkForIdentifierSeperatorAndUpdate(namePos, varName);
    }

    private NamePos analyzeLiteralExpr(Node literal) {
        String literalVal = literal.getTextContent();
        String typeName = literal.getAttributes().getNamedItem("type").getNodeValue();
        String pos = getNodePos(literal);
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
        return checkForIdentifierSeperatorAndUpdate(cfunctionDetails, cfunctionName);
    }

    private NamePos checkForIdentifierSeperatorAndUpdate(NamePos namePos, String varName) {
        String[] varNameParts = varName.split(IDENTIFIER_SEPARATOR);
        if (varNameParts.length > 0) {
            for (int i = 0; i < varNameParts.length; i++) {
                String varNamePartCurrent = varNameParts[i];
                namePos = new NamePos(varNamePartCurrent, namePos.getType(), namePos.getPos(), namePos.isPointer());
                if (i + 1 < varNameParts.length) {
                    String varNamePartNext = varNameParts[i + 1];
                    namePos = new NamePos(varNamePartNext, namePos.getType(), namePos.getPos(), namePos.isPointer());
                }
            }
        }
        return namePos;
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
        analyzeCompoundExpr(nodeAtIndex(getNodeByName(expr, "then"), 0));
        analyzeCompoundExpr(nodeAtIndex(getNodeByName(expr, "else"), 0));
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
        return lhsExprNamePos;
    }

    private void analyzeAssignmentExpr(List<Node> exprs) {
        if (exprs.size() < 5) {
            return;
        }
        Node lhsExpr = exprs.get(0);
        Node rhsExpr = exprs.get(4);

        analyzeExpr(lhsExpr);
        analyzeExpr(rhsExpr);
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