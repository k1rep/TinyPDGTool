package example.pdgextractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Stream;

public class PdgExtractor {
    private static final Logger logger = Logger.getLogger(PdgExtractor.class.getName());
    private final CompilationUnit compilationUnit;
    private final CombinedTypeSolver typeSolver;
    private final DirectedGraph pdg = new DirectedGraph();

    public PdgExtractor(CompilationUnit compilationUnit, CombinedTypeSolver typeSolver) {
        this.compilationUnit = compilationUnit;
        this.typeSolver = typeSolver;
    }

    private List<SimpleEntry<BlockStmt, ResolvedMethodDeclaration>> allMethodDeclarationBodies() {
        List<SimpleEntry<BlockStmt, ResolvedMethodDeclaration>> methodBodies = new ArrayList<>();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        compilationUnit.findAll(MethodDeclaration.class).forEach(method -> {
            BlockStmt body = method.getBody().orElse(null);
            if (body != null) {
                ResolvedMethodDeclaration resolvedMethod = symbolSolver.resolveDeclaration(method, ResolvedMethodDeclaration.class);
                methodBodies.add(new SimpleEntry<>(body, resolvedMethod));
            }
        });
        compilationUnit.findAll(LambdaExpr.class).forEach(lambda -> {
            BlockStmt body = lambda.getBody().toBlockStmt().orElse(null);
            if (body != null) {
                ResolvedMethodDeclaration resolvedMethod = symbolSolver.resolveDeclaration(lambda, ResolvedMethodDeclaration.class);
                methodBodies.add(new SimpleEntry<>(body, resolvedMethod));
            }
        });
        return methodBodies;
    }

    public void extract() {
        JavaParserFacade javaParserFacade = JavaParserFacade.get(typeSolver);
        List<SimpleEntry<BlockStmt, ResolvedMethodDeclaration>> list = allMethodDeclarationBodies();
        for (SimpleEntry<BlockStmt, ResolvedMethodDeclaration> tuple : list) {
            new ControlFlowGraph(pdg, tuple.getValue()).addMethodDeclaration(tuple.getKey(), tuple.getValue());
            new MethodCallGraph(pdg, javaParserFacade).visit(tuple.getKey(), null);
        }
        for (SimpleEntry<BlockStmt, ResolvedMethodDeclaration> tuple : list) {
            new DataFlowGraph(pdg, javaParserFacade).addDataFlowEdges(tuple.getKey(), tuple.getValue(),null);
        }
    }

    private static String dotLineType(Map.Entry<String, Object> edgeType) {
        switch (edgeType.getKey()) {
            case "controlflow":
                return "solid, key=0";
            case "yield":
                return "bold, color=crimson, key=0";
            case "return":
                return "bold, color=blue, key=0";
            case "invoke":
                return "dotted, key=2";
            case "dataflow":
                return "dashed, color=darkseagreen4, key=1, label=\"" + edgeType.getValue() + "\"";
            default:
                throw new IllegalArgumentException("Unrecognized edge type: " + edgeType);
        }
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public DirectedGraph getDirectedGraph() {
        return pdg;
    }

    public static String dotLineSpan(Object node) {
        if (node instanceof MethodEntryNode) {
            return ((MethodEntryNode) node).toSpan();
        } else if (node instanceof MethodExitNode) {
            return ((MethodExitNode) node).toSpan();
        } else if (node instanceof UnkMethodEntryNode) {
            return ((UnkMethodEntryNode) node).toSpan();
        } else {
            return "";
        }
    }



    public void exportToDot(String filename) throws IOException {
        pdg.toDot(filename,
                Object::toString,
                PdgExtractor::dotLineSpan,
                PdgExtractor::dotLineType
                );
    }

    public static SimpleEntry<CompilationUnit, CombinedTypeSolver> compile(String projectDirectory, String targetFile) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(parserConfiguration);

        try(Stream<Path> paths = Files.walk(Paths.get(projectDirectory))){
            List<File> files  = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
            CompilationUnit targetCompilationUnit = null;
            for (File file : files) {
                if (file.getCanonicalPath().equals(targetFile)) {
                    targetCompilationUnit = parser.parse(file).getResult().orElse(null);
                    break;
                }
            }

            if (targetCompilationUnit == null) {
                throw new IOException("Target file not found in the project directory");
            }
            return new SimpleEntry<>(targetCompilationUnit, typeSolver);
        }catch (IOException e) {
            logger.severe("Error while reading project directory");
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage <projectFolder> <file>");
        } else {
            logger.info("Extracting PDG from " + args[1] + " in project " + args[0] + "...");
            SimpleEntry<CompilationUnit, CombinedTypeSolver> tuple = compile(args[0], args[1]);
            logger.info("Compilation successful");
            PdgExtractor pdgExtractor = new PdgExtractor(Objects.requireNonNull(tuple).getKey(), tuple.getValue());
            logger.info("Extracting PDG...");
            pdgExtractor.extract();
            logger.info("Exporting PDG to pdg.dot");
            pdgExtractor.exportToDot("pdg.dot");
            logger.info("PDG exported to pdg.dot");
            logger.info("Extracting type constraints...");
            JavaParserFacade javaParserFacade = JavaParserFacade.get(tuple.getValue());
            TypeConstraints typeConstraints = new TypeConstraints();
            logger.info("Collecting type constraints...");
            typeConstraints.collectForSingleFile(tuple.getKey(), javaParserFacade);
            logger.info("Exporting type constraints to nameflows.json");
            typeConstraints.toJson("nameflows.json");
            logger.info("Type constraints exported to nameflows.json");
        }
    }
}
