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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * {@link PilaiPidi} is the driver class that initiate and drive the pilai-pidi phases one by one.
 *
 * @since 0.0.1
 */
public class PilaiPidi {

    private static final Logger log = Logger.getLogger(PilaiPidi.class.getName());
    private static List<String> SINK_FUNCTIONS;
    private static List<String> SOURCE_FUNCTIONS;
    private static MODE mode = MODE.EXECUTE;
    private static final String DEFAULT_SINK_FUNCTIONS_FILE = "common/sink_functions.xml";
    private static final String DEFAULT_SOURCE_FUNCTIONS_FILE = "common/source_functions.xml";

    private static Hashtable<String, Set<List<DFGNode>>> findSourcesAndSinks(Graph<DFGNode, DefaultEdge> graph,
                                                                             Map<DFGNode, List<String>> dataFlowPaths,
                                                                             ArugumentOptions arugumentOptions) {
        SourceSinkFinder sourceSinkFinder = new SourceSinkFinder(graph, dataFlowPaths, SOURCE_FUNCTIONS,
                arugumentOptions.singleTarget, mode);
        return sourceSinkFinder.invoke();
    }

    private static void analyzeDataFlow(Graph<DFGNode, DefaultEdge> graph,
                                        Map<DFGNode, List<String>> dataFlowPaths,
                                        ArugumentOptions arugumentOptions,
                                        Map<String, SliceProfilesInfo> sliceProfilesInfo) {
        DataFlowAnalyzer dataFlowAnalyzer = new DataFlowAnalyzer(sliceProfilesInfo, graph, dataFlowPaths,
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
        String[] singleTarget = null;
        if (optsList.containsKey("-node")) {
            singleTarget = optsList.get("-node").split("@AT@");
        }

        String projectLocation = args[0];
        return new ArugumentOptions(argsList, optsList, doubleOptsList, projectLocation, singleTarget);
    }

    private static void loadFunctiosFromXmlInputStream(List<String> functionList, InputStream xmlInputStream) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document sinkFunctionsXmlDoc = db.parse(xmlInputStream);
        sinkFunctionsXmlDoc.getDocumentElement().normalize();

        NodeList list = sinkFunctionsXmlDoc.getElementsByTagName("function");
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            Element element = (Element) node;
            String functionName = element.getElementsByTagName("name").item(0).getTextContent().strip();
            functionList.add(functionName);
        }
    }

    private static void loadBufferAccessSinkFunctions(ArugumentOptions arugumentOptions) {
        InputStream functionsInputStream = null;
        if (arugumentOptions.optsList.containsKey("-functions")) {
            String functionsFile = arugumentOptions.optsList.get("-functions");
            try {
                functionsInputStream = new FileInputStream(new File(functionsFile));
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("Unable to find XML file of sink functions. Please check that the " +
                        "-functions param is correct.");
            }

        } else {
            functionsInputStream = PilaiPidi.class.getClassLoader().getResourceAsStream(DEFAULT_SINK_FUNCTIONS_FILE);
        }

        SINK_FUNCTIONS = new ArrayList<>();
        try {
            loadFunctiosFromXmlInputStream(SINK_FUNCTIONS, functionsInputStream);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.log(Level.SEVERE, "Error loading buffer access sink functions list from XML", e.getStackTrace());
            throw new RuntimeException(e);
        }
    }

    private static void loadBufferAccessSourceFunctions(ArugumentOptions arugumentOptions) {
        InputStream functionsInputStream = null;
        if (arugumentOptions.optsList.containsKey("-sourcefunctions")) {
            String functionsFile = arugumentOptions.optsList.get("-sourcefunctions");
            try {
                functionsInputStream = new FileInputStream(new File(functionsFile));
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("Unable to find XML file of source functions. Please check that " +
                        "the " +
                        "-sourcefunctions param is correct.");
            }

        } else {
            functionsInputStream = PilaiPidi.class.getClassLoader().getResourceAsStream(DEFAULT_SOURCE_FUNCTIONS_FILE);
        }

        SOURCE_FUNCTIONS = new ArrayList<>();
        try {
            loadFunctiosFromXmlInputStream(SOURCE_FUNCTIONS, functionsInputStream);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.log(Level.SEVERE, "Error loading source functions list from XML", e.getStackTrace());
            throw new RuntimeException(e);
        }
    }

    public Hashtable<String, Set<List<DFGNode>>> invoke(String[] args) {
        final Graph<DFGNode, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        final Map<DFGNode, List<String>> dataFlowPaths = new Hashtable<>();
        long start = System.currentTimeMillis();
        final ArugumentOptions arugumentOptions = processArguments(args);
        if (arugumentOptions.doubleOptsList.size() > 0 && arugumentOptions.doubleOptsList.contains("debug")) {
            mode = MODE.TEST;
        }
        loadBufferAccessSinkFunctions(arugumentOptions);
        loadBufferAccessSourceFunctions(arugumentOptions);
        final Document document = generateAndParseSrcML(arugumentOptions);
        final Set<TypeSymbol> typeSymbols = findSymbols(document);
        final Map<String, SliceProfilesInfo> sliceProfiles = generateSliceProfiles(document, typeSymbols);
        analyzeDataFlow(graph, dataFlowPaths, arugumentOptions, sliceProfiles);
        long mid = System.currentTimeMillis();
        System.out.println("Completed analyzing slice profiles in " + (mid - start) / 1000 + "s");
        final Hashtable<String, Set<List<DFGNode>>> sourcesAndSinks = findSourcesAndSinks(graph,
                dataFlowPaths, arugumentOptions);
        long end = System.currentTimeMillis();
        System.out.println("Number of files analyzed = " + sliceProfiles.size());
        System.out.println("Completed analysis in " + (end - start) / 1000 + "s");

        return sourcesAndSinks;
    }
}
