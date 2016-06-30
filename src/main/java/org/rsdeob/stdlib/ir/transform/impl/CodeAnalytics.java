package org.rsdeob.stdlib.ir.transform.impl;

import java.util.Set;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeAnalytics implements ICodeListener<Statement> {
	public final ControlFlowGraph cfg;
	public final StatementGraph sgraph;

	public final DefinitionAnalyser definitions;
	public final LivenessAnalyser liveness;
	public final UsesAnalyser uses;

	public CodeAnalytics(ControlFlowGraph cfg, StatementGraph sgraph, DefinitionAnalyser definitions, LivenessAnalyser liveness, UsesAnalyser uses) {
		this.cfg = cfg;
		this.sgraph = sgraph;
		this.definitions = definitions;
		this.liveness = liveness;
		this.uses = uses;
	}

	@Override
	public void update(Statement stmt) {
		definitions.update(stmt);
		liveness.update(stmt);
		definitions.commit();
		// update defs before uses.
		uses.update(stmt);
	}

	@Override
	public void replaced(Statement old, Statement n) {
		sgraph.replace(old, n);
		definitions.replaced(old, n);
		liveness.replaced(old, n);
		definitions.commit();
		uses.replaced(old, n);
	}

	@Override
	public void remove(Statement n) {
		definitions.remove(n);
		liveness.remove(n);
		uses.remove(n);
		Set<FlowEdge<Statement>> preds = sgraph.getReverseEdges(n);
		Set<FlowEdge<Statement>> succs = sgraph.getEdges(n);
		if (sgraph.excavate(n)) {
			for(FlowEdge<Statement> p : preds) {
				if(p.dst != n)
					definitions.appendQueue(p.dst);
			}
			for(FlowEdge<Statement> s : succs) {
				if(s.src != n)
					liveness.appendQueue(s.src);
			}
			definitions.commit();
			liveness.commit();
		}
	}

	@Override
	public void insert(Statement p, Statement s, Statement n) {
		
	}

	@Override
	public void commit() {
		definitions.commit();
		liveness.commit();
		uses.commit();
	}
}