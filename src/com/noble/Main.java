package com.noble;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;

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

    private static final String jni_native_method_modifier = "native";
    private static final String projectLocation = "C:\\Users\\elbon\\IdeaProjects\\jni-example";

    private static final Hashtable<String, SliceProfilesInfo> slice_profiles_info = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> java_slice_profiles_info = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> cpp_slice_profiles_info = new Hashtable<>();
    private static final MutableGraph<Encl_name_pos_tuple> DG = GraphBuilder.directed().build();
    public static final Hashtable<Encl_name_pos_tuple,ArrayList<String>> detected_violations = new Hashtable<>();

    static LinkedList<SliceProfile> analyzed_profiles= new LinkedList<>();

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
            for(Node unit_node:asList(doc.getElementsByTagName("unit"))){
                Node fileName = unit_node.getAttributes().getNamedItem("filename");
                if(fileName!=null){
                    String source_file_path = fileName.getNodeValue();
                    if(unit_node.getNodeType() != Node.ELEMENT_NODE){
                        continue;
                    }
                    Hashtable<String, SliceProfile> slice_profiles = new Hashtable<>();
                    analyze_source_unit_and_build_slices(unit_node, source_file_path, slice_profiles);
                    Hashtable<NamePos, Node> function_nodes = find_function_nodes(unit_node);
                    SliceProfilesInfo profile_info = new SliceProfilesInfo(slice_profiles,function_nodes,unit_node);
                    slice_profiles_info.put(source_file_path,profile_info);
                }
            }

//            build_srcml_and_srcslices

            Enumeration<String> e = slice_profiles_info.keys();
            while (e.hasMoreElements()) {
                String key = e.nextElement();
                if(key.endsWith(".java") && !key.contains("/test/")){
                    java_slice_profiles_info.put(key,slice_profiles_info.get(key));
                }
                else{
                    cpp_slice_profiles_info.put(key,slice_profiles_info.get(key));
                }
            }

