package ca.uwaterloo.swag;

import ca.uwaterloo.swag.models.ArgumentNamePos;
import ca.uwaterloo.swag.models.CFunction;
import ca.uwaterloo.swag.models.DataTuple;
import ca.uwaterloo.swag.models.EnclNamePosTuple;
import ca.uwaterloo.swag.models.FunctionNamePos;
import ca.uwaterloo.swag.models.NamePos;
import ca.uwaterloo.swag.models.SliceProfile;
import ca.uwaterloo.swag.models.SliceProfilesInfo;
import ca.uwaterloo.swag.models.SliceVariableAccess;
import ca.uwaterloo.swag.models.TypeSymbol;
import ca.uwaterloo.swag.util.OsUtils;
import ca.uwaterloo.swag.util.TypeChecker;
import ca.uwaterloo.swag.util.XmlUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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

public class Main {

    private static List<String> SINK_FUNCTIONS = Arrays.asList("strcat", "strdup", "strncat", "strcmp",
        "strncmp", "strcpy", "strncpy", "strlen", "strchr", "strrchr", "index", "rindex", "strpbrk", "strspn",
        "strcspn", "strstr", "strtok", "memccpy", "memchr", "memmove", "memcpy", "memcmp", "memset", "bcopy",
        "bzero", "bcmp");
    private static final String JNI_NATIVE_METHOD_MODIFIER = "native";
    private static final Map<String, SliceProfilesInfo> sliceProfilesInfo = new Hashtable<>();
    private static final Map<String, SliceProfilesInfo> javaSliceProfilesInfo = new Hashtable<>();
    private static final Map<String, SliceProfilesInfo> cppSliceProfilesInfo = new Hashtable<>();
    private static final Graph<EnclNamePosTuple, DefaultEdge> DG = new DefaultDirectedGraph<>(DefaultEdge.class);
    private static final Map<EnclNamePosTuple, ArrayList<String>> detectedViolations = new Hashtable<>();
    private static final String JAR = "jar";
    private static final Set<SliceProfile> analyzedProfiles = new HashSet<>();
    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static String[] singleTarget;
    private static MODE mode = MODE.NON_TESTING;

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
        String functionsFile;

