package example.tinypdg.graphviz;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import example.tinypdg.ast.TinyPDGASTVisitor;
import example.tinypdg.cfg.CFG;
import example.tinypdg.cfg.edge.CFGEdge;
import example.tinypdg.cfg.node.CFGControlNode;
import example.tinypdg.cfg.node.CFGNode;
import example.tinypdg.cfg.node.CFGNodeFactory;
import example.tinypdg.pdg.PDG;
import example.tinypdg.pdg.edge.PDGEdge;
import example.tinypdg.pdg.node.PDGNode;
import example.tinypdg.pdg.node.PDGNodeFactory;
import example.tinypdg.pe.MethodInfo;
import example.tinypdg.pe.ProgramElementInfo;

public class Writer {

	public static void main(String[] args) {

		try {

			final Options options = new Options();

			{
				final Option d = new Option("d", "directory", true,
						"target directory");
				d.setArgName("directory");
				d.setArgs(1);
				d.setRequired(true);
				options.addOption(d);
			}

			{
				final Option c = new Option("c", "ControlFlowGraph", true,
						"control flow graph");
				c.setArgName("file");
				c.setArgs(1);
				c.setRequired(false);
				options.addOption(c);
			}

			{
				final Option p = new Option("p", "ProgramDepencencyGraph",
						true, "program dependency graph");
				p.setArgName("file");
				p.setArgs(1);
				p.setRequired(false);
				options.addOption(p);
			}

			// {
			// final Option o = new Option("o", "optimize", true,
			// "remove unnecessary nodes from CFGs and PDGs");
			// o.setArgName("boolean");
			// o.setArgs(1);
			// o.setRequired(false);
			// options.addOption(o);
			// }

			// {
			// final Option a = new Option("a", "atomic", true,
			// "dissolve complicated statements into simple statements");
			// a.setArgName("boolean");
			// a.setArgs(1);
			// a.setRequired(false);
			// options.addOption(a);
			// }

			final CommandLineParser parser = new PosixParser();
			final CommandLine cmd = parser.parse(options, args);

			final File target = new File(cmd.getOptionValue("d"));
			if (!target.exists()) {
				System.err
						.println("specified directory or file does not exist.");
				System.exit(0);
			}

			final List<File> files = getFiles(target);
			final List<MethodInfo> methods = new ArrayList<MethodInfo>();
			for (final File file : files) {
				final CompilationUnit unit = TinyPDGASTVisitor.createAST(file);
				final List<MethodInfo> m = new ArrayList<MethodInfo>();
				final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(
						file.getAbsolutePath(), unit, methods);
				unit.accept(visitor);
				methods.addAll(m);
			}

			if (cmd.hasOption("c")) {
				System.out.println("building and outputing CFGs ...");
				final BufferedWriter writer = new BufferedWriter(
						new FileWriter(cmd.getOptionValue("c")));

				writer.write("digraph CFG {");
				writer.newLine();

				final CFGNodeFactory nodeFactory = new CFGNodeFactory();

				int createdGraphNumber = 0;
				for (final MethodInfo method : methods) {
					final CFG cfg = new CFG(method, nodeFactory);
					cfg.build();
					cfg.removeSwitchCases();
					cfg.removeJumpStatements();
					writeMethodCFG(cfg, createdGraphNumber++, writer);
				}

				writer.write("}");

				writer.close();
			}

			if (cmd.hasOption("p")) {
				System.out.println("building and outputing PDGs ...");
				final BufferedWriter writer = new BufferedWriter(
						new FileWriter(cmd.getOptionValue("p")));

				writer.write("digraph {");
				writer.newLine();

				int createdGraphNumber = 0;
				for (final MethodInfo method : methods) {

					final PDG pdg = new PDG(method, new PDGNodeFactory(),
							new CFGNodeFactory(), true, true, true);
					pdg.build();
					writePDG(pdg, createdGraphNumber++, writer);
				}

				edgeWriter.flush();
				// stringWriter写到这里
				writer.write(stringWriter.toString());


				writer.write("}");

				writer.close();
				edgeWriter.close();
			}

			System.out.println("successfully finished.");

		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(0);
		}
	}

