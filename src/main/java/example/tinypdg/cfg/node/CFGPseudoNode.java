package example.tinypdg.cfg.node;

import example.tinypdg.cfg.node.CFGPseudoNode.PseudoElement;
import example.tinypdg.pe.ProgramElementInfo;

public class CFGPseudoNode extends CFGNode<PseudoElement> {

	public static class PseudoElement extends ProgramElementInfo {
		PseudoElement() {
			super(0, 0);
		}
	}

	public CFGPseudoNode() {
		super(new PseudoElement());
	}
}