        List<String> argsList = new ArrayList<>();
        Map<String, String> optsList = new HashMap<>();
        List<String> doubleOptsList = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].charAt(0) == '-') {
                if (args[i].length() < 2) {
                    throw new IllegalArgumentException("Not a valid argument: " + args[i]);
                }
                if (args[i].charAt(1) == '-') {
                    if (args[i].length() < 3) {
                        throw new IllegalArgumentException("Not a valid argument: " + args[i]);
                    }
                    // --opt
                    doubleOptsList.add(args[i].substring(2));
                } else {
                    if (args.length - 1 == i) {
                        throw new IllegalArgumentException("Expected arg after: " + args[i]);
                    }
                    // -opt
                    optsList.put(args[i], args[i + 1]);
                    i++;
                }
            } else { // arg
                argsList.add(args[i]);
            }
        }
        try {
            if (doubleOptsList.size() > 0 && doubleOptsList.contains("debug")) {
                mode = MODE.TESTING;
            }
            if (Files.exists(Path.of("skip.txt")) && mode.skipSrcml()) {
                result = Files.readString(Path.of("skip.txt"), StandardCharsets.UTF_8);
            } else {
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
                if (argsList.size() > 0) {
                    projectLocation = args[0];
                    if (optsList.containsKey("-srcml")) {
                        srcML = optsList.get("-srcml");
                    } else {
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
                    }
                    if (optsList.containsKey("-functions")) {
                        functionsFile = optsList.get("-functions");
                        Path functionFilePath = Path.of(functionsFile);
                        if (Files.exists(functionFilePath)) {
                            try (Scanner sc = new Scanner(functionFilePath.toFile(), StandardCharsets.UTF_8)) {
                                SINK_FUNCTIONS = new ArrayList<>();
                                while (sc.hasNextLine()) {
                                    SINK_FUNCTIONS.add(sc.nextLine());
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (optsList.containsKey("-node")) {
                        singleTarget = optsList.get("-node").split("@AT@");
                    } else {
                        singleTarget = null;
                    }
                } else {
                    System.err.println("Please specify location of project to be analysed");
                    System.exit(1);
                }
            }
            if (!mode.skipSrcml() || result == null) {
                ProcessBuilder pb;
                if (argsList.size() > 1) {
                    pb = new ProcessBuilder(srcML, projectLocation, "--position");
                } else {
                    Path zipPath = Paths.get(Objects.requireNonNull(Main.class.getClassLoader().
                        getResource(srcML)).toURI());
                    InputStream in = Files.newInputStream(zipPath);
                    file = File.createTempFile("PREFIX", "SUFFIX", tempLoc);
                    boolean execStatus = file.setExecutable(true);
                    if (!execStatus) {
                        throw new AssertionError();
                    }
                    file.deleteOnExit();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        IOUtils.copy(in, out);
                    }
                    pb = new ProcessBuilder(file.getAbsolutePath(), projectLocation, "--position");
                }
                result = IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
            }
            System.out.println("Converted to XML, beginning parsing ...");
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(new InputSource(new StringReader(result)));

            Set<TypeSymbol> typeSymbols = new HashSet<>();
            for (Node unitNode : XmlUtil.asList(document.getElementsByTagName("unit"))) {
                Node fileName = unitNode.getAttributes().getNamedItem("filename");
                if (fileName != null) {
                    String sourceFilePath = fileName.getNodeValue();
                    if (unitNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    SymbolEnter symbolEnter = new SymbolEnter(unitNode, sourceFilePath, typeSymbols);
                    symbolEnter.invoke();
                }
            }

            for (Node unitNode : XmlUtil.asList(document.getElementsByTagName("unit"))) {
                Node fileName = unitNode.getAttributes().getNamedItem("filename");
                if (fileName != null) {
                    String sourceFilePath = fileName.getNodeValue();
                    if (unitNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    SliceProfilesInfo profilesInfo = analyzeSourceUnitAndBuildSlices(unitNode, sourceFilePath,
                        typeSymbols);
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
                        if (isAnalyzedProfile(profile)) {
                            continue;
                        }
                        analyzeSliceProfile(profile, cppSliceProfilesInfo);
                    }
                }
            } else {
                for (SliceProfilesInfo currentSlice : javaSliceProfilesInfo.values()) {
                    for (SliceProfile profile : currentSlice.sliceProfiles.values()) {
                        if (isAnalyzedProfile(profile)) {
                            continue;
                        }
//                        log.log(Level.INFO, "Source Prof -> " + profile.varName + "%" + profile.functionName +
//                            "%" + profile.fileName + "%" + profile.definedPosition);
                        analyzeSliceProfile(profile, javaSliceProfilesInfo);
                    }
                }
            }

            long mid = System.currentTimeMillis();
            System.out.println("Completed analyzing slice profiles in " + (mid - start) / 1000 + "s");
            if (mode.exportGraph()) {
                exportGraph(DG);
            }
            return printViolations(start);

        } catch (URISyntaxException | IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isAnalyzedProfile(SliceProfile profile) {
//        log.log(Level.INFO, "Profile '" + profile.toString() + "'" + " analyzed : " + contains);
        return analyzedProfiles.contains(profile);
    }

    private static void exportGraph(Graph<EnclNamePosTuple, DefaultEdge> graph) throws IOException {
        System.out.println("Exporting graph...");
        DOTExporter<EnclNamePosTuple, DefaultEdge> exporter = new DOTExporter<>(
            EnclNamePosTuple::toString);
        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        final File file = new File(FileSystems.getDefault().getPath(".").toString(), "graph.dot");
        FileUtils.writeStringToFile(file, writer.toString(), Charset.defaultCharset());
    }

    public static void bfsSolution(EnclNamePosTuple source, List<String> lookup) {
        List<List<EnclNamePosTuple>> completePaths = new ArrayList<>();

        // Run a BFS from the source vertex. Each time a new vertex is encountered, construct a new path.
        BreadthFirstIterator<EnclNamePosTuple, DefaultEdge> bfs = new BreadthFirstIterator<>(DG, source);
        while (bfs.hasNext()) {
            EnclNamePosTuple vertex = bfs.next();
            // Create path P that ends in the vertex by backtracking from the new vertex we encountered
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
        for (String keyword : keywords) {
            if (!word.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private static Hashtable<String, Set<List<EnclNamePosTuple>>> printViolations(long start) {
        BFSShortestPath<EnclNamePosTuple, DefaultEdge> bfsShortestPath = new BFSShortestPath<>(DG);
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
            if (singleTarget != null) {
                Optional<EnclNamePosTuple> actualTarget = DG.vertexSet()
                    .stream()
                    .filter(enclNamePosTuple -> enclNamePosTuple.fileName().equals(singleTarget[1]) &&
                        enclNamePosTuple.varName().equals(singleTarget[0]) &&
                        enclNamePosTuple.definedPosition().equals(singleTarget[2]))
                    .findFirst();
                actualTarget.ifPresent(enclNamePosTuple -> detectedViolations.put(enclNamePosTuple,
                    new ArrayList<>(Collections.singletonList(String.join("@AT@", singleTarget)))));
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

        for (Entry<String, Set<List<EnclNamePosTuple>>> entry : violationsToPrint.entrySet()) {
            String key = entry.getKey();
            Set<List<EnclNamePosTuple>> violations = entry.getValue();
            violations.forEach(violation -> {
                System.err.print("Possible out-of-bounds operation path : ");
                StringBuilder vPath = new StringBuilder();
                int size = violation.size() - 1;
                if (key.startsWith("Buffer")) {
                    size = violation.size();
                }
                for (int i = 0; i < size; i++) {
                    EnclNamePosTuple node = violation.get(i);
                    if (MODE.TESTING == mode && node.isFunctionNamePos()) {
                        continue;
                    }
                    vPath.append(node).append(" -> ");
                }
                System.err.println(vPath);
            });
            System.err.println(key + "\n");
        }

        System.out.println("No of files analyzed " +
            (javaSliceProfilesInfo.size() + cppSliceProfilesInfo.size()));
        System.out.println("Detected violations " + violationsCount);
        long end = System.currentTimeMillis();
        System.out.println("Completed analysis in " + (end - start) / 1000 + "s");
        return violationsToPrint;
    }

    private static void analyzeSliceProfile(SliceProfile profile, Map<String, SliceProfilesInfo> rawProfilesInfo) {
        analyzedProfiles.add(profile);

        // step-01 : analyse cfunctions of the slice variable
        EnclNamePosTuple enclNamePosTuple;
        for (CFunction cFunction : profile.cfunctions) {
            analyzeCfunction(cFunction, profile, rawProfilesInfo);
        }
        enclNamePosTuple = new EnclNamePosTuple(profile.varName, profile.functionName, profile.fileName,
            profile.definedPosition);
        if (!DG.containsVertex(enclNamePosTuple)) {
            DG.addVertex(enclNamePosTuple);
        }

        // step-02 : analyze data dependent vars of the slice variable
        for (NamePos dependentVar : profile.dependentVars) {
            String dvarName = dependentVar.getName();
            String dvarEnclFunctionName = dependentVar.getType();
            String dvarPos = dependentVar.getPos();
            Map<String, SliceProfile> sourceSliceProfiles = rawProfilesInfo.get(profile.fileName).sliceProfiles;
            String sliceKey =
                dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
            if (!sourceSliceProfiles.containsKey(sliceKey)) {
                // not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
            EnclNamePosTuple dvarNamePosTuple = new EnclNamePosTuple(dvarSliceProfile.varName,
                dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                dvarSliceProfile.definedPosition);
            if (!hasNoEdge(enclNamePosTuple, dvarNamePosTuple)) {
                continue;
            }
            if (isAnalyzedProfile(dvarSliceProfile)) {
                continue;
            }
            analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
        }

        // step-03 : analyze if given function node is a native method
        if (!profile.functionName.equals("GLOBAL") && profile.cfunctions.size() < 1
            && profile.functionNode != null) {
            Node enclFunctionNode = profile.functionNode;
            if (isFunctionOfGivenModifier(enclFunctionNode, JNI_NATIVE_METHOD_MODIFIER)) {
                analyzeNativeFunction(profile, rawProfilesInfo, enclFunctionNode, enclNamePosTuple);
            }
        }

        if (!mode.checkBuffer()) {
            return;
        }

        if (profile.fileName.endsWith(".java")) {
            return;
        }

        if (singleTarget != null) {
            return;
        }

        // step-04 : check and add buffer reads and writes for this profile
        for (SliceVariableAccess varAccess : profile.usedPositions) {
            for (DataTuple access : varAccess.writePositions) {
                if (XmlUtil.DataAccessType.BUFFER_WRITE == access.accessType) {
                    ArrayList<String> violations;
                    if (detectedViolations.containsKey(enclNamePosTuple)) {
                        violations = new ArrayList<>(detectedViolations.get(enclNamePosTuple));
                    } else {
                        violations = new ArrayList<>();
                    }
                    violations.add("Buffer write at " + access.accessVarNamePos.getPos());
                    detectedViolations.put(enclNamePosTuple, violations);
                } else if (XmlUtil.DataAccessType.BUFFER_READ == access.accessType) {
                    ArrayList<String> violations;
                    if (detectedViolations.containsKey(enclNamePosTuple)) {
                        violations = new ArrayList<>(detectedViolations.get(enclNamePosTuple));
                    } else {
                        violations = new ArrayList<>();
                    }
                    violations.add("Buffer read at " + access.accessVarNamePos.getPos());
                    detectedViolations.put(enclNamePosTuple, violations);
                }
            }
        }

        for (SliceVariableAccess varAccess : profile.dataAccess) {
            for (DataTuple access : varAccess.writePositions) {
                NamePos dependentVar = access.accessVarNamePos;
                String dvarName = dependentVar.getName();
                String dvarEnclFunctionName = dependentVar.getType();
                String dvarPos = dependentVar.getPos();
                Map<String, SliceProfile> sourceSliceProfiles = rawProfilesInfo.get(profile.fileName).sliceProfiles;
                String sliceKey = dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
                if (!sourceSliceProfiles.containsKey(sliceKey)) { // not capturing struct/class var assignments
                    continue;
                }
                SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
                EnclNamePosTuple dvarNamePosTuple = new EnclNamePosTuple(dvarSliceProfile.varName,
                    dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                    dvarSliceProfile.definedPosition);
                if (hasNoEdge(enclNamePosTuple, dvarNamePosTuple) && !isAnalyzedProfile(dvarSliceProfile)) {
                    analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
                }

                if (dvarSliceProfile.isPointer && XmlUtil.DataAccessType.DATA_WRITE == access.accessType
                    && singleTarget == null) {
                    ArrayList<String> violations;
                    if (detectedViolations.containsKey(dvarNamePosTuple)) {
                        violations = new ArrayList<>(detectedViolations.get(dvarNamePosTuple));
                    } else {
                        violations = new ArrayList<>();
                    }
                    violations.add("Pointer data write of '" + dvarSliceProfile.varName + "' at " +
                        access.accessVarNamePos.getPos());
                    detectedViolations.put(dvarNamePosTuple, violations);
                }
            }
        }
    }

    private static void analyzeCfunction(CFunction cFunction, SliceProfile profile,
                                         Map<String, SliceProfilesInfo> sliceProfilesInfo) {
        if (singleTarget != null) {
            return;
        }

        String cfunctionName = cFunction.getName();
        String cfunctionPos = cFunction.getPosition();
        String enclFunctionName = cFunction.getEnclFunctionName();
        EnclNamePosTuple enclNamePosTuple = new EnclNamePosTuple(profile.varName, enclFunctionName, profile.fileName,
            profile.definedPosition);

        if (SINK_FUNCTIONS.contains(cfunctionName)) {
            DG.addVertex(enclNamePosTuple);
            ArrayList<String> cErrors = new ArrayList<>();
            cErrors.add("Use of " + cfunctionName + " at " + cfunctionPos);
            EnclNamePosTuple bufferErrorFunctionPosTuple =
                new EnclNamePosTuple(enclNamePosTuple.varName() + "#" + cfunctionName,
                    enclNamePosTuple.functionName(), enclNamePosTuple.fileName(), cfunctionPos, true);
            hasNoEdge(enclNamePosTuple, bufferErrorFunctionPosTuple);
            detectedViolations.put(bufferErrorFunctionPosTuple, cErrors);
            return;
        }

        LinkedList<SliceProfile> dependentSliceProfiles = findDependentSliceProfiles(cFunction, profile.typeName,
            sliceProfilesInfo);
        for (SliceProfile dependentSliceProfile : dependentSliceProfiles) {
            EnclNamePosTuple depNamePosTuple = new EnclNamePosTuple(dependentSliceProfile.varName,
                dependentSliceProfile.functionName, dependentSliceProfile.fileName,
                dependentSliceProfile.definedPosition, dependentSliceProfile.isFunctionNameProfile);
            if (!hasNoEdge(enclNamePosTuple, depNamePosTuple)) {
                continue;
            }
            if (isAnalyzedProfile(dependentSliceProfile)) {
                continue;
            }
            analyzeSliceProfile(dependentSliceProfile, sliceProfilesInfo);
        }
    }

    private static LinkedList<SliceProfile> findDependentSliceProfiles(CFunction cFunction, String typeName,
                                                                       Map<String, SliceProfilesInfo> profilesInfoMap) {
        LinkedList<SliceProfile> dependentSliceProfiles = new LinkedList<>();
        for (String filePath : profilesInfoMap.keySet()) {
            SliceProfilesInfo profileInfo = profilesInfoMap.get(filePath);
            List<FunctionNamePos> possibleFunctions = findPossibleFunctions(profileInfo.functionNodes,
                profileInfo.functionDeclMap, cFunction, typeName);
            for (FunctionNamePos functionNamePos : possibleFunctions) {
                // 01 - Add cfunction profile
                String key = functionNamePos.getName() + "%" + functionNamePos.getPos() + "%" +
                    functionNamePos.getName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));

                if (cFunction.isEmptyArgFunc() || functionNamePos.getArguments() == null ||
                    functionNamePos.getArguments().isEmpty()) {
                    continue;
                }

                // 02 - Add function arg index based profile
                NamePos param = functionNamePos.getArguments().get(cFunction.getArgPosIndex());
                String param_name = param.getName();
                String param_pos = param.getPos();
                key = param_name + "%" + param_pos + "%" + functionNamePos.getName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));
            }
        }
        return dependentSliceProfiles;
    }


    private static void analyzeNativeFunction(SliceProfile profile, Map<String, SliceProfilesInfo> profilesInfo,
                                              Node enclFunctionNode, EnclNamePosTuple enclNamePosTuple) {
        Node enclUnitNode = profilesInfo.get(profile.fileName).unitNode;
        String jniFunctionName = profile.functionName;
        if (jniFunctionName.length() > 2 && jniFunctionName.startsWith("n") &&
            Character.isUpperCase(jniFunctionName.charAt(1))) {
            jniFunctionName = jniFunctionName.substring(1);
        }
        String jniArgName = profile.varName;
        List<ArgumentNamePos> parameters = XmlUtil.findFunctionParameters(enclFunctionNode);
        int index = 0;
        for (NamePos parameter : parameters) {
            if (parameter.getName().equals(jniArgName)) {
                break;
            }
            index++;
        }
        int jniArgPosIndex = index + 2; // first two arugments in native jni methods are env and obj
        String clazzName = XmlUtil.getNodeByName(XmlUtil.getNodeByName(enclUnitNode, "class").get(0),
            "name").get(0).getTextContent();
        String jniFunctionSearchStr = clazzName + "_" + jniFunctionName;

        for (String filePath : cppSliceProfilesInfo.keySet()) {
            SliceProfilesInfo profileInfo = cppSliceProfilesInfo.get(filePath);

            for (FunctionNamePos funcNamePos : profileInfo.functionNodes.keySet()) {
                Node functionNode = profileInfo.functionNodes.get(funcNamePos);
                String functionName = funcNamePos.getName();
                if (!functionName.toLowerCase().endsWith(jniFunctionSearchStr.toLowerCase())) {
                    continue;
                }
                List<ArgumentNamePos> functionArgs = XmlUtil.findFunctionParameters(functionNode);
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
                if (isAnalyzedProfile(possibleSliceProfile)) {
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

    private static List<FunctionNamePos> findPossibleFunctions(Map<FunctionNamePos, Node> functionNodes,
                                                               Map<String, List<FunctionNamePos>> functionDeclMap,
                                                               CFunction cFunction, String argTypeName) {
        String cfunctionName = cFunction.getName();
        int argPosIndex = cFunction.getArgPosIndex();
        Node enclFunctionNode = cFunction.getEnclFunctionNode();
        List<FunctionNamePos> possibleFunctions = new ArrayList<>();

        if (enclFunctionNode == null) {
            return possibleFunctions;
        }

        List<String> cFunctionsWithAlias = new ArrayList<>();
        cFunctionsWithAlias.add(cfunctionName);

        if (functionDeclMap.containsKey(cfunctionName)) {
            cFunctionsWithAlias.addAll(functionDeclMap.get(cfunctionName)
                .stream()
                .map(FunctionNamePos::getFunctionDeclName)
                .collect(Collectors.toList()));
        }

        for (String cFuncName : cFunctionsWithAlias) {
            for (FunctionNamePos functionNamePos : functionNodes.keySet()) {
                String functionName = functionNamePos.getName();
                if (!functionName.equals(cFuncName)) {
                    continue;
                }
                if (cFunction.isEmptyArgFunc()) {
                    possibleFunctions.add(functionNamePos);
                    continue;
                }
                if (!typeCheckFunctionSignature(functionNamePos, argPosIndex, argTypeName, cFunction)) {
                    continue;
                }
                possibleFunctions.add(functionNamePos);
            }
        }
        return possibleFunctions;
    }

    private static boolean typeCheckFunctionSignature(FunctionNamePos functionNamePos, int argPosIndex,
                                                      String argTypeName, CFunction cFunction) {
        List<ArgumentNamePos> funcArgs = functionNamePos.getArguments();
        if (funcArgs.size() == 0 || argPosIndex >= funcArgs.size()) {
            return false;
        }
        ArgumentNamePos namePos = funcArgs.get(argPosIndex);
        if (namePos == null) {
            return false;
        }
        String paramName = namePos.getName();
        if (paramName.equals("")) {
            return false;
        }

        if (cFunction.getNumberOfArguments() < getRequiredArgumentsSize(funcArgs)) {
            return false;
        }

        return TypeChecker.isAssignable(namePos.getType(), argTypeName);
    }

    private static int getRequiredArgumentsSize(List<ArgumentNamePos> funcArgs) {
        return (int) funcArgs.stream().filter(arg -> !arg.isOptional()).count();
    }

    private static SliceProfilesInfo analyzeSourceUnitAndBuildSlices(Node unitNode, String sourceFilePath,
                                                                     Set<TypeSymbol> typeSymbols) {
        SliceGenerator sliceGenerator = new SliceGenerator(unitNode, sourceFilePath, typeSymbols);
        return sliceGenerator.generate();
    }
}
