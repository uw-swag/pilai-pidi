package ca.uwaterloo.swag.pilaipidi.phases;

import ca.uwaterloo.swag.pilaipidi.models.EnclNamePosTuple;
import ca.uwaterloo.swag.pilaipidi.util.MODE;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import org.apache.commons.io.FileUtils;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.traverse.BreadthFirstIterator;

public class SourceSinkFinder {

    private final Graph<EnclNamePosTuple, DefaultEdge> graph;
    private final Map<EnclNamePosTuple, List<String>> detectedViolations;
    private final String[] singleTarget;
    private final MODE mode;

    public SourceSinkFinder(Graph<EnclNamePosTuple, DefaultEdge> graph,
                            Map<EnclNamePosTuple, List<String>> detectedViolations, String[] singleTarget, MODE mode) {
        this.graph = graph;
        this.detectedViolations = detectedViolations;
        this.singleTarget = singleTarget;
        this.mode = mode;
    }

    public Hashtable<String, Set<List<EnclNamePosTuple>>> invoke() {
        if (mode.exportGraph()) {
            exportGraph(graph);
        }
        return findViolatedPaths(graph, detectedViolations, singleTarget);
    }

    private void exportGraph(Graph<EnclNamePosTuple, DefaultEdge> graph) {
        System.out.println("Exporting graph...");
        DOTExporter<EnclNamePosTuple, DefaultEdge> exporter = new DOTExporter<>(
            EnclNamePosTuple::toString);
        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        final File file = new File(FileSystems.getDefault().getPath(".").toString(), "graph.dot");
        try {
            FileUtils.writeStringToFile(file, writer.toString(), Charset.defaultCharset());
        } catch (IOException e) {
            //ignore
        }
    }

    private void bfsSolution(EnclNamePosTuple source, Graph<EnclNamePosTuple, DefaultEdge> graph, List<String> lookup) {
        List<List<EnclNamePosTuple>> completePaths = new ArrayList<>();

        // Run a BFS from the source vertex. Each time a new vertex is encountered, construct a new path.
        BreadthFirstIterator<EnclNamePosTuple, DefaultEdge> bfs = new BreadthFirstIterator<>(graph, source);
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

    private boolean containsAllWords(String word, List<String> keywords) {
        for (String keyword : keywords) {
            if (!word.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private Hashtable<String, Set<List<EnclNamePosTuple>>> findViolatedPaths(
        Graph<EnclNamePosTuple, DefaultEdge> graph, Map<EnclNamePosTuple, List<String>> detectedViolations,
        String[] singleTarget) {
        BFSShortestPath<EnclNamePosTuple, DefaultEdge> bfsShortestPath = new BFSShortestPath<>(graph);
        Hashtable<String, Set<List<EnclNamePosTuple>>> violatedPaths = new Hashtable<>();
        ArrayList<EnclNamePosTuple> sourceNodes = new ArrayList<>();
        for (EnclNamePosTuple node : graph.vertexSet()) {
            if (graph.inDegreeOf(node) == 0 && node.fileName().endsWith(".java")) {
                sourceNodes.add(node);
            }
        }
        int violationsCount = 0;
        for (EnclNamePosTuple sourceNode : sourceNodes) {
            if (mode.skipViolations()) {
                bfsSolution(sourceNode, graph, mode.lookupString());
                continue;
            }
            if (singleTarget != null) {
                Optional<EnclNamePosTuple> actualTarget = graph.vertexSet()
                    .stream()
                    .filter(enclNamePosTuple -> enclNamePosTuple.fileName().equals(singleTarget[1]) &&
                        enclNamePosTuple.varName().equals(singleTarget[0]) &&
                        enclNamePosTuple.definedPosition().equals(singleTarget[2]))
                    .findFirst();
                actualTarget.ifPresent(enclNamePosTuple -> detectedViolations.put(enclNamePosTuple,
                    new ArrayList<>(Collections.singletonList(String.join("@AT@", singleTarget)))));
            }

            for (EnclNamePosTuple violatedNodePos : detectedViolations.keySet()) {
                List<String> violations = detectedViolations.get(violatedNodePos);

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
                        if (violatedPaths.containsKey(violation)) {
                            currentArray = violatedPaths.get(violation);
                        } else {
                            currentArray = new HashSet<>();
                        }
                        currentArray.add(vertexList);
                        violatedPaths.put(violation, currentArray);
                    });
                    violationsCount = violationsCount + violations.size();
                }
            }
        }

        for (Entry<String, Set<List<EnclNamePosTuple>>> entry : violatedPaths.entrySet()) {
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
                    if (MODE.TEST == mode && node.isFunctionNamePos()) {
                        continue;
                    }
                    vPath.append(node).append(" -> ");
                }
                System.err.println(vPath);
            });
            System.err.println(key + "\n");
        }

        System.out.println("Detected violations " + violationsCount);
        return violatedPaths;
    }
}
