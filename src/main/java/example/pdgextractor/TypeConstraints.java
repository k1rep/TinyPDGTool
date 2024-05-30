package example.pdgextractor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeConstraints {
    public final Map<AbstractNode, Set<AbstractNode>> allRelationships = new HashMap<>();

    public void addFromCompilation(List<CompilationUnit> compilations, JavaParserFacade javaParserFacade) {
        for (CompilationUnit compilationUnit : compilations) {
            new FileTypeRelationCollector(javaParserFacade, allRelationships, true).visit(compilationUnit, null);
        }
    }

    public void collectForSingleFile(CompilationUnit unit, JavaParserFacade javaParserFacade) {
        new FileTypeRelationCollector(javaParserFacade, allRelationships, true).visit(unit, null);
    }

    public void removeSelfLinks() {
        List<AbstractNode> toRemove = allRelationships.entrySet().stream()
                .filter(entry -> entry.getValue().contains(entry.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        for (AbstractNode node : toRemove) {
            allRelationships.get(node).remove(node);
        }
    }

    public void toDot(String filename, Function<String, String> pathProcessor, List<Set<AbstractNode>> grouping) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("digraph \"extractedGraph\"{\n");

            Set<AbstractNode> allNodes = allRelationships.keySet().stream()
                    .flatMap(key -> Stream.concat(Stream.of(key), allRelationships.get(key).stream()))
                    .collect(Collectors.toSet());

            Map<AbstractNode, Integer> nodeIds = new HashMap<>();
            int nodeId = 0;

            int clusterId = 0;
            for (Set<AbstractNode> group : grouping) {
                clusterId++;
                writer.write("subgraph cluster_" + clusterId + " {style=filled; color = lightgrey; node[style = filled, shape=box; color = white];\n");
                for (AbstractNode node : group) {
                    if (allNodes.remove(node)) {
                        writer.write("n" + nodeId + " [label=\"" + node.toDotString() + "\"];\n");
                        nodeIds.put(node, nodeId++);
                    }
                }
                writer.write("}\n");
            }

            for (Map.Entry<AbstractNode, Set<AbstractNode>> entry : allRelationships.entrySet()) {
                int fromId = nodeIds.get(entry.getKey());
                for (AbstractNode toNode : entry.getValue()) {
                    if (nodeIds.containsKey(toNode)) {
                        writer.write("n" + fromId + "->n" + nodeIds.get(toNode) + ";\n");
                    }
                }
            }
            writer.write("}\n");
        }
    }

    public void toJson(String filename) throws IOException {
        Set<AbstractNode> allNodes = new HashSet<>(allRelationships.keySet());
        allRelationships.values().forEach(allNodes::addAll);

        Map<AbstractNode, Integer> nodeToId = new HashMap<>();
        List<Map<String, String>> nodesList = new ArrayList<>();
        int id = 0;

        for (AbstractNode node : allNodes) {
            nodeToId.put(node, id++);
            nodesList.add(nodeAsJsonInfo(node));
        }

        List<List<Integer>> relationsList = new ArrayList<>(Collections.nCopies(nodeToId.size(), null));
        for (Map.Entry<AbstractNode, Set<AbstractNode>> entry : allRelationships.entrySet()) {
            int fromId = nodeToId.get(entry.getKey());
            relationsList.set(fromId, entry.getValue().stream()
                    .map(nodeToId::get)
                    .collect(Collectors.toList()));
        }

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("nodes", nodesList);
        jsonMap.put("relations", relationsList);

        try (FileWriter writer = new FileWriter(filename)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(jsonMap, writer);
        }
    }

    private Map<String, String> nodeAsJsonInfo(AbstractNode node) {
        Map<String, String> info = new HashMap<>();
        info.put("Location", node.getLocation());
        if (node instanceof LiteralSymbol) {
            info.put("value", node.toString());
            info.put("kind", "const");
        } else if (node instanceof MethodReturnSymbol) {
            info.put("name", node.getName());
            info.put("kind", "methodReturn");
            try {
                info.put("type", node.getType());
            }catch (Exception e) {
                info.put("type", "Unknown");
            }
            info.put("symbolKind", ((MethodReturnSymbol) node).isConstructor() ? "constructor" : "method");
        } else if (node instanceof VariableSymbol) {
            info.put("name", node.getName());
            info.put("kind", "variable");
            try{
            info.put("type", node.getType());
            info.put("symbolKind", node.getType());
            }catch(Exception e) {
                info.put("type", "Unknown");
                info.put("symbolKind", "Unknown");
            }
        }
        return info;
    }

    private static String dotEscape(String input) {
        return input.replace("\"", "''").replace('\r', ' ').replace('\n', ' ').replace("\\", "\\\\").replace('^', ' ');
    }
}
