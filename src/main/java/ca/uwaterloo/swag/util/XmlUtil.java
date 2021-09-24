package ca.uwaterloo.swag.util;

import static ca.uwaterloo.swag.SliceGenerator.IDENTIFIER_SEPARATOR;

import ca.uwaterloo.swag.models.ArgumentNamePos;
import ca.uwaterloo.swag.models.EnclNamePosTuple;
import ca.uwaterloo.swag.models.FunctionNamePos;
import ca.uwaterloo.swag.models.NamePos;
import ca.uwaterloo.swag.models.SliceProfilesInfo;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
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
        return node.getNodeName().equals("#text") && node.getNodeValue().isBlank();
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

    public static List<Node> getMacros(Node unitNode) {
        return getNodesByName(unitNode, "macro", true);
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

        if (node.getNodeName().equals("name")) {
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

        return new FunctionNamePos(namePos, functionDeclName);
    }

    public static NamePos getNamePosTextPair(Node node) {
        NamePos namePos = new NamePos.DefaultNamePos();
        if (node == null) {
            return namePos;
        }
        NodeList nodeList = node.getChildNodes();
//        boolean isPointer;
        Set<String> names = new HashSet<>();
        names.add("decl");
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);

            if (tempNode.getNodeType() != Node.ELEMENT_NODE || !tempNode.hasAttributes() || !tempNode
                .hasChildNodes()) {
                continue;
            }
            if (tempNode.getNodeName().equals("name")) {
                String linePos = getNodePos(tempNode);
                boolean isPointer = isPointer(tempNode);
                StringBuilder varType = new StringBuilder();
                try {
//                        NodeList typeList = tempNode.getParentNode().getChildNodes().item(0).getChildNodes();
                    List<Node> typNode = getNodeByName(tempNode.getParentNode(), "type");
                    if (!(typNode.size() < 1)) {
                        NodeList typeList = typNode.get(0).getChildNodes();
                        for (int c = 0; c < typeList.getLength(); c++) {
                            Node tempType = typeList.item(c);
                            if (tempType.getNodeName().equals("name")) {
                                String filler = "~";
                                if (varType.toString().equals("")) {
                                    filler = "";
                                }
//                                if(tempType.getChildNodes().getLength()) std :: String [ERR]
                                if (tempType.getLastChild().getNodeType() == Node.ELEMENT_NODE) {
                                    varType.append(filler).append(tempType.getLastChild().
                                        getFirstChild().getNodeValue());
                                } else {
                                    varType.append(filler).append(tempType.getLastChild().getNodeValue());
                                }
                            }

                            if (!isPointer) {
                                isPointer = isPointer(tempType);
                            }
                        }
                    }
//                      varType = tempNode.getParentNode().getNextSibling().getNextSibling().getChildNodes().item(0).getNodeValue();
                } catch (NullPointerException | IndexOutOfBoundsException e) {
                    varType = new StringBuilder();
                    e.printStackTrace();
                }
                if (tempNode.getFirstChild().getNodeType() == Node.ELEMENT_NODE) {
                    List<Node> nameChildren = getNodeByName(tempNode, "name");
                    namePos = new NamePos(nameChildren.get(nameChildren.size() - 1).getTextContent(),
                        varType.toString(), linePos, isPointer);
                } else {
                    namePos = new NamePos(tempNode.getFirstChild().getNodeValue(), varType.toString(),
                        linePos, isPointer);
                }
                break;
            } else if (tempNode.getNodeName().equals("literal")) {
                return new NamePos(tempNode.getTextContent(),
                    tempNode.getAttributes().getNamedItem("type").getNodeValue(), getNodePos(tempNode),
                    isPointer(tempNode));
            } else if (names.contains(tempNode.getNodeName())) {
                return getNamePosTextPair(tempNode);
            }
        }
        if (node.getNodeName().equals("name") && namePos.getName().equals("")) {
            namePos = new NamePos(node.getFirstChild().getNodeValue(), "", getNodePos(node),
                false);
        }
        return namePos;
    }

    private static boolean isPointer(Node node) {
        if (node.getNextSibling() == null ||
            node.getNextSibling().getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }

        Node nextSibling = node.getNextSibling();

        if (((Element) nextSibling).getTagName().equals("modifier") &&
            nextSibling.getFirstChild() != null) {
            return nextSibling.getFirstChild().getTextContent().equals("*");
        }

        if (nextSibling.getNodeValue() == null) {
            return false;
        }

        return nextSibling.getNodeValue().equals("*") ||
            nextSibling.getNodeValue().equals("&");

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

    @SuppressWarnings("unused")
    public static final class MyResult {

        private final ArrayList<EnclNamePosTuple> first;
        private final Hashtable<EnclNamePosTuple, ArrayList<String>> second;
        private final Hashtable<String, SliceProfilesInfo> javaSliceProfilesInfo;
        private final Graph<EnclNamePosTuple, DefaultEdge> dg;

        public MyResult(ArrayList<EnclNamePosTuple> first, Hashtable<EnclNamePosTuple,
            ArrayList<String>> second, Hashtable<String, SliceProfilesInfo> javaSliceProfilesInfo,
            Graph<EnclNamePosTuple, DefaultEdge> dg) {
            this.first = first;
            this.second = second;
            this.javaSliceProfilesInfo = javaSliceProfilesInfo;
            this.dg = dg;
        }

        public ArrayList<EnclNamePosTuple> getSourceNodes() {
            return first;
        }

        public Hashtable<EnclNamePosTuple, ArrayList<String>> getDetectedViolations() {
            return second;
        }

        public Hashtable<String, SliceProfilesInfo> getJavaSliceProfilesInfo() {
            return javaSliceProfilesInfo;
        }

        public Graph<EnclNamePosTuple, DefaultEdge> getDG() {
            return dg;
        }
    }
}