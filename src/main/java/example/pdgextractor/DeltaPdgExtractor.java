package example.pdgextractor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class DeltaPdgExtractor {
    private final Map<Node, ChangeType> changes;
    private final PdgExtractor beforePdg;
    private final PdgExtractor afterPdg;
    private final DirectedGraph deltaPdg;

    public DeltaPdgExtractor(PdgExtractor beforePdg, PdgExtractor afterPdg) {
        this.changes = new HashMap<>();
        this.beforePdg = beforePdg;
        this.afterPdg = afterPdg;
        this.deltaPdg = afterPdg.getDirectedGraph();
    }

    public void extract() {
        CompilationUnit beforeTree = beforePdg.getCompilationUnit();
        CompilationUnit afterTree = afterPdg.getCompilationUnit();
        List<Node> traversalBefore = getTraversal(beforeTree);
        List<Node> traversalAfter = getTraversal(afterTree);

        for (Node nodeAfter : traversalAfter) {
            System.out.println("Checking Node::\t" + nodeAfter.toString());
            boolean unchanged = false;
            for (Iterator<Node> iterator = traversalBefore.iterator(); iterator.hasNext(); ) {
                Node nodeBefore = iterator.next();
                if (nodeAfter.toString().equals(nodeBefore.toString())) {
                    changes.put(nodeBefore, ChangeType.UNCHANGED);
                    iterator.remove();
                    unchanged = true;
                    break;
                }
            }
            if (!unchanged) {
                changes.put(nodeAfter, ChangeType.INSERTION);
            }
        }
        for (Node nodeBefore : traversalBefore) {
            changes.put(nodeBefore, ChangeType.DELETION);
        }
    }

    private List<Node> getTraversal(CompilationUnit unit) {
        List<Node> traversal = new ArrayList<>();
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(unit);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            traversal.add(node);
            queue.addAll(node.getChildNodes());
        }
        return traversal;
    }

    private static String dotLineType(Map.Entry<String, Object> edgeType) {
        switch (edgeType.getKey()) {
            case "controlflow":
                return "solid";
            case "yield":
                return "bold, color=red";
            case "return":
                return "bold, color=blue";
            case "invoke":
                return "dotted";
            default:
                throw new IllegalArgumentException("Unrecognized edge type: " + edgeType);
        }
    }

    private Function<Object, String> generateNodeColourFunc() {
        return node -> {
            try {
                switch (changes.get((Node) node)) {
                    case UNCHANGED:
                        return "black";
                    case INSERTION:
                        return "green";
                    case DELETION:
                        return "red";
                    default:
                        throw new IllegalArgumentException("Unrecognized node type: " + node);
                }
            } catch (ClassCastException | NullPointerException e) {
                return "black";
            }
        };
    }

    public void exportToDot(String filename) throws IOException {
        deltaPdg.toDot(filename,
                Object::toString,
                n -> "",
                DeltaPdgExtractor::dotLineType,
                generateNodeColourFunc());
    }

    private enum ChangeType {
        UNCHANGED,
        INSERTION,
        DELETION
    }
}
