package example.pdgextractor;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.AbstractMap.SimpleEntry;

public class DirectedGraph {
    private final Map<Integer, Object> contexts = new HashMap<>();
    private final Map<Integer, Set<Integer>> edges = new HashMap<>();
    private final Map<Integer, Set<Integer>> edgesReverse = new HashMap<>();
    private final Map<Integer, Object> idToObject = new HashMap<>();
    private final Map<Object, Integer> objectToId = new HashMap<>();
    private int nextId = 0;
    private final Map<Map.Entry<Integer, Integer>, Set<Map.Entry<String, Object>>> edgeAnnotations = new HashMap<>();

    public void addEdge(Object fromNode, Object toNode, SimpleEntry<String, Object> annotation, Object fromNodeContext, Object toNodeContext) {
        if (fromNode == null || toNode == null) return;
        int fromNodeId = getObjectId(fromNode);
        int toNodeId = getObjectId(toNode);
        if (fromNodeContext != null) contexts.put(fromNodeId, fromNodeContext);
        if (toNodeContext != null) contexts.put(toNodeId, toNodeContext);

        edges.computeIfAbsent(fromNodeId, k -> new HashSet<>()).add(toNodeId);
        edgesReverse.computeIfAbsent(toNodeId, k -> new HashSet<>()).add(fromNodeId);

        Map.Entry<Integer, Integer> key = new AbstractMap.SimpleEntry<>(fromNodeId, toNodeId);
        edgeAnnotations.computeIfAbsent(key, k -> new HashSet<>()).add(annotation);
    }

    public Collection<?> getOutEdgesFrom(Object node) {
        int nodeId = getObjectId(node);
        try {
            return edges.getOrDefault(nodeId, Collections.emptySet()).stream()
                    .map(idToObject::get)
                    .collect(Collectors.toList());
        } catch (NoSuchElementException e) {
            return Collections.emptyList();
        }
    }

    public Object getRootNode() {
        return idToObject.get(0);
    }

    public boolean containsNode(Object node) {
        return node != null && objectToId.containsKey(node);
    }

    private int getObjectId(Object obj) {
        return objectToId.computeIfAbsent(obj, k -> {
            int id = nextId;
            idToObject.put(nextId, obj);
            nextId++;
            return id;
        });
    }

    private String dotEscape(String input) {
        return input.replace("\"", "''").replace("\r", "\\r").replace("\n", "\\n");
    }

    public void toDot(String filepath, Function<Object, String> nodeNames, Function<Object, String> nodeSpans, Function<Map.Entry<String, Object>, String> arrowStyle, Function<Object, String> nodeColour) throws IOException {
        Map<Object, Set<Integer>> contextMap = new HashMap<>();
        for (var entry : idToObject.entrySet()) {
            Object context = contexts.getOrDefault(entry.getKey(), "none");
            contextMap.computeIfAbsent(context, k -> new HashSet<>()).add(entry.getKey());
        }

        try (FileWriter writer = new FileWriter(filepath)) {
            writer.write("digraph \"extractedGraph\"{\n");
            int clusterNum = 0;
            for (var entry : contextMap.entrySet()) {
                if (!entry.getKey().equals("none")) {
                    writer.write("subgraph cluster_" + clusterNum + " {\n");
                    writer.write("label = \"" + dotEscape(nodeNames.apply(entry.getKey())) + "\";\n");
                    clusterNum++;
                }

                for (int key : entry.getValue()) {
                    String nodeLabel = "n" + key + " [label=\"" + dotEscape(nodeNames.apply(idToObject.get(key))) + "\", span=\"" + dotEscape(nodeSpans.apply(idToObject.get(key))) + "\"";
                    if (nodeColour != null) {
                        nodeLabel += ", color=\"" + nodeColour.apply(idToObject.get(key)) + "\"";
                    }
                    nodeLabel += "];";
                    writer.write(nodeLabel + "\n");
                }

                if (!entry.getKey().equals("none")) {
                    writer.write("}\n");
                }
            }

            for (var edgeEntry : edges.entrySet()) {
                for (int target : edgeEntry.getValue()) {
                    for (var annotation : edgeAnnotations.get(new AbstractMap.SimpleEntry<>(edgeEntry.getKey(), target))) {
                        writer.write("n" + edgeEntry.getKey() + "->n" + target + " [style=" + arrowStyle.apply(annotation) + "];\n");
                    }
                }
            }

            writer.write("}\n");
        }
    }

    public void toJson(String filepath, Function<Object, String> nodeNames) {
        // Implement JSON export functionality if required
    }
}