//            analyze_sources

            Enumeration<String> profiles_to_analyze = java_slice_profiles_info.keys();
            while (profiles_to_analyze.hasMoreElements()) {
                String file_path = profiles_to_analyze.nextElement();
                SliceProfilesInfo currentSlice = java_slice_profiles_info.get(file_path);
                Enumeration<String> slices_to_analyze = currentSlice.slice_profiles.keys();
                while (slices_to_analyze.hasMoreElements()) {
                    String profile_id = slices_to_analyze.nextElement();
                    SliceProfile profile = currentSlice.slice_profiles.get(profile_id);
                    if(analyzed_profiles.contains(profile)) continue;
                    analyze_slice_profile(profile, java_slice_profiles_info);
                }
            }

            print_violations();

        } catch (URISyntaxException | IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private static void print_violations() {
        ArrayList<Encl_name_pos_tuple> source_nodes = new ArrayList<>();
        for(Encl_name_pos_tuple node:DG.nodes()){
            if (DG.inDegree(node) == 0)
            source_nodes.add(node);
        }
        int violations_count = 0;
        for(Encl_name_pos_tuple source_node: source_nodes){
            Enumeration<Encl_name_pos_tuple> violationE = detected_violations.keys();
            while (violationE.hasMoreElements()) {
                Encl_name_pos_tuple violated_node_pos_pair = violationE.nextElement();
                ArrayList<String> violations = detected_violations.get(violated_node_pos_pair);
                System.out.println("Possible out-of-bounds operation path");
//                TODO shortest path
                if(DG.hasEdgeConnecting(source_node, violated_node_pos_pair)){
                    Traverser.forGraph(DG).depthFirstPostOrder(source_node)
                            .forEach(x->System.out.print(x + " -> "));
                }
                violations.forEach(violation-> System.out.println("Reason : "+violation));
                violations_count = violations.size();
            }
        }
        System.out.println("No of files analyzed "+ java_slice_profiles_info.size());
        System.out.println("Detected violations "+ violations_count);
    }

    private static void analyze_slice_profile(SliceProfile profile, Hashtable<String, SliceProfilesInfo> raw_profiles_info) {
        analyzed_profiles.add(profile);

//                  step-01 : analyse cfunctions of the slice variable

        Encl_name_pos_tuple encl_name_pos_tuple;
        Enumeration<String> cfunction_k = profile.cfunctions.keys();
        while (cfunction_k.hasMoreElements()) {
            String cfunction_name = cfunction_k.nextElement();
            cFunction cfunction = profile.cfunctions.get(cfunction_name);
            int arg_pos_index = cfunction.getArg_pos_index();
            String cfunction_pos = cfunction.getCurrent_function_pos();
            String encl_function_name = cfunction.getCurrent_function_name();
            Node encl_function_node = cfunction.getCurrent_function_node();
            encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,encl_function_name,profile.file_name,profile.defined_position);
            analyze_cfunction(cfunction_name, cfunction_pos, arg_pos_index, profile.type_name, encl_function_node, encl_name_pos_tuple, raw_profiles_info);
        }
        encl_name_pos_tuple = new Encl_name_pos_tuple(profile.var_name,profile.function_name,profile.file_name,profile.defined_position);
        DG.addNode(encl_name_pos_tuple);

//                  step-02 : analyze data dependent vars of the slice variable

        for(NamePos dv: profile.dependent_vars){
            String dvar = dv.getName();
            String dvar_encl_function_name = dv.getType();
            String dvar_pos = dv.getPos();
            Hashtable<String, SliceProfile> source_slice_profiles = raw_profiles_info.get(profile.file_name).slice_profiles;
            String key = dvar + "%" + dvar_pos + "%" + dvar_encl_function_name + "%" + profile.file_name;
            if(!source_slice_profiles.containsKey(key)) {
//                not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvar_slice_profile = source_slice_profiles.get(key);
            Encl_name_pos_tuple dvar_name_pos_tuple = new Encl_name_pos_tuple(dvar_slice_profile.var_name, dvar_slice_profile.function_name, dvar_slice_profile.file_name, dvar_slice_profile.defined_position);
            if(has_no_edge(encl_name_pos_tuple,dvar_name_pos_tuple)){
                analyze_slice_profile(dvar_slice_profile,raw_profiles_info);
            }
        }

//                  step-03 : analyze if given function node is a native method

        if(!profile.function_name.equals("GLOBAL") && profile.cfunctions.size()<1){
            Node encl_function_node = profile.function_node;
            if (is_function_of_given_modifier(encl_function_node, jni_native_method_modifier)){
               analyze_native_function(profile, raw_profiles_info, encl_function_node, encl_name_pos_tuple);
            }
        }

//                  step-04 : check and add buffer reads and writes for this profile

        if(profile.file_name.endsWith(".cpp")||profile.file_name.endsWith(".c")||profile.file_name.endsWith(".cc")){
            for(SliceVariableAccess var_access:profile.used_positions){
                for(Tuple access:var_access.write_positions){
                    if(SliceGenerator.DataAccessType.BUFFER_WRITE == access.access_type){
                        ArrayList<String> currentArr = new ArrayList<>(detected_violations.get(encl_name_pos_tuple));
                        currentArr.add("Buffer write at " + access.access_pos);
                        detected_violations.put(encl_name_pos_tuple,currentArr);
                    }
                }
            }
        }

    }

    private static void analyze_cfunction(String cfunction_name, String cfunction_pos, int arg_pos_index, String var_type_name, Node encl_function_node, Encl_name_pos_tuple encl_name_pos_tuple, Hashtable<String, SliceProfilesInfo> slice_profiles_info) {
        LinkedList <SliceProfile> dependent_slice_profiles = find_dependent_slice_profiles(cfunction_name, arg_pos_index, var_type_name, encl_function_node, slice_profiles_info);
        if(dependent_slice_profiles.size()<1){
            if(cfunction_name.equals("strcpy") || cfunction_name.equals("strncpy") || cfunction_name.equals("memcpy")){
                DG.addNode(encl_name_pos_tuple);
                ArrayList<String> cErrors = new ArrayList<>();
                cErrors.add("Use of " + cfunction_name + " at " + cfunction_pos);
                detected_violations.put(encl_name_pos_tuple, cErrors);
            }
            return;
        }

        dependent_slice_profiles.forEach(dep_profile->{
            Encl_name_pos_tuple dep_name_pos_tuple = new Encl_name_pos_tuple(dep_profile.var_name, dep_profile.function_name, dep_profile.file_name, dep_profile.defined_position);
            if(!has_no_edge(encl_name_pos_tuple,dep_name_pos_tuple)) return;
            if(analyzed_profiles.contains(dep_profile)) return;
            analyze_slice_profile(dep_profile, slice_profiles_info);
        });
    }

    private static LinkedList<SliceProfile> find_dependent_slice_profiles(String current_function_name, int arg_pos_index, String type_name, Node current_function_node, Hashtable<String, SliceProfilesInfo> java_slice_profiles_info) {
        LinkedList <SliceProfile> dependent_slice_profiles = new LinkedList<>();
        Enumeration<String> profiles_to_analyze = java_slice_profiles_info.keys();
        while (profiles_to_analyze.hasMoreElements()) {
            String keyP = profiles_to_analyze.nextElement();
            SliceProfilesInfo currentSlice = java_slice_profiles_info.get(keyP);
            Enumeration<String> slices_to_analyze = currentSlice.slice_profiles.keys();
            while (slices_to_analyze.hasMoreElements()) {
                String keyS = slices_to_analyze.nextElement();
                SliceProfile profile = currentSlice.slice_profiles.get(keyS);
                analyzed_profiles.add(profile);
                for (cFunction cfunction: find_possible_functions(currentSlice.function_nodes, current_function_name, arg_pos_index, current_function_node)
                ) {
                    NamePos param = getNamePosTextPair(cfunction.getFunc_args().get(arg_pos_index - 1));
                    String param_name = param.getName();
                    String param_pos = param.getPos();
                    String key = param_name + "%" + param_pos + "%" + current_function_name + "%" + keyS;
                    if(!currentSlice.slice_profiles.containsKey(key)) continue;
                    dependent_slice_profiles.add(currentSlice.slice_profiles.get(key));
                }
            }
        }
        return dependent_slice_profiles;
    }


    private static void analyze_native_function(SliceProfile profile, Hashtable<String, SliceProfilesInfo> profiles_info, Node encl_function_node, Encl_name_pos_tuple encl_name_pos_tuple) {
        Node encl_unit_node = profiles_info.get(profile.file_name).unit_node;
        String jni_function_name = profile.function_name;
        String jni_arg_name = profile.var_name;
        ArrayList<NamePos> params = find_function_parameters(encl_function_node);
        int index = 0;
        for(NamePos par:params){
            index++;
            if(par.getName().equals(jni_arg_name)) break;
        }
//        TODO invalid
        int jni_arg_pos_index = index + 2;
        String clazz_name = getNodeByName(getNodeByName(encl_unit_node,"class").get(0),"name").get(0).getTextContent();
        String jni_function_search_str = "_" + clazz_name + "_" + jni_function_name;

        Enumeration<String> profiles_to_analyze = cpp_slice_profiles_info.keys();
        while (profiles_to_analyze.hasMoreElements()) {
            String file_path = profiles_to_analyze.nextElement();
            SliceProfilesInfo profile_info = java_slice_profiles_info.get(file_path);
            profile_info.function_nodes.forEach((func,function_node)->{
                String function_name = func.getName();
                if(!function_name.toLowerCase().endsWith(jni_function_search_str.toLowerCase(Locale.ROOT))) return;
                ArrayList<NamePos> function_args = find_function_parameters(function_node);
                if(function_args.size()<1 || jni_arg_pos_index>function_args.size()) return;
                NamePos arg = function_args.get(jni_arg_pos_index);
                String key = arg.getName() + "%" + arg.getPos() +"%" + function_name + "%" + file_path;
                SliceProfile possible_slice_profile = null;
                Enumeration<String> profiles_prob = profile_info.slice_profiles.keys();
                while (profiles_prob.hasMoreElements()) {
                    String cpp_profile_id = profiles_prob.nextElement();
                    SliceProfile cpp_profile = profile_info.slice_profiles.get(cpp_profile_id);
                    if(cpp_profile_id.equals(key)) possible_slice_profile = cpp_profile;
                }
                if(possible_slice_profile == null) return;
                Encl_name_pos_tuple analyzed_name_pos_tuple = new Encl_name_pos_tuple(possible_slice_profile.var_name, possible_slice_profile.function_name, possible_slice_profile.file_name, possible_slice_profile.defined_position);
                if (has_no_edge(encl_name_pos_tuple, analyzed_name_pos_tuple)) return;
                if (analyzed_profiles.contains(possible_slice_profile)) return;
                analyze_slice_profile(possible_slice_profile,cpp_slice_profiles_info);
            });
        }
    }

    private static boolean is_function_of_given_modifier(Node encl_function_node, String jni_native_method_modifier) {
        return getNodeByName(encl_function_node,jni_native_method_modifier).size()>0;
    }

    private static boolean has_no_edge(Encl_name_pos_tuple source_name_pos_tuple, Encl_name_pos_tuple target_name_pos_tuple) {
        if(source_name_pos_tuple == target_name_pos_tuple) return false;
        if(DG.hasEdgeConnecting(source_name_pos_tuple,target_name_pos_tuple)) return false;
        DG.putEdge(source_name_pos_tuple,target_name_pos_tuple);
        return true;
    }

    private static LinkedList<cFunction> find_possible_functions(Hashtable<NamePos, Node> function_nodes, String current_function_name, int arg_pos_index, Node current_function_node) {
        LinkedList<cFunction> possible_functions = new LinkedList<>();
        Enumeration<NamePos> e = function_nodes.keys();
        while (e.hasMoreElements()) {
            NamePos key = e.nextElement();
            Node function = function_nodes.get(key);
            if(!key.getName().equals(current_function_name)) continue;
            List<Node> param = getNodeByName(function, "parameter");
            if(param.size()==0 || arg_pos_index>param.size()) continue;
            String param_name = getNamePosTextPair(param.get(arg_pos_index - 1)).getName();
            if(param_name.isBlank()) continue;
            if(!validate_function_against_call_expr(current_function_node, current_function_name, arg_pos_index-1, param)) continue;
            possible_functions.add(new cFunction(arg_pos_index-1,current_function_name,"",current_function_node,param));
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


    private static Hashtable<NamePos, Node> find_function_nodes(Node unit_node) {
        Hashtable<NamePos, Node> function_nodes = new Hashtable<>();
        Element eElement = (Element) unit_node;
//        NodeList nList = unit_node.getChildNodes();
//        Stream<Node> nodeStream = IntStream.range(0, nList.getLength()).mapToObj(nList::item);
//        Stream<Node> filterStream = nodeStream.filter(Main::isElementOfInterest);
//        return  filterStream.collect(Collectors.toList()) ;

        NodeList fun1 = eElement.getElementsByTagName("function");
        NodeList fun2 = eElement.getElementsByTagName("function_decl");
        NodeList fun3 = eElement.getElementsByTagName("constructor");

        for(Node node:appendNodeLists(fun1,fun2,fun3)){
            function_nodes.put(getNamePosTextPair(node),node);
        }
        return function_nodes;
    }
//    public static native String srcml();
//
//    public static void main(String[] args) {
//        System.out.println(srcml());
//    }

}
