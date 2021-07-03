package com.noble.util;

import java.util.*;

import com.noble.NamePos;
import org.w3c.dom.*;

public final class XmlUtil {
    private XmlUtil(){}
    public static List<Node> asList(NodeList n) {
        return n.getLength()==0?
                Collections.<Node>emptyList(): new NodeListWrapper(n);
    }
    private static String getNodePos(Node tempNode) {
        return tempNode.getAttributes().item(0).getNodeValue().split(":")[0];
    }
    public static List<Node> getNodeByName(Node parent, String tag){
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
    public static NamePos getNamePosTextPair(Node init_node) {
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

    static final class NodeListWrapper extends AbstractList<Node>
            implements RandomAccess {
        private final NodeList list;
        NodeListWrapper(NodeList l) {
            list=l;
        }
        public Node get(int index) {
            return list.item(index);
        }
        public int size() {
            return list.getLength();
        }
    }
    public static List<Node> appendNodeLists(NodeList a, NodeList b, NodeList c)
    {
        List<Node> nodes = new ArrayList<Node>();
        int aSize = a.getLength();
        int bSize = b.getLength();
        int cSize = c.getLength();
        if(aSize>0)
        {
            for (int i = 0; i < aSize; i++)
                nodes.add(a.item(i));
        }
        if(bSize>0)
        {
            for (int i = 0; i < bSize; i++)
                nodes.add(b.item(i));
        }
        if(cSize>0)
        {
            for (int i = 0; i < bSize; i++)
                nodes.add(c.item(i));
        }

        return nodes;
    }
}