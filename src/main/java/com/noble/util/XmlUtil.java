package com.noble.util;

import java.util.*;

import com.noble.models.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.w3c.dom.*;

import static com.noble.SliceGenerator.IDENTIFIER_SEPARATOR;

public final class XmlUtil {

    public enum DataAccessType {
        @SuppressWarnings("unused") BUFFER_READ, BUFFER_WRITE
    }

    private XmlUtil() {
    }

    public static List<Node> asList(NodeList nodeList) {
        return nodeList.getLength() == 0 ? Collections.emptyList() : new NodeListWrapper(nodeList);
    }

    public static String getNodePos(Node tempNode) {
        return tempNode.getAttributes().getNamedItem("pos:start").getNodeValue();
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
        NamePos namePos = new NamePos("", "", "", false);
        if (node == null) {
            return namePos;
        }
        NodeList nodeList = node.getChildNodes();
        boolean isPointer = false;
        Set<String> names = new HashSet<>();
        names.add("decl");
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);

            if (tempNode.getNodeType() != Node.ELEMENT_NODE || !tempNode.hasAttributes() || !tempNode.hasChildNodes()) {
                continue;
            }
            if (tempNode.getNodeName().equals("name")) {
                String linePos = getNodePos(tempNode);
                if (tempNode.getNextSibling() != null &&
                        tempNode.getNextSibling().getNodeType() == Node.ELEMENT_NODE) {
                    if (((Element) tempNode.getNextSibling()).getTagName().equals("modifier") &&
                            tempNode.getNextSibling().getNodeValue() != null) {
                        isPointer = tempNode.getNextSibling().getNodeValue().equals("*") ||
                                tempNode.getNextSibling().getNodeValue().equals("&");
                    }
                }
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
                        false);
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

    public static Node nodeAtIndex(final List<Node> list, int index) {
        if (index < 0 || index >= list.size()) {
            return null;
        } else return list.get(index);
    }

    public static List<Node> findAllNodes(Node unitNode, String tag) {
        Element element = (Element) unitNode;
        NodeList allChilds = element.getElementsByTagName(tag);
        return asList(allChilds);
    }

    public static ArrayList<ArgumentNamePos> findFunctionParameters(Node enclFunctionNode) {
        ArrayList<ArgumentNamePos> parameters = new ArrayList<>();
        Node parameterList = nodeAtIndex(getNodeByName(enclFunctionNode, "parameter_list"), 0);
        if (parameterList == null) {
            return parameters;
        }
        getNodeByName(parameterList, "parameter").forEach(param -> {
            Node paramDecl = getNodeByName(param, "decl").get(0);
            List<Node> nameNode = getNodeByName(paramDecl, "name");
            boolean isOptional = getNodeByName(paramDecl, "init").size() > 0;
            if (nameNode.size() < 1) {
                parameters.add(new ArgumentNamePos("NoNameParam", "", String.valueOf(parameters.size()),
                        false, isOptional));
            } else {
                parameters.add(new ArgumentNamePos(getNamePosTextPair(nameNode.get(0)), isOptional));
            }
        });
        return parameters;
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