	static private void writeMethodCFG(final CFG cfg,
			final int createdGraphNumber, final BufferedWriter writer)
			throws IOException {

		writer.write("subgraph cluster");
		writer.write(Integer.toString(createdGraphNumber));
		writer.write(" {");
		writer.newLine();

		writer.write("label = \"");
		writer.write(getMethodSignature((MethodInfo) cfg.core));
		writer.write("\";");
		writer.newLine();

		final SortedMap<CFGNode<? extends ProgramElementInfo>, Integer> nodeLabels = new TreeMap<CFGNode<? extends ProgramElementInfo>, Integer>();
		for (final CFGNode<?> node : cfg.getAllNodes()) {
			nodeLabels.put(node, nodeLabels.size());
		}

		for (final Map.Entry<CFGNode<? extends ProgramElementInfo>, Integer> entry : nodeLabels
				.entrySet()) {

			final CFGNode<? extends ProgramElementInfo> node = entry.getKey();
			final Integer label = entry.getValue();

			writer.write(Integer.toString(createdGraphNumber));
			writer.write(".");
			writer.write(Integer.toString(label));
			writer.write(" [style = filled, label = \"");
			writer.write(node.getText().replace("\"", "\\\"")
					.replace("\\\\\"", "\\\\\\\""));
			writer.write("\"");

			final CFGNode<? extends ProgramElementInfo> enterNode = cfg
					.getEnterNode();
			final SortedSet<CFGNode<? extends ProgramElementInfo>> exitNodes = cfg
					.getExitNodes();

			if (enterNode == node) {
				writer.write(", fillcolor = aquamarine");
			} else if (exitNodes.contains(node)) {
				writer.write(", fillcolor = deeppink");
			} else {
				writer.write(", fillcolor = white");
			}

			if (node instanceof CFGControlNode) {
				writer.write(", shape = diamond");
			} else {
				writer.write(", shape = ellipse");
			}

			writer.write("];");
			writer.newLine();
		}

		writeCFGEdges(cfg, nodeLabels, createdGraphNumber, writer);

		writer.write("}");
		writer.newLine();
	}

	static private void writeCFGEdges(
			final CFG cfg,
			final Map<CFGNode<? extends ProgramElementInfo>, Integer> nodeLabels,
			final int createdGraphNumber, final BufferedWriter writer)
			throws IOException {

		if (null == cfg) {
			return;
		}

		final SortedSet<CFGEdge> edges = new TreeSet<CFGEdge>();
		for (final CFGNode<?> node : cfg.getAllNodes()) {
			edges.addAll(node.getBackwardEdges());
			edges.addAll(node.getForwardEdges());
		}

		for (final CFGEdge edge : edges) {
			writer.write(Integer.toString(createdGraphNumber));
			writer.write(".");
			writer.write(Integer.toString(nodeLabels.get(edge.fromNode)));
			writer.write(" -> ");
			writer.write(Integer.toString(createdGraphNumber));
			writer.write(".");
			writer.write(Integer.toString(nodeLabels.get(edge.toNode)));
			writer.write(" [style = solid, label=\""
					+ edge.getDependenceString() + "\"];");
			writer.newLine();
		}
	}

	// 创建一个StringWriter
	static StringWriter stringWriter = new StringWriter();

	// 将StringWriter包装在BufferedWriter里面以提供缓冲的写入
	static BufferedWriter edgeWriter = new BufferedWriter(stringWriter);

