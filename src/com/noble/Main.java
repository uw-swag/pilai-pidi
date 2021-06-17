package com.noble;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import static com.noble.util.XmlUtil.appendNodeLists;
import static com.noble.util.XmlUtil.asList;

final class OsUtils
{
    private static String OS = null;
    public static String getOsName()
    {
        if(OS == null) { OS = System.getProperty("os.name"); }
        return OS;
    }
    public static boolean isWindows()
    {
        return getOsName().startsWith("Windows");
    }
}

public class Main {

    private static final String projectLocation = "C:\\Users\\elbon\\IdeaProjects\\jni-example";

    public static void stringToDom(String xmlSource)
            throws IOException {
        java.io.FileWriter fw = new java.io.FileWriter("temp.xml");
        fw.write(xmlSource);
        fw.close();
    }

    public static void main(String[] args) {
        String srcML = "ubuntu/srcml";
        if(OsUtils.isWindows()){
            srcML = "windows/srcml.exe";
        }
        URL res = Main.class.getClassLoader().getResource(srcML);
        try {
            assert res != null;
            File file = Paths.get(res.toURI()).toFile();
            ProcessBuilder pb = new ProcessBuilder(file.getAbsolutePath(), projectLocation, "--position");
            Process process = pb.start();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ( (line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
            String result = builder.toString();
            stringToDom(result);
            System.out.println("Converted to XML, beginning parsing ...");
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(result)));
//            Object slice_profiles_info = new Object[]{};
            for(Node unit_node:asList(doc.getElementsByTagName("unit"))){
                Node fileName = unit_node.getAttributes().getNamedItem("filename");
                if(fileName!=null){
//                    Object slice_profiles = new Object[]{};
                    String source_file_path = fileName.getNodeValue();
                    if(unit_node.getNodeType() != Node.ELEMENT_NODE){
                        continue;
                    }
                    List<Node> function_nodes = find_function_nodes(unit_node);
                    System.out.println(source_file_path);
                    System.out.println(function_nodes.size());
                }
            }

//            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
//            InputStream in = new FileInputStream("temp.xml");
//            XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);
//            streamReader.nextTag();
//            streamReader.nextTag();
//            while (streamReader.hasNext()) {
//                if (streamReader.isStartElement()) {
//                    System.out.println(streamReader.getLocalName());
////                    switch (streamReader.getLocalName()) {
////                        case "first": {
////                            System.out.print("First Name : ");
////                            System.out.println(streamReader.getElementText());
////                            break;
////                        }
////                    }
//                }
//                streamReader.next();
//            }

        } catch (URISyntaxException | IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }
//    private static boolean isElementOfInterest(Node nNode)
//    {
//        System.out.println(nNode.getNodeName());
//        return nNode.getNodeName().equals("function") || nNode.getNodeName().equals("function_decl") || nNode.getNodeName().equals("constructor");
//    }


    private static List<Node> find_function_nodes(Node unit_node) {
        Element eElement = (Element) unit_node;
//        NodeList nList = unit_node.getChildNodes();
//        Stream<Node> nodeStream = IntStream.range(0, nList.getLength()).mapToObj(nList::item);
//        Stream<Node> filterStream = nodeStream.filter(Main::isElementOfInterest);
//        return  filterStream.collect(Collectors.toList()) ;

        NodeList fun1 = eElement.getElementsByTagName("function");
        NodeList fun2 = eElement.getElementsByTagName("function_decl");
        NodeList fun3 = eElement.getElementsByTagName("constructor");

        return appendNodeLists(fun1,fun2,fun3);

    }
//    public static native String srcml();
//
//    public static void main(String[] args) {
//        System.out.println(srcml());
//    }

}
