package ca.uwaterloo.swag;

import ca.uwaterloo.swag.models.*;
import ca.uwaterloo.swag.util.XmlUtil;
import ca.uwaterloo.swag.util.OsUtils;
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

public class Main {

    private static final List<String> BUFFER_ERROR_FUNCTIONS = Arrays.asList("strcat", "strdup", "strncat", "strcmp",
            "strncmp", "strcpy", "strncpy", "strlen", "strchr", "strrchr", "index", "rindex", "strpbrk", "strspn",
            "strcspn", "strstr", "strtok", "memccpy", "memchr", "memmove", "memcpy", "memcmp", "memset", "bcopy",
            "bzero", "bcmp");
    private static final String JNI_NATIVE_METHOD_MODIFIER = "native";
    private static final Hashtable<String, SliceProfilesInfo> sliceProfilesInfo = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> javaSliceProfilesInfo = new Hashtable<>();
    private static final Hashtable<String, SliceProfilesInfo> cppSliceProfilesInfo = new Hashtable<>();
    private static final Graph<EnclNamePosTuple, DefaultEdge> DG = new DefaultDirectedGraph<>(DefaultEdge.class);
    private static final Hashtable<EnclNamePosTuple, ArrayList<String>> detectedViolations = new Hashtable<>();
    private static final String JAR = "jar";
    private static final MODE mode = MODE.NON_TESTING;

    private static final LinkedList<SliceProfile> analyzedProfiles = new LinkedList<>();

