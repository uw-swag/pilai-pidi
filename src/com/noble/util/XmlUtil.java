package com.noble.util;

import java.util.*;
import org.w3c.dom.*;

public final class XmlUtil {
    private XmlUtil(){}

    public static List<Node> asList(NodeList n) {
        return n.getLength()==0?
                Collections.<Node>emptyList(): new NodeListWrapper(n);
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