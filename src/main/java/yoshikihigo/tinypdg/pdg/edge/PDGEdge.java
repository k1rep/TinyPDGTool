package yoshikihigo.tinypdg.pdg.edge;

import yoshikihigo.tinypdg.pdg.node.PDGNode;

public abstract class PDGEdge implements Comparable<PDGEdge> {

	final public TYPE type;
	final public PDGNode<?> fromNode;
	final public PDGNode<?> toNode;

	protected PDGEdge(final TYPE type, final PDGNode<?> fromNode,
			final PDGNode<?> toNode) {
		assert null != type : "\"type\" is null";
		assert null != fromNode : "\"fromNode\" is null.";
		assert null != toNode : "\"toNode\" is null.";
		this.type = type;
		this.fromNode = fromNode;
		this.toNode = toNode;
	}

	public abstract PDGEdge replaceFromNode(PDGNode<?> fromNode);

	public abstract PDGEdge replaceToNode(PDGNode<?> toNode);

	public abstract String getDependenceString();

	@Override
	public boolean equals(final Object o) {

		if (null == o) {
			return false;
		}
		if (!(o instanceof PDGEdge)) {
			return false;
		}

		return 0 == this.compareTo((PDGEdge) o);
	}

	@Override
	public int hashCode() {
		return fromNode.core.id + toNode.core.id;
	}

	@Override
	final public int compareTo(final PDGEdge edge) {
		assert null != edge : "\"edge\" is null.";
		final int fromNodeOrder = this.fromNode.compareTo(edge.fromNode);
		final int toNodeOrder = this.toNode.compareTo(edge.toNode);
		if (0 != fromNodeOrder) {
			return fromNodeOrder;
		} else if (0 != toNodeOrder) {
			return toNodeOrder;
		} else {
			return this.type.toString().compareTo(edge.type.toString());
		}
	}

	public boolean connectedWith(final PDGEdge edge) {
		assert null != edge : "\"edge\" is null.";
		return (0 == this.fromNode.compareTo(edge.fromNode))
				|| (0 == this.fromNode.compareTo(edge.toNode))
				|| (0 == this.toNode.compareTo(edge.fromNode))
				|| (0 == this.toNode.compareTo(edge.toNode));
	}

	public void remove() {
		final boolean f = this.fromNode.removeForwardEdge(this);
		final boolean b = this.toNode.removeBackwardEdge(this);
		assert f : "invalid status.";
		assert b : "invalid status.";
	}

	public enum TYPE {
		CONTROL(0, "black", "solid") {
			@Override
			public String toString() {
				return "control";
			}
		}, DATA(1, "darkseagreen4", "dashed") {
			@Override
			public String toString() {
				return "data";
			}
		}, CALL(2, "black", "dotted") {
			@Override
			public String toString() {
				return "call";
			}
		}, NAME(3, "darkorchid", "bold") {
			@Override
			public String toString() {
				return "name";
			}
		}, EXIT(0, "blue", "bold") {
			@Override
			public String toString() {
				return "exit";
			}
		};

		private final int key;
		private final String color;
		private final String style;

		TYPE(int key, String color, String style) {
			this.key = key;
			this.color = color;
			this.style = style;
		}

		public String getStyle() {
			return style;
		}

		public String getColor() {
			return color;
		}

		public int getKey() {
			return key;
		}

		abstract public String toString();
	}
}