    public static void main(String[] args) {
        nonCLI(args);
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
                    Path zipPath = Paths.get(Objects.requireNonNull(Main.class.getClassLoader().
                            getResource(srcML)).toURI());
                    InputStream in = Files.newInputStream(zipPath);
                    //noinspection ConstantConditions
                    file = File.createTempFile("PREFIX", "SUFFIX", tempLoc);
                    boolean execStatus = file.setExecutable(true);
                    if (!execStatus) throw new AssertionError();
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
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(new InputSource(new StringReader(result)));
            for (Node unitNode : XmlUtil.asList(document.getElementsByTagName("unit"))) {
                Node fileName = unitNode.getAttributes().getNamedItem("filename");
                if (fileName != null) {
                    String sourceFilePath = fileName.getNodeValue();
                    if (unitNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    SliceProfilesInfo profilesInfo = analyzeSourceUnitAndBuildSlices(unitNode, sourceFilePath);
                    sliceProfilesInfo.put(sourceFilePath, profilesInfo);
                }
            }

            for (String sliceKey : sliceProfilesInfo.keySet()) {
                if (sliceKey.endsWith(".java")) {
//                    && !key.contains("/test/")
                    javaSliceProfilesInfo.put(sliceKey, sliceProfilesInfo.get(sliceKey));
                } else {
                    cppSliceProfilesInfo.put(sliceKey, sliceProfilesInfo.get(sliceKey));
                }
            }

            if (mode.startFromCpp()) {
//            start from cpp slice profiles
                System.out.println("Beginning test...");
                for (SliceProfilesInfo currentSlice : cppSliceProfilesInfo.values()) {
                    for (SliceProfile profile : currentSlice.sliceProfiles.values()) {
                        if (analyzedProfiles.contains(profile)) {
                            continue;
                        }
                        analyzeSliceProfile(profile, cppSliceProfilesInfo);
                    }
                }
            } else {
                for (SliceProfilesInfo currentSlice : javaSliceProfilesInfo.values()) {
                    for (SliceProfile profile : currentSlice.sliceProfiles.values()) {
                        if (analyzedProfiles.contains(profile)) {
                            continue;
                        }
                        analyzeSliceProfile(profile, javaSliceProfilesInfo);
                    }
                }
            }

            long mid = System.currentTimeMillis();
            System.out.println("Completed building slice profiles in " + (mid - start) / 1000 + "s");
            if (mode.exportGraph()) {
                exportGraph(DG);
            }
            return printViolations(start);

        } catch (URISyntaxException | IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void exportGraph(Graph<EnclNamePosTuple, DefaultEdge> graph) throws IOException {
        System.out.println("Exporting graph...");
        DOTExporter<EnclNamePosTuple, DefaultEdge> exporter = new DOTExporter<>(EnclNamePosTuple::toString);
        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
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

    public static boolean containsAllWords(String word, List<String> keywords) {
        for (String k : keywords) {
            if (!word.contains(k)) {
                return false;
            }
        }
        return true;
    }

    private static Hashtable<String, Set<List<EnclNamePosTuple>>> printViolations(long start) {
        Hashtable<String, Set<List<EnclNamePosTuple>>> violationsToPrint = new Hashtable<>();
        ArrayList<EnclNamePosTuple> sourceNodes = new ArrayList<>();
        for (EnclNamePosTuple node : DG.vertexSet()) {
            if (DG.inDegreeOf(node) == 0 && node.fileName().endsWith(".java")) {
                sourceNodes.add(node);
            }
        }
        int violationsCount = 0;
        for (EnclNamePosTuple sourceNode : sourceNodes) {
            if (mode.skipViolations()) {
                bfsSolution(sourceNode, mode.lookupString());
                continue;
            }

            for (EnclNamePosTuple violatedNodePos : detectedViolations.keySet()) {
                ArrayList<String> violations = detectedViolations.get(violatedNodePos);

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
                        bfsShortestPath.getPath(sourceNode, violatedNodePos);

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
                    violationsCount = violationsCount + violations.size();
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
                (javaSliceProfilesInfo.size() + cppSliceProfilesInfo.size()));
        System.out.println("Detected violations " + violationsCount);
        long end = System.currentTimeMillis();
        System.out.println("Completed analysis in " + (end - start) / 1000 + "s");
        return violationsToPrint;
    }

    private static void analyzeSliceProfile(SliceProfile profile,
                                            Hashtable<String, SliceProfilesInfo> rawProfilesInfo) {
        analyzedProfiles.add(profile);

//      step-01 : analyse cfunctions of the slice variable

        EnclNamePosTuple enclNamePosTuple;
        for (CFunction cfunction : profile.cfunctions) {
            String cfunctionName = cfunction.getName();
            int argPosIndex = cfunction.getArgPosIndex();
            String cfunctionPos = cfunction.getPosition();
            String enclFunctionName = cfunction.getEnclFunctionName();
            Node enclFunctionNode = cfunction.getEnclFunctionNode();
            enclNamePosTuple = new EnclNamePosTuple(profile.varName, enclFunctionName, profile.fileName,
                    profile.definedPosition);
            analyzeCfunction(cfunctionName, cfunctionPos, argPosIndex, profile.typeName, enclFunctionNode,
                    enclNamePosTuple, rawProfilesInfo);
        }
        enclNamePosTuple = new EnclNamePosTuple(profile.varName, profile.functionName, profile.fileName,
                profile.definedPosition);
        if (!DG.containsVertex(enclNamePosTuple)) {
            DG.addVertex(enclNamePosTuple);
        }

//      step-02 : analyze data dependent vars of the slice variable

        for (NamePos dependentVar : profile.dependentVars) {
            String dvarName = dependentVar.getName();
            String dvarEnclFunctionName = dependentVar.getType();
            String dvarPos = dependentVar.getPos();
            Hashtable<String, SliceProfile> sourceSliceProfiles =
                    rawProfilesInfo.get(profile.fileName).sliceProfiles;
            String sliceKey = dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
            if (!sourceSliceProfiles.containsKey(sliceKey)) {
//              not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
            EnclNamePosTuple dvarNamePosTuple = new EnclNamePosTuple(dvarSliceProfile.varName,
                    dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                    dvarSliceProfile.definedPosition);
            if (hasNoEdge(enclNamePosTuple, dvarNamePosTuple)) {
                analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
            }
        }

//      step-03 : analyze if given function node is a native method

        if (!profile.functionName.equals("GLOBAL") && profile.cfunctions.size() < 1) {
            Node enclFunctionNode = profile.functionNode;
            if (isFunctionOfGivenModifier(enclFunctionNode, JNI_NATIVE_METHOD_MODIFIER)) {
                analyzeNativeFunction(profile, rawProfilesInfo, enclFunctionNode, enclNamePosTuple);
            }
        }

//      step-04 : check and add buffer reads and writes for this profile

        if (!mode.checkBuffer()) {
            return;
        }

        if (profile.fileName.endsWith(".java")) {
            return;
        }

        for (SliceVariableAccess varAccess : profile.usedPositions) {
            for (DataTuple access : varAccess.writePositions) {
                if (XmlUtil.DataAccessType.BUFFER_WRITE != access.accessType) {
                    continue;
                }

                ArrayList<String> violations;
                if (detectedViolations.containsKey(enclNamePosTuple)) {
                    violations = new ArrayList<>(detectedViolations.get(enclNamePosTuple));
                } else {
                    violations = new ArrayList<>();
                }
                violations.add("Buffer write at " + access.accessPos);
                detectedViolations.put(enclNamePosTuple, violations);
            }
        }
    }

    private static void analyzeCfunction(String cfunctionName, String cfunctionPos, int argPosIndex,
                                         String varTypeName, Node enclFunctionNode,
                                         EnclNamePosTuple enclNamePosTuple, Hashtable<String,
            SliceProfilesInfo> sliceProfilesInfo) {
        LinkedList<SliceProfile> dependentSliceProfiles = findDependentSliceProfiles(cfunctionName,
                argPosIndex, varTypeName, enclFunctionNode, sliceProfilesInfo);
        for (SliceProfile dep_profile : dependentSliceProfiles) {
            EnclNamePosTuple depNamePosTuple = new EnclNamePosTuple(dep_profile.varName,
                    dep_profile.functionName, dep_profile.fileName, dep_profile.definedPosition);
            if (!hasNoEdge(enclNamePosTuple, depNamePosTuple)) {
                continue;
            }
            if (analyzedProfiles.contains(dep_profile)) {
                continue;
            }
            analyzeSliceProfile(dep_profile, sliceProfilesInfo);
        }

        if (dependentSliceProfiles.size() > 0) {
            return;
        }

        if (BUFFER_ERROR_FUNCTIONS.contains(cfunctionName)) {
            DG.addVertex(enclNamePosTuple);
            ArrayList<String> cErrors = new ArrayList<>();
            cErrors.add("Use of " + cfunctionName + " at " + cfunctionPos);
            EnclNamePosTuple bufferErrorFunctionPosTuple =
                    new EnclNamePosTuple(enclNamePosTuple.varName() + "#" + cfunctionName,
                            enclNamePosTuple.functionName(), enclNamePosTuple.fileName(), cfunctionPos);
            hasNoEdge(enclNamePosTuple, bufferErrorFunctionPosTuple);
            detectedViolations.put(bufferErrorFunctionPosTuple, cErrors);
        }
    }

    @SuppressWarnings("unused")
    private static LinkedList<SliceProfile> findDependentSliceProfiles(String cfunctionName,
                                                                       int argPosIndex, String typeName,
                                                                       Node currentFunctionNode,
                                                                       Hashtable<String, SliceProfilesInfo> sliceProfileInfo) {
        LinkedList<SliceProfile> dependentSliceProfiles = new LinkedList<>();
        for (String filePath : sliceProfileInfo.keySet()) {
            SliceProfilesInfo profileInfo = sliceProfileInfo.get(filePath);
            for (CFunction cfunction : findPossibleFunctions(profileInfo.functionNodes, profileInfo.functionDeclMap,
                    cfunctionName, argPosIndex, currentFunctionNode)) {
                NamePos param = cfunction.getFuncArgs().get(argPosIndex - 1);
                String param_name = param.getName();
                String param_pos = param.getPos();
                String key = param_name + "%" + param_pos + "%" + cfunction.getEnclFunctionName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));
            }
        }
        return dependentSliceProfiles;
    }


    private static void analyzeNativeFunction(SliceProfile profile,
                                              Hashtable<String, SliceProfilesInfo> profilesInfo,
                                              Node enclFunctionNode, EnclNamePosTuple enclNamePosTuple) {
        Node enclUnitNode = profilesInfo.get(profile.fileName).unitNode;
        String jniFunctionName = profile.functionName;
        if (jniFunctionName.length() > 2 && jniFunctionName.startsWith("n")
                && Character.isUpperCase(jniFunctionName.charAt(1))) {
            jniFunctionName = jniFunctionName.substring(1);
        }
        String jniArgName = profile.varName;
        ArrayList<ArgumentNamePos> params = XmlUtil.findFunctionParameters(enclFunctionNode);
        int index = 0;
        for (NamePos par : params) {
            if (par.getName().equals(jniArgName)) break;
            index++;
        }
        int jniArgPosIndex = index + 2;
        String clazzName = XmlUtil.getNodeByName(XmlUtil.getNodeByName(enclUnitNode, "class").get(0), "name").get(0).
                getTextContent();
        String jniFunctionSearchStr = clazzName + "_" + jniFunctionName;

        for (String filePath : cppSliceProfilesInfo.keySet()) {
            SliceProfilesInfo profileInfo = cppSliceProfilesInfo.get(filePath);

            for (FunctionNamePos funcNamePos : profileInfo.functionNodes.keySet()) {
                Node functionNode = profileInfo.functionNodes.get(funcNamePos);
                String functionName = funcNamePos.getName();
                if (!functionName.toLowerCase().endsWith(jniFunctionSearchStr.toLowerCase())) {
                    continue;
                }
                ArrayList<ArgumentNamePos> functionArgs = XmlUtil.findFunctionParameters(functionNode);
                if (functionArgs.size() < 1 || jniArgPosIndex > functionArgs.size() - 1) {
                    continue;
                }
                NamePos arg = functionArgs.get(jniArgPosIndex);
                String sliceKey = arg.getName() + "%" + arg.getPos() + "%" + functionName + "%" + filePath;

                SliceProfile possibleSliceProfile = null;

                for (String cppProfileId : profileInfo.sliceProfiles.keySet()) {
                    SliceProfile cppProfile = profileInfo.sliceProfiles.get(cppProfileId);
                    if (cppProfileId.equals(sliceKey)) {
                        possibleSliceProfile = cppProfile;
                        break;
                    }
                }
                if (possibleSliceProfile == null) {
                    continue;
                }
                EnclNamePosTuple analyzedNamePosTuple = new EnclNamePosTuple(possibleSliceProfile.varName,
                        possibleSliceProfile.functionName, possibleSliceProfile.fileName,
                        possibleSliceProfile.definedPosition);
                if (!hasNoEdge(enclNamePosTuple, analyzedNamePosTuple)) {
                    continue;
                }
                if (analyzedProfiles.contains(possibleSliceProfile)) {
                    continue;
                }
                analyzeSliceProfile(possibleSliceProfile, cppSliceProfilesInfo);
            }
        }
    }

    private static boolean isFunctionOfGivenModifier(Node enclFunctionNode, String accessModifier) {
        List<Node> specifiers = XmlUtil.getNodeByName(enclFunctionNode, "specifier");
        for (Node specifier : specifiers) {
            String nodeName = specifier.getTextContent();
            if (accessModifier.equals(nodeName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNoEdge(EnclNamePosTuple sourceNamePosTuple,
                                     EnclNamePosTuple targetNamePosTuple) {
        if (sourceNamePosTuple.equals(targetNamePosTuple)) {
            return false;
        }
        if (!DG.containsVertex(sourceNamePosTuple)) {
            DG.addVertex(sourceNamePosTuple);
        }
        if (!DG.containsVertex(targetNamePosTuple)) {
            DG.addVertex(targetNamePosTuple);
        }

        if (DG.containsEdge(sourceNamePosTuple, targetNamePosTuple)) {
            return false;
        }

        DG.addEdge(sourceNamePosTuple, targetNamePosTuple);
        return true;
    }

    private static LinkedList<CFunction> findPossibleFunctions(Hashtable<FunctionNamePos, Node> functionNodes,
                                                               Hashtable<String, List<FunctionNamePos>> functionDeclMap,
                                                               String cfunctionName, int argPosIndex,
                                                               Node enclFunctionNode) {
        LinkedList<CFunction> possibleFunctions = new LinkedList<>();

        if (enclFunctionNode == null) {
            return possibleFunctions;
        }

        List<String> cFunctionsWithAlias = new ArrayList<>();
        cFunctionsWithAlias.add(cfunctionName);

        if (functionDeclMap.containsKey(cfunctionName)) {
            cFunctionsWithAlias.addAll(functionDeclMap.get(cfunctionName).
                    stream().
                    map(FunctionNamePos::getFunctionDeclName).
                    collect(Collectors.toList()));
        }

        for (String cFunc : cFunctionsWithAlias) {
            for (FunctionNamePos key : functionNodes.keySet()) {
                Node possibleFunctionNode = functionNodes.get(key);
                String functionName = key.getName();
                if (!functionName.equals(cFunc)) {
                    continue;
                }

                ArrayList<ArgumentNamePos> funcArgs = XmlUtil.findFunctionParameters(possibleFunctionNode);
                if (funcArgs.size() == 0 || argPosIndex > funcArgs.size()) {
                    continue;
                }

                int argIndex = argPosIndex - 1;
                String paramName = funcArgs.get(argIndex).getName();
                if (paramName.equals("")) {
                    continue;
                }

                if (!validateFunctionAgainstCallExpr(enclFunctionNode, cfunctionName, argIndex, funcArgs)) {
                    continue;
                }

                possibleFunctions.add(new CFunction(cfunctionName, "", argIndex, functionName, enclFunctionNode,
                        funcArgs));
            }
        }
        return possibleFunctions;
    }

    @SuppressWarnings("unused")
    private static boolean validateFunctionAgainstCallExpr(Node enclFunctionNode, String cfunctionName,
                                                           int argIndex, ArrayList<ArgumentNamePos> funcArgs) {
        List<Node> callArgumentList;
        for (Node call : XmlUtil.getNodeByName(enclFunctionNode, "call", true)) {
            String functionName = XmlUtil.getNamePosTextPair(call).getName();
            if (!cfunctionName.equals(functionName)) {
                continue;
            }
            callArgumentList = XmlUtil.getNodeByName(XmlUtil.getNodeByName(call, "argument_list").get(0), "argument");
            if (callArgumentList.size() > funcArgs.size()) {
                continue;
//                int sizeWithoutOptionalArgs = (int) funcArgs.stream().filter(arg -> !arg.isOptional()).count();
//                if (callArgumentList.size() != sizeWithoutOptionalArgs) {
//                    continue;
//                }
            }
            return true;
        }

        for (Node decl : XmlUtil.getNodeByName(enclFunctionNode, "decl", true)) {
            Node init = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(decl, "init"), 0);
            if (init != null) {
                continue;
            }

            String constructorTypeName = XmlUtil.getNamePosTextPair(decl).getType();
            if (!cfunctionName.equals(constructorTypeName)) {
                continue;
            }

            Node argumentList = XmlUtil.nodeAtIndex(XmlUtil.getNodeByName(decl, "argument_list"), 0);
            if (argumentList == null) {
                continue;
            }

            callArgumentList = XmlUtil.getNodeByName(argumentList, "argument");
            if (callArgumentList.size() != funcArgs.size()) {
                continue;
            }
            return true;
        }

        return false;
    }

    private static SliceProfilesInfo analyzeSourceUnitAndBuildSlices(Node unitNode, String sourceFilePath) {
        SliceGenerator sliceGenerator = new SliceGenerator(unitNode, sourceFilePath);
        return sliceGenerator.generate();
    }

}