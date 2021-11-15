package ca.uwaterloo.swag.pilaipidi;

import ca.uwaterloo.swag.pilaipidi.models.DFGNode;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfilesInfo;
import ca.uwaterloo.swag.pilaipidi.models.TypeSymbol;
import ca.uwaterloo.swag.pilaipidi.phases.DataFlowAnalyzer;
import ca.uwaterloo.swag.pilaipidi.phases.SliceGenerator;
import ca.uwaterloo.swag.pilaipidi.phases.SourceSinkFinder;
import ca.uwaterloo.swag.pilaipidi.phases.SrcMLGenerator;
import ca.uwaterloo.swag.pilaipidi.phases.SymbolFinder;
import ca.uwaterloo.swag.pilaipidi.util.ArugumentOptions;
import ca.uwaterloo.swag.pilaipidi.util.MODE;
import ca.uwaterloo.swag.pilaipidi.util.XmlUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());

    private static List<String> SINK_FUNCTIONS = Arrays.asList("strcat", "strdup", "strncat", "strcmp",
        "strncmp", "strcpy", "strncpy", "strlen", "strchr", "strrchr", "index", "rindex", "strpbrk", "strspn",
        "strcspn", "strstr", "strtok", "memccpy", "memchr", "memmove", "memcpy", "memcmp", "memset", "bcopy",
        "bzero", "bcmp");
    private static MODE mode = MODE.EXECUTE;

    public static void main(String[] args) {
        nonCLI(args);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Hashtable<String, Set<List<DFGNode>>> nonCLI(String[] args) {
        final Graph<DFGNode, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        final Map<DFGNode, List<String>> detectedViolations = new Hashtable<>();
        long start = System.currentTimeMillis();

        final ArugumentOptions arugumentOptions = processArguments(args);
        if (arugumentOptions.doubleOptsList.size() > 0 && arugumentOptions.doubleOptsList.contains("debug")) {
            mode = MODE.TEST;
        }

        final Document document = generateAndParseSrcML(arugumentOptions);

        final Set<TypeSymbol> typeSymbols = findSymbols(document);

        final Map<String, SliceProfilesInfo> sliceProfiles = generateSliceProfiles(document, typeSymbols);

        analyzeDataFlow(graph, detectedViolations, arugumentOptions, sliceProfiles);

        long mid = System.currentTimeMillis();
        System.out.println("Completed analyzing slice profiles in " + (mid - start) / 1000 + "s");

        final Hashtable<String, Set<List<DFGNode>>> sourcesAndSinks = findSourcesAndSinks(graph,
            detectedViolations, arugumentOptions);

        long end = System.currentTimeMillis();
        System.out.println("No of files analyzed " + sliceProfiles.size());
        System.out.println("Completed analysis in " + (end - start) / 1000 + "s");

        return sourcesAndSinks;
    }

    private static Hashtable<String, Set<List<DFGNode>>> findSourcesAndSinks(
        Graph<DFGNode, DefaultEdge> graph, Map<DFGNode, List<String>> detectedViolations,
        ArugumentOptions arugumentOptions) {
        SourceSinkFinder sourceSinkFinder = new SourceSinkFinder(graph, detectedViolations,
            arugumentOptions.singleTarget, mode);
        return sourceSinkFinder.invoke();
    }

    private static void analyzeDataFlow(Graph<DFGNode, DefaultEdge> graph,
                                        Map<DFGNode, List<String>> detectedViolations,
                                        ArugumentOptions arugumentOptions,
                                        Map<String, SliceProfilesInfo> sliceProfilesInfo) {
        DataFlowAnalyzer dataFlowAnalyzer = new DataFlowAnalyzer(sliceProfilesInfo, graph, detectedViolations,
            SINK_FUNCTIONS, arugumentOptions.singleTarget, mode);
        dataFlowAnalyzer.analyze();
    }

    private static Map<String, SliceProfilesInfo> generateSliceProfiles(Document document,
                                                                        Set<TypeSymbol> typeSymbols) {
        final Map<String, SliceProfilesInfo> sliceProfilesInfo = new Hashtable<>();
        for (Node unitNode : XmlUtil.asList(document.getElementsByTagName("unit"))) {
            Node fileName = unitNode.getAttributes().getNamedItem("filename");
            if (fileName != null) {
                String sourceFilePath = fileName.getNodeValue();
                if (unitNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                SliceGenerator sliceGenerator = new SliceGenerator(unitNode, sourceFilePath, typeSymbols);
                sliceProfilesInfo.put(sourceFilePath, sliceGenerator.generate());
            }
        }
        return sliceProfilesInfo;
    }

    private static Set<TypeSymbol> findSymbols(Document document) {
        final Set<TypeSymbol> typeSymbols = new HashSet<>();
        for (Node unitNode : XmlUtil.asList(document.getElementsByTagName("unit"))) {
            Node fileName = unitNode.getAttributes().getNamedItem("filename");
            if (fileName != null) {
                String sourceFilePath = fileName.getNodeValue();
                if (unitNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                SymbolFinder symbolFinder = new SymbolFinder(unitNode, sourceFilePath, typeSymbols);
                symbolFinder.invoke();
            }
        }
        return typeSymbols;
    }

    private static Document generateAndParseSrcML(ArugumentOptions arugumentOptions) {
        final Document document;
        try {
            SrcMLGenerator srcMLGenerator = new SrcMLGenerator(arugumentOptions);
            document = srcMLGenerator.generateSrcML();
        } catch (IOException | URISyntaxException | ParserConfigurationException | SAXException e) {
            log.log(Level.SEVERE, "Error generating srcML for the given project", e.getStackTrace());
            throw new RuntimeException(e);
        }
        return document;
    }

    private static ArugumentOptions processArguments(String[] args) {
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
        if (optsList.containsKey("-functions")) {
            String functionsFile = optsList.get("-functions");
            Path functionFilePath = Path.of(functionsFile);
            if (Files.exists(functionFilePath)) {
                try (Scanner sc = new Scanner(functionFilePath.toFile(), StandardCharsets.UTF_8)) {
                    SINK_FUNCTIONS = new ArrayList<>();
                    while (sc.hasNextLine()) {
                        SINK_FUNCTIONS.add(sc.nextLine());
                    }
                } catch (IOException e) {
                    //ignore
                }
            }
        }
        String[] singleTarget = null;
        if (optsList.containsKey("-node")) {
            singleTarget = optsList.get("-node").split("@AT@");
        }

        String projectLocation = args[0];
        return new ArugumentOptions(argsList, optsList, doubleOptsList, projectLocation, singleTarget);
    }
}
