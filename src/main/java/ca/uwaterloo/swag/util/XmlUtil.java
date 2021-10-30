package ca.uwaterloo.swag.util;

import static ca.uwaterloo.swag.SliceGenerator.IDENTIFIER_SEPARATOR;

import ca.uwaterloo.swag.models.ArgumentNamePos;
import ca.uwaterloo.swag.models.FunctionNamePos;
import ca.uwaterloo.swag.models.NamePos;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.stream.Collectors;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XmlUtil {

    private XmlUtil() {
    }

    public static List<Node> asList(NodeList nodeList) {
        if (nodeList.getLength() == 0) {
            return Collections.emptyList();
        }
        return new NodeListWrapper(nodeList);
    }

    public static boolean isEmptyTextNode(Node node) {
        return node != null && node.getNodeName().equals("#text") && node.getNodeValue().isBlank();
    }

    public static String getNodePos(Node tempNode) {
        return tempNode.getAttributes().getNamedItem("pos:start").getNodeValue();
    }

    public static List<Node> getFunctionParamList(Node functionNode) {
        List<Node> functionParams = new ArrayList<>();
        List<Node> paramList = getNodeByName(functionNode, "parameter_list");

        if (paramList.size() == 1) {
            functionParams = getNodeByName(paramList.get(0), "parameter");
        }

        return functionParams;
    }

    public static List<Node> getArgumentList(Node functionNode) {
        List<Node> functionParams = new ArrayList<>();
        List<Node> paramList = getNodeByName(functionNode, "argument_list");

        if (paramList.size() == 1) {
            functionParams = getNodeByName(paramList.get(0), "argument");
        }

        return functionParams;
    }

    public static List<Node> getNodeByName(Node parent, String tag) {

        List<Node> namedNodes = getNodesBase(parent, tag);
        if (namedNodes.size() > 0) {
            return namedNodes;
        }
        NodeList deep = parent.getChildNodes();
        for (int i = 0, len = deep.getLength(); i < len; i++) {
            if (deep.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node childElement = deep.item(i);
                List<Node> attr = getNodeByName(childElement, tag);
                if (attr.size() > 0) {
                    return attr;
                }
            }
        }

        return namedNodes;
    }

    public static List<Node> getNodeByNameAtSameLevel(Node parent, String tag) {
        List<Node> namedNodes = getNodesBase(parent, tag);
        if (namedNodes.size() > 0) {
            return namedNodes;
        }
        NodeList deep = parent.getChildNodes();
        for (int i = 0, len = deep.getLength(); i < len; i++) {
            if (deep.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node childElement = deep.item(i);
                List<Node> attr = getNodesBase(childElement, tag);
                if (attr.size() > 0) {
                    return attr;
                }
            }
        }

        return namedNodes;
    }

    public static List<Node> getNodeByName(Node parent, String tag, Boolean all) {
        return getNodesByName(parent, tag, all);
    }

    private static List<Node> getNodesByName(Node parent, String tag, Boolean all) {
        List<Node> nodeList = new ArrayList<>(getNodesBase(parent, tag));

        NodeList deep = parent.getChildNodes();
        for (int i = 0, len = deep.getLength(); i < len; i++) {
            if (deep.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node childElement = deep.item(i);
                nodeList.addAll(getNodesByName(childElement, tag, all));
            }
        }
        return nodeList;
    }

    private static List<Node> getNodesBase(Node parent, String tag) {
        NodeList children = parent.getChildNodes();
        List<Node> namedNodes = new LinkedList<>(asList(children));
        for (int x = namedNodes.size() - 1; x >= 0; x--) {
            if (!namedNodes.get(x).getNodeName().equals(tag)) {
                namedNodes.remove(x);
            }
        }
        return namedNodes;
    }

    public static FunctionNamePos getFunctionNamePos(Node node) {
        NamePos namePos = getNamePosTextPair(node);
        String functionDeclName = namePos.getName();
        String nodeName = node.getNodeName();
        if (nodeName.equals("name")) {
            functionDeclName = node.getTextContent();
        }
        List<Node> nameNodeList = getNodeByName(node, "name");
        if (nameNodeList.size() == 1) {
            Node functionNameNode = nodeAtIndex(nameNodeList, 0);
            if (functionNameNode != null) {
                functionDeclName = functionNameNode.getTextContent();
            }
        }
        String[] parts = functionDeclName.split(IDENTIFIER_SEPARATOR);
        if (parts.length > 1) {
            functionDeclName = parts[parts.length - 1];
        }

        String functionType = namePos.getType();
        if ("constructor".equals(nodeName) || "destructor".equals(nodeName)) {
            functionType = functionDeclName;
        }
        return new FunctionNamePos(new NamePos(functionDeclName, functionType, namePos.getPos(),
            namePos.isPointer()), functionDeclName);
    }

    public static NamePos getNamePosTextPair(Node node) {
        // TODO refactor this method
        NamePos namePos = new NamePos.DefaultNamePos();
        if (node == null) {
            return namePos;
        }
        String linePos = "";
        StringBuilder nodeName = new StringBuilder();
        String typeName = "";
        boolean isPointer = false;
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() != Node.ELEMENT_NODE || !currentNode.hasAttributes() ||
                !currentNode.hasChildNodes()) {
                continue;
            }
            switch (currentNode.getNodeName()) {
                case "name":
                    nodeName.append(getNodeName(currentNode));
                    linePos = getNodePos(currentNode);
                    isPointer = isPointer(currentNode);
                    List<Node> typeNodeList = getNodeByNameAtSameLevel(currentNode.getParentNode(), "type");
                    if (typeNodeList.size() > 0) {
                        NodeList typeList = typeNodeList.get(0).getChildNodes();
                        for (int j = 0; j < typeList.getLength(); j++) {
                            Node typeNode = typeList.item(j);
                            if (typeNode.getNodeName().equals("name")) {
                                typeName = getNodeName(typeNode);
                                if (!isPointer) {
                                    isPointer = isPointer(typeNode);
                                }
                            }
                        }
                    }
                    break;
                case "operator":
                    nodeName.append(currentNode.getFirstChild().getNodeValue());
                    break;
                case "literal":
                    return new NamePos(currentNode.getTextContent(),
                        currentNode.getAttributes().getNamedItem("type").getNodeValue(), getNodePos(currentNode),
                        isPointer(currentNode));
                case "decl":
                    return getNamePosTextPair(currentNode);
            }
        }

        namePos = new NamePos(nodeName.toString(), typeName, linePos, isPointer);
        if (node.getNodeName().equals("name") && namePos.getName().equals("")) {
            namePos = new NamePos(node.getFirstChild().getNodeValue(), "", getNodePos(node),
                false);
        }
        return namePos;
    }

    private static boolean isPointer(Node node) {
        if (node.getNextSibling() == null || node.getNextSibling().getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }
        Node nextSibling = node.getNextSibling();
        if (((Element) nextSibling).getTagName().equals("modifier") && nextSibling.getFirstChild() != null) {
            String modifer = nextSibling.getFirstChild().getTextContent();
            return "*".equals(modifer) || "&".equals(modifer);
        }
        String nodeValue = nextSibling.getNodeValue();
        if (nodeValue == null) {
            return false;
        }
        return "*".equals(nodeValue) || "&".equals(nodeValue);
    }

    public static Node nodeAtIndex(final List<Node> list, int index) {
        if (index < 0 || index >= list.size()) {
            return null;
        } else {
            return list.get(index);
        }
    }

    public static List<Node> findAllNodes(Node unitNode, String tag) {
        Element element = (Element) unitNode;
        NodeList allChilds = element.getElementsByTagName(tag);
        return asList(allChilds);
    }

    public static List<ArgumentNamePos> findFunctionParameters(Node functionNode) {
        List<ArgumentNamePos> parameters = new ArrayList<>();
        Node parameterList = nodeAtIndex(getNodeByName(functionNode, "parameter_list"), 0);
        if (parameterList == null) {
            List<Node> argumentList = XmlUtil.getArgumentList(functionNode);
            if (argumentList.size() > 0) {
                parameters = argumentList
                    .stream()
                    .map(XmlUtil::getArgumentOfMacro)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            }
            return parameters;
        }
        List<ArgumentNamePos> finalParameters = parameters;
        for (Node param : getNodeByName(parameterList, "parameter")) {
            List<Node> decls = getNodeByName(param, "decl");
            if (decls.size() == 0) {
                continue;
            }
            Node paramDecl = decls.get(0);
            List<Node> nameNode = getNodeByName(paramDecl, "name");
            boolean isOptional = getNodeByName(paramDecl, "init").size() > 0;
            if (nameNode.size() < 1) {
                finalParameters.add(new ArgumentNamePos("NoNameParam", "",
                    String.valueOf(finalParameters.size()), false, isOptional));
            } else {
                finalParameters.add(new ArgumentNamePos(getNamePosTextPair(nameNode.get(0)), isOptional));
            }
        }
        return parameters;
    }

    private static ArgumentNamePos getArgumentOfMacro(Node param) {
        if (param == null) {
            return null;
        }

        String paramNameWithType = param.getTextContent();
        if (paramNameWithType == null || paramNameWithType.isBlank()) {
            return null;
        }
        String[] parts = paramNameWithType.split("\\s+");
        if (parts.length >= 2) {
            String type = parts[parts.length - 2];
            String name = parts[parts.length - 1];
            String pos = getNodePos(param);
            return new ArgumentNamePos(name, type, pos, false, false);
        }

        return null;
    }

    public enum DataAccessType {
        @SuppressWarnings("unused") BUFFER_READ, BUFFER_WRITE, DATA_READ, DATA_WRITE
    }

    static final class NodeListWrapper extends AbstractList<Node> implements RandomAccess {

        private final NodeList list;

        NodeListWrapper(NodeList list) {
            this.list = list;
        }

        public Node get(int index) {
            return list.item(index);
        }

        public int size() {
            return list.getLength();
        }
    }

    public static String getNodeName(final Node base) {
        StringBuilder value = new StringBuilder();
        NodeList nodeList = base.getChildNodes();
        if (nodeList.getLength() <= 0) {
            return value.toString();
        }
        int length = nodeList.getLength();
        for (int i = 0; i < length; i++) {
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.TEXT_NODE && !isEmptyTextNode(node)) {
                value.append(node.getNodeValue());
            }

            if (node.getNodeType() != Node.ELEMENT_NODE || !node.hasAttributes() || !node.hasChildNodes()) {
                continue;
            }

            NodeList chileNodeList = node.getChildNodes();
            if (chileNodeList.getLength() > 1) {
                value.append(getNodeName(node));
            } else {
                Node elNode = node.getFirstChild();
                if (elNode != null) {
                    if (elNode.getFirstChild() == null) {
                        value.append(elNode.getNodeValue());
                    } else {
                        value.append(getNodeName(elNode));
                    }
                }
            }
        }
        return value.toString();
    }
}