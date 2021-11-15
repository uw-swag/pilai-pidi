package ca.uwaterloo.swag.pilaipidi.phases;

import ca.uwaterloo.swag.pilaipidi.models.DFGNode;
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

    private final Graph<DFGNode, DefaultEdge> graph;
    private final Map<DFGNode, List<String>> detectedViolations;
    private final String[] singleTarget;
    private final MODE mode;

    public SourceSinkFinder(Graph<DFGNode, DefaultEdge> graph, Map<DFGNode, List<String>> detectedViolations,
                            String[] singleTarget, MODE mode) {
        this.graph = graph;
        this.detectedViolations = detectedViolations;
        this.singleTarget = singleTarget;
        this.mode = mode;
    }

    public Hashtable<String, Set<List<DFGNode>>> invoke() {
        if (mode.exportGraph()) {
            exportGraph(graph);
        }
        return findViolatedPaths(graph, detectedViolations, singleTarget);
    }

    private void exportGraph(Graph<DFGNode, DefaultEdge> graph) {
        System.out.println("Exporting graph...");
        DOTExporter<DFGNode, DefaultEdge> exporter = new DOTExporter<>(
            DFGNode::toString);
        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        final File file = new File(FileSystems.getDefault().getPath(".").toString(), "graph.dot");
        try {
            FileUtils.writeStringToFile(file, writer.toString(), Charset.defaultCharset());
        } catch (IOException e) {
            //ignore
        }
    }

    private void bfsSolution(DFGNode source, Graph<DFGNode, DefaultEdge> graph, List<String> lookup) {
        List<List<DFGNode>> completePaths = new ArrayList<>();

        // Run a BFS from the source vertex. Each time a new vertex is encountered, construct a new path.
        BreadthFirstIterator<DFGNode, DefaultEdge> bfs = new BreadthFirstIterator<>(graph, source);
        while (bfs.hasNext()) {
            DFGNode vertex = bfs.next();
            // Create path P that ends in the vertex by backtracking from the new vertex we encountered
            Stack<DFGNode> partialPathP = new Stack<>();
            while (vertex != null) {
                partialPathP.push(vertex);
                vertex = bfs.getParent(vertex);
            }
            List<DFGNode> pathP = new ArrayList<>(partialPathP.size());
            while (!partialPathP.isEmpty()) {
                pathP.add(partialPathP.pop());
            }
            completePaths.add(pathP);
        }
        for (List<DFGNode> smallPath : completePaths) {
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

    private Hashtable<String, Set<List<DFGNode>>> findViolatedPaths(Graph<DFGNode, DefaultEdge> graph,
                                                                    Map<DFGNode, List<String>> detectedViolations,
                                                                    String[] singleTarget) {
        BFSShortestPath<DFGNode, DefaultEdge> bfsShortestPath = new BFSShortestPath<>(graph);
        Hashtable<String, Set<List<DFGNode>>> violatedPaths = new Hashtable<>();
        List<DFGNode> sourceNodes = new ArrayList<>();
        for (DFGNode node : graph.vertexSet()) {
            if (graph.inDegreeOf(node) == 0 && node.fileName().endsWith(".java")) {
                sourceNodes.add(node);
            }
        }
        int violationsCount = 0;
        for (DFGNode sourceNode : sourceNodes) {
            if (mode.skipViolations()) {
                bfsSolution(sourceNode, graph, mode.lookupString());
                continue;
            }
            if (singleTarget != null) {
                Optional<DFGNode> actualTarget = graph.vertexSet()
                    .stream()
                    .filter(dfgNode -> dfgNode.fileName().equals(singleTarget[1]) &&
                        dfgNode.varName().equals(singleTarget[0]) &&
                        dfgNode.definedPosition().equals(singleTarget[2]))
                    .findFirst();
                actualTarget.ifPresent(DFGNode -> detectedViolations.put(DFGNode,
                    new ArrayList<>(Collections.singletonList(String.join("@AT@", singleTarget)))));
            }

            for (DFGNode violatedNode : detectedViolations.keySet()) {
                List<String> violations = detectedViolations.get(violatedNode);
                GraphPath<DFGNode, DefaultEdge> requiredPath = bfsShortestPath.getPath(sourceNode, violatedNode);
                if (requiredPath != null) {
                    List<DFGNode> vertexList = requiredPath.getVertexList();
                    violations.forEach(violation -> {
                        Set<List<DFGNode>> currentArray;
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

        for (Entry<String, Set<List<DFGNode>>> entry : violatedPaths.entrySet()) {
            String key = entry.getKey();
            Set<List<DFGNode>> violations = entry.getValue();
            violations.forEach(violation -> {
                System.err.print("Possible out-of-bounds operation path : ");
                StringBuilder vPath = new StringBuilder();
                int size = violation.size() - 1;
                if (key.startsWith("Buffer")) {
                    size = violation.size();
                }
                for (int i = 0; i < size; i++) {
                    DFGNode node = violation.get(i);
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
