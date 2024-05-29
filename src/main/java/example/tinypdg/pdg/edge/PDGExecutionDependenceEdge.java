package example.tinypdg.pdg.edge;

import example.tinypdg.pdg.node.PDGNode;

public class PDGExecutionDependenceEdge extends PDGEdge {

	public PDGExecutionDependenceEdge(final PDGNode<?> fromNode,
			final PDGNode<?> toNode) {
		super(PDGEdge.TYPE.CALL, fromNode, toNode);
	}

	@Override
	public PDGEdge replaceFromNode(final PDGNode<?> fromNode) {
		assert null != fromNode : "\"fromNode\" is null.";
		return new PDGExecutionDependenceEdge(fromNode, this.toNode);
	}

	@Override
	public PDGEdge replaceToNode(final PDGNode<?> toNode) {
		assert null != fromNode : "\"toNode\" is null.";
		return new PDGExecutionDependenceEdge(this.fromNode, toNode);
	}

	@Override
	public String getDependenceString() {
		return "";
	}
}