	static private void writePDG(final PDG pdg, final int createdGraphNumber,
			final BufferedWriter writer) throws IOException {

		final MethodInfo method = pdg.unit;

		writer.write("subgraph cluster");
		writer.write(Integer.toString(createdGraphNumber));
		writer.write(" {");
		writer.newLine();

		writer.write("label = \"");
		writer.write(getMethodSignature(method));
		writer.write("\";");
		writer.newLine();
		// 在每个子图的开始添加入口节点和出口节点
		writer.write(String.format("%d.entry [label=\"Entry\", span=\"0-0\"];", createdGraphNumber));
		writer.newLine();



		final Map<PDGNode<?>, Integer> nodeLabels = new HashMap<PDGNode<?>, Integer>();
		for (final PDGNode<?> node : pdg.getAllNodes()) {
			nodeLabels.put(node, nodeLabels.size());
		}

		for (final Map.Entry<PDGNode<?>, Integer> entry : nodeLabels.entrySet()) {
			final PDGNode<?> node = entry.getKey();
			int label = entry.getValue();
			String nodeLabel = entry.getKey().getText().replace("\"", "\\\"").replace("\\\\\"", "\\\\\\\"");
			// 获取node的行号
			String nodeSpan;
			nodeSpan = node.core.startLine + "-" + node.core.endLine;
			writer.write(String.format("%d.%d [label=\"%s\", span=\"%s\"];", createdGraphNumber, label, nodeLabel, nodeSpan));
			writer.newLine();
		}
//		for (final Map.Entry<PDGNode<?>, Integer> entry : nodeLabels.entrySet()) {
//			writer.write(Integer.toString(createdGraphNumber));
//			writer.write(".");
//			writer.write(Integer.toString(entry.getValue()));
//			writer.write(" [style = filled, label = \"");
//			writer.write(entry.getKey().getText().replace("\"", "\\\"")
//					.replace("\\\\\"", "\\\\\\\""));
//			writer.write("\"");
//
//			final PDGNode<?> node = entry.getKey();
//			if (node instanceof PDGMethodEnterNode) {
//				writer.write(", fillcolor = aquamarine");
//			} else if (pdg.getExitNodes().contains(node)) {
//				writer.write(", fillcolor = deeppink");
//			} else if (node instanceof PDGParameterNode) {
//				writer.write(", fillcolor = tomato");
//			} else {
//				writer.write(", fillcolor = white");
//			}
//
//			if (node instanceof PDGControlNode) {
//				writer.write(", shape = diamond");
//			} else if (node instanceof PDGParameterNode) {
//				writer.write(", shape = box");
//			} else {
//				writer.write(", shape = ellipse");
//			}
//
//			writer.write("];");
//			writer.newLine();
//		}

		try {
			for (final PDGEdge edge : pdg.getAllEdges()) {
				edgeWriter.write(Integer.toString(createdGraphNumber));
				edgeWriter.write(".");
				edgeWriter.write(Integer.toString(nodeLabels.get(edge.fromNode)));
				edgeWriter.write(" -> ");
				edgeWriter.write(Integer.toString(createdGraphNumber));
				edgeWriter.write(".");
				edgeWriter.write(Integer.toString(nodeLabels.get(edge.toNode)));

				edgeWriter.write(String.format(" [key=%d, style=%s, color=%s, label=\"%s\"];", edge.type.getKey(), edge.type.getStyle(), edge.type.getColor(), edge.getDependenceString()));

				edgeWriter.newLine();
			}
			edgeWriter.flush();
		}catch (IOException e){
			e.printStackTrace();
		}
		writer.write(String.format("%d.exit [label=\"Exit\", span=\"0-0\"];", createdGraphNumber));
		writer.newLine();
		writer.write("}");
		writer.newLine();
	}

	static private List<File> getFiles(final File file) {

		final List<File> files = new ArrayList<File>();

		if (file.isFile() && file.getName().endsWith(".java")) {
			files.add(file);
		}

		else if (file.isDirectory()) {
			for (final File child : file.listFiles()) {
				final List<File> children = getFiles(child);
				files.addAll(children);
			}
		}

		return files;
	}

	static private String getMethodSignature(final MethodInfo method) {

		final StringBuilder text = new StringBuilder();

		text.append(method.name);
		text.append(" <");
		text.append(method.startLine);
		text.append("...");
		text.append(method.endLine);
		text.append(">");

		return text.toString();
	}
}
