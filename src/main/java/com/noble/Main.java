package com.noble;

import com.noble.models.*;
import com.noble.util.OsUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.noble.MODE.TESTING;
import static com.noble.util.XmlUtil.*;

public class Main {

    private static final List<String> buffer_error_functions = Arrays.asList("strcat", "strdup", "strncat", "strcmp",
            "strncmp", "strcpy", "strncpy", "strlen", "strchr", "strrchr", "index", "rindex", "strpbrk", "strspn",
            "strcspn", "strstr", "strtok", "memccpy", "memchr", "memmove", "memcpy", "memcmp", "memset", "bcopy",
            "bzero", "bcmp");
    private static final String jni_native_method_modifier = "native";
    private static final Hashtable<String, SliceProfilesInfo> slice_profiles_info = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> java_slice_profiles_info = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> cpp_slice_profiles_info = new Hashtable<>();
    private static final Graph<EnclNamePosTuple, DefaultEdge> DG = new DefaultDirectedGraph<>(DefaultEdge.class);
    public static final Hashtable<EnclNamePosTuple, ArrayList<String>> detected_violations = new Hashtable<>();
    public static final String JAR = "jar";
    private static final MODE mode = com.noble.MODE.NON_TESTING;

    static LinkedList<SliceProfile> analyzed_profiles = new LinkedList<>();

    public static void main(String[] args) {
        nonCLI(args);
    }

