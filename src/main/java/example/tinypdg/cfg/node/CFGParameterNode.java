package example.tinypdg.cfg.node;

import example.tinypdg.pe.VariableInfo;

public class CFGParameterNode extends CFGNode<VariableInfo> {

	private CFGParameterNode(final VariableInfo variable) {
		super(variable);
	}
}
