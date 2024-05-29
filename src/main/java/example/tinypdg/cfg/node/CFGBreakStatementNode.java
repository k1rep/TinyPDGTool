package example.tinypdg.cfg.node;

import example.tinypdg.pe.StatementInfo;

public class CFGBreakStatementNode extends CFGJumpStatementNode {

	public CFGBreakStatementNode(final StatementInfo breakStatement) {
		super(breakStatement);
	}
}
