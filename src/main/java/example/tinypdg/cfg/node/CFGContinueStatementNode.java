package example.tinypdg.cfg.node;

import example.tinypdg.pe.StatementInfo;

public class CFGContinueStatementNode extends CFGJumpStatementNode {

	public CFGContinueStatementNode(final StatementInfo continueStatement) {
		super(continueStatement);
	}
}