    public static boolean containsAllWords(String word, List<String> keywords) {
        for (String k : keywords) {
            if (!word.contains(k)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Hashtable<String, Set<List<EnclNamePosTuple>>> nonCLI(String[] args) {
        long start = System.currentTimeMillis();
        String projectLocation = null;
        String srcML = null;
        File file;
        File tempLoc = null;
        String result = null;

        try {
            if (Files.exists(Path.of("skip.txt")) && mode.skipSrcml())
                result = Files.readString(Path.of("skip.txt"), StandardCharsets.UTF_8);
            else {
                URI uri = Objects.requireNonNull(Main.class.getClassLoader().
                        getResource("windows/srcml.exe")).toURI();
                if (JAR.equals(uri.getScheme())) {
                    for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
                        if (provider.getScheme().equalsIgnoreCase(JAR)) {
                            try {
                                provider.getFileSystem(uri);
                            } catch (FileSystemNotFoundException e) {
                                // in this case we need to initialize it first:
                                provider.newFileSystem(uri, Collections.emptyMap());
                            }
                        }
                    }
                }
                if (args.length > 1) {
                    projectLocation = args[0];
                    srcML = args[1];
                } else if (args.length == 1) {
                    projectLocation = args[0];
                    if (OsUtils.isWindows()) {
                        srcML = "windows/srcml.exe";
                    } else if (OsUtils.isLinux()) {
                        srcML = "ubuntu/srcml";
                    } else if (OsUtils.isMac()) {
                        srcML = "mac/srcml";
                    } else {
                        System.err.println("Please specify location of srcML, binary not included for current OS");
                        System.exit(1);
                    }
                } else {
                    System.err.println("Please specify location of project to be analysed");
                    System.exit(1);
                }
            }
            if (!mode.skipSrcml() || result == null) {
                ProcessBuilder pb;
                if (args.length > 1) {
                    pb = new ProcessBuilder(srcML, projectLocation, "--position");
                } else {
                    Path zipPath = Paths.get(Objects.requireNonNull(Main.class.getClassLoader().getResource(srcML)).toURI());
                    InputStream in = Files.newInputStream(zipPath);
                    //noinspection ConstantConditions
                    file = File.createTempFile("PREFIX", "SUFFIX", tempLoc);
                    file.setExecutable(true);
                    file.deleteOnExit();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        IOUtils.copy(in, out);
                    }
                    pb = new ProcessBuilder(file.getAbsolutePath(), projectLocation, "--position");
                }
                result = IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
                try (PrintWriter out = new PrintWriter("skip.txt")) {
                    out.println(result);
                }
            }
            System.out.println("Converted to XML, beginning parsing ...");
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(result)));
            for (Node unit_node : asList(doc.getElementsByTagName("unit"))) {
                Node fileName = unit_node.getAttributes().getNamedItem("filename");
                if (fileName != null) {
                    String source_file_path = fileName.getNodeValue();
                    if (unit_node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Hashtable<String, SliceProfile> slice_profiles = new Hashtable<>();
                    analyze_source_unit_and_build_slices(unit_node, source_file_path, slice_profiles);
                    Hashtable<NamePos, Node> function_nodes = find_function_nodes(unit_node);
                    SliceProfilesInfo profile_info = new SliceProfilesInfo(slice_profiles, function_nodes, unit_node);
                    slice_profiles_info.put(source_file_path, profile_info);
                }
            }

            for (String sliceKey : slice_profiles_info.keySet()) {
                if (sliceKey.endsWith(".java")) {
//                    && !key.contains("/test/")
                    java_slice_profiles_info.put(sliceKey, slice_profiles_info.get(sliceKey));
                } else {
                    cpp_slice_profiles_info.put(sliceKey, slice_profiles_info.get(sliceKey));
                }
            }

            if (mode.equals(TESTING)) {
//            start from cpp slice profiles [testing]
                System.out.println("Beginning test...");
                for (SliceProfilesInfo currentSlice : cpp_slice_profiles_info.values()) {
                    for (SliceProfile profile : currentSlice.slice_profiles.values()) {
                        if (analyzed_profiles.contains(profile)) continue;
                        analyze_slice_profile(profile, cpp_slice_profiles_info);
                    }
                }
            } else {
                for (SliceProfilesInfo currentSlice : java_slice_profiles_info.values()) {
                    for (SliceProfile profile : currentSlice.slice_profiles.values()) {
                        if (analyzed_profiles.contains(profile)) continue;
                        analyze_slice_profile(profile, java_slice_profiles_info);
                    }
                }
            }

            long mid = System.currentTimeMillis();
            System.out.println("Completed building slice profiles in " + (mid - start) / 1000 + "s");
            if (mode.equals(TESTING)) {
                export_graph(DG);
            }
            return print_violations(start);

        } catch (URISyntaxException | IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void export_graph(Graph<EnclNamePosTuple, DefaultEdge> dg) throws IOException {
        System.out.println("Exporting graph...");
        DOTExporter<EnclNamePosTuple, DefaultEdge> exporter = new DOTExporter<>(EnclNamePosTuple::toString);
        StringWriter writer = new StringWriter();
        exporter.exportGraph(dg, writer);
        final File file = new File(FileSystems.getDefault().getPath(".").toString(), "graph.dot");
        FileUtils.writeStringToFile(file, writer.toString(), Charset.defaultCharset());
    }

//    public static void inspectXML(String xmlSource)
//            throws IOException {
//        java.io.FileWriter fw = new java.io.FileWriter("temp.xml");
//        fw.write(xmlSource);
//        fw.close();
//    }

    @SuppressWarnings("unused")
    public static void bfsSolution(EnclNamePosTuple source, List<String> lookup) {
        List<List<EnclNamePosTuple>> completePaths = new ArrayList<>();

        //Run a BFS from the source vertex. Each time a new vertex is encountered, construct a new path.
        BreadthFirstIterator<EnclNamePosTuple, DefaultEdge> bfs = new BreadthFirstIterator<>(DG, source);
        while (bfs.hasNext()) {
            EnclNamePosTuple vertex = bfs.next();
            //Create path P that ends in the vertex by backtracking from the new vertex we encountered
            Stack<EnclNamePosTuple> partialPathP = new Stack<>();
            while (vertex != null) {
                partialPathP.push(vertex);
                vertex = bfs.getParent(vertex);
            }
            List<EnclNamePosTuple> pathP = new ArrayList<>(partialPathP.size());
            while (!partialPathP.isEmpty()) {
                pathP.add(partialPathP.pop());
            }
            completePaths.add(pathP);
        }
        for (List<EnclNamePosTuple> smallPath : completePaths) {
            if (containsAllWords(smallPath.toString(), lookup)) {
                System.out.println(smallPath);
            }
        }
    }

    private static Hashtable<String, Set<List<EnclNamePosTuple>>> print_violations(long start) {
        Hashtable<String, Set<List<EnclNamePosTuple>>> violationsToPrint = new Hashtable<>();
        ArrayList<EnclNamePosTuple> source_nodes = new ArrayList<>();
        for (EnclNamePosTuple node : DG.vertexSet()) {
            if (DG.inDegreeOf(node) == 0) {
                source_nodes.add(node);
            }
        }
        int violations_count = 0;
        for (EnclNamePosTuple source_node : source_nodes) {
            if (mode.skipViolations()) {
                bfsSolution(source_node, mode.lookupString());
                continue;
            }
            Enumeration<EnclNamePosTuple> violationE = detected_violations.keys();
            while (violationE.hasMoreElements()) {
                EnclNamePosTuple violated_node_pos_pair = violationE.nextElement();
                ArrayList<String> violations = detected_violations.get(violated_node_pos_pair);

//                AllDirectedPaths<Encl_name_pos_tuple,DefaultEdge> allDirectedPaths = new AllDirectedPaths<>(DG);
//                List<GraphPath<Encl_name_pos_tuple,DefaultEdge>> requiredPath =
//                        allDirectedPaths.getAllPaths(source_node, violated_node_pos_pair, true, 15);
//
//                BellmanFordShortestPath<Encl_name_pos_tuple, DefaultEdge> bellmanFordShortestPath =
//                        new BellmanFordShortestPath<>(DG);
//                GraphPath<Encl_name_pos_tuple,DefaultEdge> requiredPath =
//                        bellmanFordShortestPath.getPath(source_node, violated_node_pos_pair);

                BFSShortestPath<EnclNamePosTuple, DefaultEdge> bfsShortestPath = new BFSShortestPath<>(DG);
                GraphPath<EnclNamePosTuple, DefaultEdge> requiredPath =
                        bfsShortestPath.getPath(source_node, violated_node_pos_pair);

                if (requiredPath != null) {
                    List<EnclNamePosTuple> vertexList = requiredPath.getVertexList();
                    violations.forEach(violation -> {
                        Set<List<EnclNamePosTuple>> currentArray;
                        if (violationsToPrint.containsKey(violation)) {
                            currentArray = violationsToPrint.get(violation);
                        } else {
                            currentArray = new HashSet<>();
                        }
                        currentArray.add(vertexList);
                        violationsToPrint.put(violation, currentArray);
                    });
                    violations_count = violations_count + violations.size();
                }
            }
        }


        violationsToPrint.forEach((key, violations) -> {
            violations.forEach(violation -> {
                System.err.print("Possible out-of-bounds operation path : ");
                StringBuilder vPath = new StringBuilder();
                int size = violation.size() - 1;
                if (key.startsWith("Buffer")) {
                    size = violation.size();
                }
                for (int i = 0; i < size; i++) {
                    EnclNamePosTuple x = violation.get(i);
                    vPath.append(x).append(" -> ");
                }
                System.err.println(vPath);
            });
            System.err.println(key + "\n");
        });

        System.out.println("No of files analyzed " +
                (java_slice_profiles_info.size() + cpp_slice_profiles_info.size()));
        System.out.println("Detected violations " + violations_count);
        long end = System.currentTimeMillis();
        System.out.println("Completed analysis in " + (end - start) / 1000 + "s");
        return violationsToPrint;
    }

    private static void analyze_slice_profile(SliceProfile profile,
                                              Hashtable<String, SliceProfilesInfo> raw_profiles_info) {
        analyzed_profiles.add(profile);

//      step-01 : analyse cfunctions of the slice variable

        EnclNamePosTuple encl_namePosTuple;
        for (String cfunction_name : profile.cfunctions.keySet()) {
            cFunction cfunction = profile.cfunctions.get(cfunction_name);
            int arg_pos_index = cfunction.getArg_pos_index();
            String cfunction_pos = cfunction.getCfunction_pos();
            String encl_function_name = cfunction.getCurrent_function_name();
            Node encl_function_node = cfunction.getCurrent_function_node();
            encl_namePosTuple = new EnclNamePosTuple(profile.var_name, encl_function_name, profile.file_name,
                    profile.defined_position);
            analyze_cfunction(cfunction_name, cfunction_pos, arg_pos_index, profile.type_name, encl_function_node,
                    encl_namePosTuple, raw_profiles_info);
        }
        encl_namePosTuple = new EnclNamePosTuple(profile.var_name, profile.function_name, profile.file_name,
                profile.defined_position);
        if (!DG.containsVertex(encl_namePosTuple)) {
            DG.addVertex(encl_namePosTuple);
        }
        encl_namePosTuple = new EnclNamePosTuple(profile.var_name, profile.function_name, profile.file_name,
                profile.defined_position);
        if (!DG.containsVertex(encl_namePosTuple)) {
            DG.addVertex(encl_namePosTuple);
        }

//      step-02 : analyze data dependent vars of the slice variable

        for (NamePos dv : profile.dependent_vars) {
            String dvar = dv.getName();
            String dvar_encl_function_name = dv.getType();
            String dvar_pos = dv.getPos();
            Hashtable<String, SliceProfile> source_slice_profiles =
                    raw_profiles_info.get(profile.file_name).slice_profiles;
            String key = dvar + "%" + dvar_pos + "%" + dvar_encl_function_name + "%" + profile.file_name;
            if (!source_slice_profiles.containsKey(key)) {
//              not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvar_slice_profile = source_slice_profiles.get(key);
            EnclNamePosTuple dvar_name_pos_tuple = new EnclNamePosTuple(dvar_slice_profile.var_name,
                    dvar_slice_profile.function_name, dvar_slice_profile.file_name,
                    dvar_slice_profile.defined_position);
            if (has_no_edge(encl_namePosTuple, dvar_name_pos_tuple)) {
                analyze_slice_profile(dvar_slice_profile, raw_profiles_info);
            }
        }

//      step-03 : analyze if given function node is a native method

        if (!profile.function_name.equals("GLOBAL") && profile.cfunctions.size() < 1) {
            Node encl_function_node = profile.function_node;
            if (is_function_of_given_modifier(encl_function_node, jni_native_method_modifier)) {
                analyze_native_function(profile, raw_profiles_info, encl_function_node, encl_namePosTuple);
            }
        }

//      step-04 : check and add buffer reads and writes for this profile

        if (!mode.checkBuffer()) {
            return;
        }

        if (profile.file_name.endsWith(".java")) {
            return;
        }

        for (SliceVariableAccess var_access : profile.used_positions) {
            for (Tuple access : var_access.write_positions) {
                if (DataAccessType.BUFFER_WRITE != access.access_type) {
                    continue;
                }

                ArrayList<String> violations;
                if (detected_violations.containsKey(encl_namePosTuple)) {
                    violations = new ArrayList<>(detected_violations.get(encl_namePosTuple));
                } else {
                    violations = new ArrayList<>();
                }
                violations.add("Buffer write at " + access.access_pos);
                detected_violations.put(encl_namePosTuple, violations);
            }
        }
    }

    private static void analyze_cfunction(String cfunction_name, String cfunction_pos, int arg_pos_index,
                                          String var_type_name, Node encl_function_node,
                                          EnclNamePosTuple encl_namePosTuple, Hashtable<String,
            SliceProfilesInfo> slice_profiles_info) {
        LinkedList<SliceProfile> dependent_slice_profiles = find_dependent_slice_profiles(cfunction_name,
                arg_pos_index, var_type_name, encl_function_node, slice_profiles_info);
        for (SliceProfile dep_profile : dependent_slice_profiles) {
            EnclNamePosTuple dep_name_pos_tuple = new EnclNamePosTuple(dep_profile.var_name,
                    dep_profile.function_name, dep_profile.file_name, dep_profile.defined_position);
            if (!has_no_edge(encl_namePosTuple, dep_name_pos_tuple)) {
                continue;
            }
            if (analyzed_profiles.contains(dep_profile)) {
                continue;
            }
            analyze_slice_profile(dep_profile, slice_profiles_info);
        }

        if (dependent_slice_profiles.size() > 0) {
            return;
        }

        if (buffer_error_functions.contains(cfunction_name)) {
            DG.addVertex(encl_namePosTuple);
            ArrayList<String> cErrors = new ArrayList<>();
            cErrors.add("Use of " + cfunction_name + " at " + cfunction_pos);
            EnclNamePosTuple bufferErrorFunctionPosTuple =
                    new EnclNamePosTuple(encl_namePosTuple.varName() + "#" + cfunction_name,
                            encl_namePosTuple.functionName(), encl_namePosTuple.fileName(), cfunction_pos);
            has_no_edge(encl_namePosTuple, bufferErrorFunctionPosTuple);
            detected_violations.put(bufferErrorFunctionPosTuple, cErrors);
//            detected_violations.put(encl_name_pos_tuple, cErrors);
        }
    }

    @SuppressWarnings("unused")
    private static LinkedList<SliceProfile> find_dependent_slice_profiles(String cfunction_name,
                                                                          int arg_pos_index, String type_name,
                                                                          Node current_function_node,
                                                                          Hashtable<String, SliceProfilesInfo> sliceProfileInfo) {
        LinkedList<SliceProfile> dependent_slice_profiles = new LinkedList<>();
        for (String file_path : sliceProfileInfo.keySet()) {
            SliceProfilesInfo profile_info = sliceProfileInfo.get(file_path);
            for (cFunction cfunction : find_possible_functions(profile_info.function_nodes, cfunction_name,
                    arg_pos_index, current_function_node)) {
                NamePos param = cfunction.getFunc_args().get(arg_pos_index - 1);
                String param_name = param.getName();
                String param_pos = param.getPos();
                String key = param_name + "%" + param_pos + "%" + cfunction_name + "%" + file_path;
                if (!profile_info.slice_profiles.containsKey(key)) {
                    continue;
                }
                dependent_slice_profiles.add(profile_info.slice_profiles.get(key));
            }
        }
        return dependent_slice_profiles;
    }


    private static void analyze_native_function(SliceProfile profile,
                                                Hashtable<String, SliceProfilesInfo> profiles_info,
                                                Node encl_function_node, EnclNamePosTuple encl_namePosTuple) {
        Node encl_unit_node = profiles_info.get(profile.file_name).unit_node;
        String jni_function_name = profile.function_name;
        if (jni_function_name.length() > 2 && jni_function_name.startsWith("n")
                && Character.isUpperCase(jni_function_name.charAt(1))) {
            jni_function_name = jni_function_name.substring(1);
        }
        String jni_arg_name = profile.var_name;
        ArrayList<ArgumentNamePos> params = find_function_parameters(encl_function_node);
        int index = 0;
        for (NamePos par : params) {
            if (par.getName().equals(jni_arg_name)) break;
            index++;
        }
        int jni_arg_pos_index = index + 2;
        String clazz_name = getNodeByName(getNodeByName(encl_unit_node, "class").get(0), "name").get(0).
                getTextContent();
        String jni_function_search_str = clazz_name + "_" + jni_function_name;

        for (String file_path : cpp_slice_profiles_info.keySet()) {
            SliceProfilesInfo profile_info = cpp_slice_profiles_info.get(file_path);

            for (NamePos func : profile_info.function_nodes.keySet()) {
                Node function_node = profile_info.function_nodes.get(func);
                String function_name = func.getName();
                if (!function_name.toLowerCase().endsWith(jni_function_search_str.toLowerCase())) {
                    continue;
                }
                ArrayList<ArgumentNamePos> function_args = find_function_parameters(function_node);
                if (function_args.size() < 1 || jni_arg_pos_index > function_args.size() - 1) {
                    continue;
                }
                NamePos arg = function_args.get(jni_arg_pos_index);
                String key = arg.getName() + "%" + arg.getPos() + "%" + function_name + "%" + file_path;
                SliceProfile possible_slice_profile = null;

                for (String cpp_profile_id : profile_info.slice_profiles.keySet()) {
                    SliceProfile cpp_profile = profile_info.slice_profiles.get(cpp_profile_id);
                    if (cpp_profile_id.equals(key)) {
                        possible_slice_profile = cpp_profile;
                        break;
                    }
                }
                if (possible_slice_profile == null) continue;
                EnclNamePosTuple analyzed_name_pos_tuple = new EnclNamePosTuple(possible_slice_profile.var_name,
                        possible_slice_profile.function_name, possible_slice_profile.file_name,
                        possible_slice_profile.defined_position);
                if (!has_no_edge(encl_namePosTuple, analyzed_name_pos_tuple)) {
                    continue;
                }
                if (analyzed_profiles.contains(possible_slice_profile)) {
                    continue;
                }
                analyze_slice_profile(possible_slice_profile, cpp_slice_profiles_info);
            }
        }
    }

    private static boolean is_function_of_given_modifier(Node encl_function_node, String jni_native_method_modifier) {
        List<Node> specifiers = getNodeByName(encl_function_node, "specifier");
        for (Node specifier : specifiers) {
            String nodeName = specifier.getTextContent();
            if (jni_native_method_modifier.equals(nodeName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean has_no_edge(EnclNamePosTuple source_name_pos_tuple,
                                       EnclNamePosTuple target_name_pos_tuple) {
        if (source_name_pos_tuple.equals(target_name_pos_tuple)) {
            return false;
        }
        if (!DG.containsVertex(source_name_pos_tuple)) {
            DG.addVertex(source_name_pos_tuple);
        }
        if (!DG.containsVertex(target_name_pos_tuple)) {
            DG.addVertex(target_name_pos_tuple);
        }

        if (DG.containsEdge(source_name_pos_tuple, target_name_pos_tuple)) {
            return false;
        }

        DG.addEdge(source_name_pos_tuple, target_name_pos_tuple);
        return true;
    }

    private static LinkedList<cFunction> find_possible_functions(Hashtable<NamePos, Node> function_nodes,
                                                                 String cfunction_name, int arg_pos_index,
                                                                 Node encl_function_node) {
        LinkedList<cFunction> possible_functions = new LinkedList<>();

        if (encl_function_node == null) {
            return possible_functions;
        }

        for (NamePos key : function_nodes.keySet()) {
            Node possible_function_node = function_nodes.get(key);
            String function_name = key.getName();
            if (!function_name.equals(cfunction_name)) {
                continue;
            }

            ArrayList<ArgumentNamePos> func_args = find_function_parameters(possible_function_node);
            if (func_args.size() == 0 || arg_pos_index > func_args.size()) {
                continue;
            }

            int arg_index = arg_pos_index - 1;
            String param_name = func_args.get(arg_index).getName();
            if (param_name.equals("")) {
                continue;
            }

            if (!validate_function_against_call_expr(encl_function_node, cfunction_name, arg_index, func_args)) {
                continue;
            }

            possible_functions.add(new cFunction(arg_index, function_name, "", encl_function_node,
                    func_args));
        }
        return possible_functions;
    }

    @SuppressWarnings("unused")
    private static boolean validate_function_against_call_expr(Node encl_function_node, String cfunction_name,
                                                               int arg_index, ArrayList<ArgumentNamePos> func_args) {
        List<Node> call_argument_list;
        for (Node call : getNodeByName(encl_function_node, "call", true)) {
            String function_name = getNamePosTextPair(call).getName();
            if (!cfunction_name.equals(function_name)) {
                continue;
            }
            call_argument_list = getNodeByName(getNodeByName(call, "argument_list").get(0), "argument");
            if (call_argument_list.size() != func_args.size()) {
                int size_without_optional_args = (int) func_args.stream().filter(arg -> !arg.isOptional()).count();
                if (call_argument_list.size() != size_without_optional_args) {
                    continue;
                }
            }
            return true;
        }

        for (Node decl : getNodeByName(encl_function_node, "decl", true)) {
            Node init = nodeAtIndex(getNodeByName(decl, "init"), 0);
            if (init != null) {
                continue;
            }

            String constructorTypeName = getNamePosTextPair(decl).getType();
            if (!cfunction_name.equals(constructorTypeName)) {
                continue;
            }

            Node argument_list = nodeAtIndex(getNodeByName(decl, "argument_list"), 0);
            if (argument_list == null) {
                continue;
            }

            call_argument_list = getNodeByName(argument_list, "argument");
            if (call_argument_list.size() != func_args.size()) {
                continue;
            }
            return true;
        }

        return false;
    }

    private static void analyze_source_unit_and_build_slices(Node unit_node, String source_file_path,
                                                             Hashtable<String, SliceProfile> slice_profiles) {
        SliceGenerator slice_generator = new SliceGenerator(unit_node, source_file_path, slice_profiles);
        slice_generator.generate();
    }

    private static Hashtable<NamePos, Node> find_function_nodes(Node unit_node) {
        Hashtable<NamePos, Node> function_nodes = new Hashtable<>();
        List<Node> fun1 = getNodeByName(unit_node, "function", true);
        List<Node> fun2 = getNodeByName(unit_node, "function_decl", true);
        List<Node> fun3 = getNodeByName(unit_node, "constructor", true);
        List<Node> fun4 = getNodeByName(unit_node, "destructor", true);

        List<Node> funList = Stream.of(fun1, fun2, fun3, fun4)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        for (Node node : funList) {
            function_nodes.put(getNamePosTextPair(node), node);
        }
        return function_nodes;
    }

}