package example.tinypdg.cfg.node;

import example.tinypdg.pe.StatementInfo;

abstract public class CFGJumpStatementNode extends CFGStatementNode {

	CFGJumpStatementNode(final StatementInfo jumpStatement) {
		super(jumpStatement);
	}
}
