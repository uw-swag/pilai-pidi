package com.noble;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
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
import java.util.*;

import static com.noble.util.XmlUtil.*;
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

    static LinkedList<SliceProfile> analyzed_profiles= new LinkedList<>();
    private static MutableGraph<Encl_name_pos_tuple> DG = GraphBuilder.directed().build();
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
            Hashtable<String, SliceProfilesInfo> slice_profiles_info = new Hashtable<String, SliceProfilesInfo>();
            for(Node unit_node:asList(doc.getElementsByTagName("unit"))){
                Node fileName = unit_node.getAttributes().getNamedItem("filename");
                if(fileName!=null){
                    String source_file_path = fileName.getNodeValue();
                    if(unit_node.getNodeType() != Node.ELEMENT_NODE){
                        continue;
                    }
                    Hashtable<String, SliceProfile> slice_profiles = new Hashtable<String, SliceProfile>();
                    analyze_source_unit_and_build_slices(unit_node, source_file_path, slice_profiles);
                    List<Node> function_nodes = find_function_nodes(unit_node);
                    SliceProfilesInfo profile_info = new SliceProfilesInfo(slice_profiles,function_nodes,unit_node);
//                    System.out.println(source_file_path);
//                    System.out.println(function_nodes.size());
                    slice_profiles_info.put(source_file_path,profile_info);
                }
            }
            Hashtable<String, SliceProfilesInfo> java_slice_profiles_info = new Hashtable<>();
            Hashtable<String, SliceProfilesInfo> cpp_slice_profiles_info = new Hashtable<>();
            Enumeration<String> e = slice_profiles_info.keys();
            while (e.hasMoreElements()) {
                String key = e.nextElement();
                //                !key.contains("/test/")
                if(key.endsWith(".java")){
                    java_slice_profiles_info.put(key,slice_profiles_info.get(key));
                }
                else{
                    cpp_slice_profiles_info.put(key,slice_profiles_info.get(key));
                }
            }
            Enumeration<String> profiles_to_analyze = java_slice_profiles_info.keys();
            while (profiles_to_analyze.hasMoreElements()) {
                String key = profiles_to_analyze.nextElement();
                SliceProfilesInfo currentSlice = java_slice_profiles_info.get(key);
                Enumeration<String> slices_to_analyze = currentSlice.slice_profiles.keys();
                while (slices_to_analyze.hasMoreElements()) {
                    String keyS = slices_to_analyze.nextElement();
                    SliceProfile profile = currentSlice.slice_profiles.get(keyS);
                    analyzed_profiles.add(profile);
                    for (cFunction cfunction:profile.cfunctions
                         ) {
                        Encl_name_pos_tuple encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,cfunction.getCurrent_function_name(),profile.file_name,profile.defined_position);
                        SliceProfile[] dependent_slice_profiles = find_dependent_slice_profiles(cfunction.getCurrent_function_name(),cfunction.getArg_pos_index(),profile.type_name,cfunction.getCurrent_function_node(),java_slice_profiles_info);
                        encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,profile.function_name,profile.file_name,profile.defined_position);
                        DG.addNode(encl_name_pos_tuple);
                    }
//                    System.out.println(Arrays.toString(currentSlice.slice_profiles.get(keyS).cfunctions));
                }
            }
//

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

    private static SliceProfile[] find_dependent_slice_profiles(String current_function_name, int arg_pos_index, String type_name, Node current_function_node, Hashtable<String, SliceProfilesInfo> java_slice_profiles_info) {
        SliceProfile[] dependent_slice_profiles = new SliceProfile[]{};
        Enumeration<String> profiles_to_analyze = java_slice_profiles_info.keys();
        while (profiles_to_analyze.hasMoreElements()) {
            String key = profiles_to_analyze.nextElement();
            SliceProfilesInfo currentSlice = java_slice_profiles_info.get(key);
            Enumeration<String> slices_to_analyze = currentSlice.slice_profiles.keys();
            while (slices_to_analyze.hasMoreElements()) {
                String keyS = slices_to_analyze.nextElement();
                SliceProfile profile = currentSlice.slice_profiles.get(keyS);
                analyzed_profiles.add(profile);
                for (cFunction cfunction: find_possible_functions(currentSlice.function_nodes, current_function_name, arg_pos_index, current_function_node)
                ) {

//                    Encl_name_pos_tuple encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,cfunction.getCurrent_function_name(),profile.file_name,profile.defined_position);
//                    SliceProfile[] dependent_slice_profiles = find_dependent_slice_profiles(cfunction.getCurrent_function_name(),cfunction.getArg_pos_index(),profile.type_name,cfunction.getCurrent_function_node(),profiles_to_analyze);
//                    encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,profile.function_name,profile.file_name,profile.defined_position);
//                    DG.addNode(encl_name_pos_tuple);
                }
//                    System.out.println(Arrays.toString(currentSlice.slice_profiles.get(keyS).cfunctions));
            }
        }
        return dependent_slice_profiles;
    }

    private static LinkedList<cFunction> find_possible_functions(List<Node> function_nodes, String current_function_name, int arg_pos_index, Node current_function_node) {
        LinkedList<cFunction> possible_functions = new LinkedList<>();
        for (Node function:function_nodes) {
            if(getNamePosTextPair(function).getName()!=current_function_name) continue;
            List<Node> param = getNodeByName(function, "parameter");
            if(param.size()==0 || arg_pos_index>param.size()) continue;
            String param_name = getNamePosTextPair(param.get(arg_pos_index - 1)).getName();
            if(param_name.isBlank()) continue;
            if(!validate_function_against_call_expr(current_function_node, current_function_name, arg_pos_index-1, param)) continue;
            possible_functions.add(new cFunction(arg_pos_index-1,current_function_name,current_function_node,param));
        }
        return possible_functions;
    }

    private static boolean validate_function_against_call_expr(Node current_function_node, String current_function_name, int i, List<Node> param) {
        List<Node> call_argument_list;
        for (Node call:getNodeByName(current_function_node,"call")){
            String function_name = getNamePosTextPair(call).getName();
            if(!current_function_name.equals(function_name)) continue;
            call_argument_list = getNodeByName(call,"argument");
            if(call_argument_list.size()!=param.size()) continue;
            return true;
        }
        return false;
    }

    private static void analyze_source_unit_and_build_slices(Node unit_node, String source_file_path, Hashtable<String, SliceProfile> slice_profiles) {
        SliceGenerator slice_generator = new SliceGenerator(unit_node,source_file_path,slice_profiles);
        slice_generator.generate();
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
