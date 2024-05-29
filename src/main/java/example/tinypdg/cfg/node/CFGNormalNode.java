package example.tinypdg.cfg.node;

import example.tinypdg.pe.ProgramElementInfo;

public class CFGNormalNode<T extends ProgramElementInfo> extends CFGNode<T> {

	public CFGNormalNode(final T element) {
		super(element);
	}
}
