package org.mapleir.stdlib.ir.transform.ssa;

import java.util.Arrays;
import java.util.HashSet;

import org.mapleir.ir.analysis.StatementGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.UninitialisedObjectExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.PopStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.transform.SSATransformer;
import org.objectweb.asm.Opcodes;

public class SSAInitialiserAggregator extends SSATransformer {

	private final StatementGraph graph;
	
	public SSAInitialiserAggregator(CodeBody code, SSALocalAccess localAccess, StatementGraph graph) {
		super(code, localAccess);
		this.graph = graph;
	}

	@Override
	public int run() {
		for(Statement stmt : new HashSet<>(code)) {
			if (stmt instanceof PopStatement) {
				PopStatement pop = (PopStatement) stmt;
				Expression expr = pop.getExpression();
				if (expr instanceof InvocationExpression) {
					InvocationExpression invoke = (InvocationExpression) expr;
					if (invoke.getOpcode() == Opcodes.INVOKESPECIAL && invoke.getName().equals("<init>")) {
						Expression inst = invoke.getInstanceExpression();
						if (inst instanceof VarExpression) {
							VarExpression var = (VarExpression) inst;
							VersionedLocal local = (VersionedLocal) var.getLocal();

							CopyVarStatement def = localAccess.defs.get(local);

							Expression rhs = def.getExpression();
							if (rhs instanceof UninitialisedObjectExpression) {
								// replace pop(x.<init>()) with x := new Klass();
								// remove x := new Klass;
								
								// here we are assuming that the new object
								// can't be used until it is initialised.
								UninitialisedObjectExpression obj = (UninitialisedObjectExpression) rhs;
								Expression[] args = invoke.getParameterArguments();
								Expression[] newArgs = Arrays.copyOf(args, args.length);
								InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
								// remove the old def
								// add a copy statement before the pop (x = newExpr)
								// remove the pop statement
								
								code.remove(def);
								graph.excavate(def);
								
								CopyVarStatement newCvs = new CopyVarStatement(var, newExpr);
								localAccess.defs.put(local, newCvs);
								localAccess.useCount.get(local).decrementAndGet();
								
								int index = code.indexOf(pop);
								code.add(index, newCvs);
								graph.replace(pop, newCvs);
								code.remove(pop);
							}
						} else if(inst instanceof UninitialisedObjectExpression) {
							// replace pop(new Klass.<init>(args)) with pop(new Klass(args))
							UninitialisedObjectExpression obj = (UninitialisedObjectExpression) inst;
							Expression[] args = invoke.getParameterArguments();
							Expression[] newArgs = Arrays.copyOf(args, args.length);
							InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
							// replace pop contents
							// no changes to defs or uses
							
							pop.setExpression(newExpr);
						} else {
							System.err.println(code);
							System.err.println("Stmt: " + stmt.getId() + ". " + stmt);
							System.err.println("Inst: " + inst);
							throw new RuntimeException("interesting1 " + inst.getClass());
						}
					}
				}
			}
		
		}
		return 0;
	}
}