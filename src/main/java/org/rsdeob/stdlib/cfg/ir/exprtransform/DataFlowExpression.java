package org.rsdeob.stdlib.cfg.ir.exprtransform;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class DataFlowExpression extends Expression {
	// We will define out lattice as such:
	//     T
	// constants
	//    _|_
	// Moving from top to bottom.

	/**
	 * Variable is undefined; control flow never reaches this expression
	 */
	public static final DataFlowExpression UNDEFINED = new DataFlowExpression();
	/**
	 * Variable is not a constant
	 */
	public static final DataFlowExpression NOT_A_CONST = new DataFlowExpression();

	private DataFlowExpression() {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(this == UNDEFINED ? "undef" : "NAC");
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		throw new UnsupportedOperationException("Top/Bottom Expression is for data flow use only");
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return false;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return false;
	}

	@Override
	public Expression copy() {
		return this;
	}

	@Override
	public Type getType() {
		throw new UnsupportedOperationException("Top/Bottom has no type");
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public int hashCode() {
		if (this == UNDEFINED)
			return -1;
		if (this == NOT_A_CONST)
			return -2;
		throw new IllegalStateException("Invalid DataFlowExpression with id " + getId());
	}
}