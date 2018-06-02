package org.mapleir.ir.code.expr.invoke;

import org.mapleir.app.service.InvocationResolver;
import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.collections.CollectionUtils;
import org.mapleir.stdlib.collections.map.SetCreator;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

public abstract class InvocationExpr extends Invocation {

	public enum CallType {
		STATIC, SPECIAL, VIRTUAL, INTERFACE, DYNAMIC;
	}
	
	private CallType callType;
	private Expr[] args;
	private String owner;
	private String name;
	private String desc;

	public InvocationExpr(CallType callType, Expr[] args, String owner, String name, String desc) {
		super(callType == CallType.DYNAMIC ? DYNAMIC_INVOKE : INVOKE); // temporary hack
		
		this.callType = callType;
		this.args = args;
		this.owner = owner;
		this.name = name;
		this.desc = desc;
		
		for (int i = 0; i < args.length; i++) {
			overwrite(args[i], i);
		}
	}

	public final CallType getCallType() {
		return callType;
	}

	public void setCallType(CallType callType) {
		this.callType = callType;
	}

	@Override
	public final String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	@Override
	public final String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public final String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	protected Expr[] copyArgs() {
		Expr[] arguments = new Expr[args.length];
		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = args[i].copy();
		}
		return arguments;
	}

	/**
	 * The implementer MUST call copyArgs when passing args!!!
	 * @return a copy of this invocationExpr
	 */
	@Override
	abstract public InvocationExpr copy();
	
	@Override
	public Type getType() {
		return Type.getReturnType(desc);
	}

	@Override
	public void onChildUpdated(int index) {
		Expr argument = read(index);
		if (index < 0 || (index) >= args.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		
		args[index] = argument;
		overwrite(argument, index);
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.METHOD_INVOCATION;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (!isStatic()) {
			int memberAccessPriority = Precedence.MEMBER_ACCESS.ordinal();
			Expr instanceExpression = args[0];
			int instancePriority = instanceExpression.getPrecedence();
			if (instancePriority > memberAccessPriority) {
				printer.print('(');
			}
			instanceExpression.toString(printer);
			if (instancePriority > memberAccessPriority) {
				printer.print(')');
			}
		} else {
			printer.print(owner.replace('/', '.'));
		}
		printer.print('.');
		printer.print(name);
		printer.print('(');
		for (int i = isStatic() ? 0 : 1; i < args.length; i++) {
			args[i].toString(printer);
			if ((i + 1) < args.length) {
				printer.print(", ");
			}
		}
		printer.print(')');
	}
	
	protected abstract void generateCallCode(MethodVisitor visitor); 

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		Type[] argTypes = Type.getArgumentTypes(desc);
		if (!isStatic()) {
			Type[] bck = argTypes;
			argTypes = new Type[bck.length + 1];
			System.arraycopy(bck, 0, argTypes, 1, bck.length);
			argTypes[0] = Type.getType("L" + owner + ";");
		}
		
		for (int i = 0; i < args.length; i++) {
			args[i].toCode(visitor, cfg);
			if (TypeUtils.isPrimitive(args[i].getType())) {
				int[] cast = TypeUtils.getPrimitiveCastOpcodes(args[i].getType(), argTypes[i]);
				for (int a = 0; a < cast.length; a++) {
					visitor.visitInsn(cast[a]);
				}
			}
		}
		
		generateCallCode(visitor);
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof InvocationExpr) {
			InvocationExpr o = (InvocationExpr) s;
			if(callType != o.callType || !name.equals(o.name) || !owner.equals(o.owner) || !desc.equals(o.desc)) {
				return false;
			}
			if(args.length != o.args.length) {
				return false;
			}
			for(int i=0; i < args.length; i++) {
				if(!args[i].equivalent(o.args[i])) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public Expr getPhysicalReceiver() {
		if(isStatic()) {
			return null;
		} else {
			return args[0];
		}
	}

	@Override
	public Expr[] getParameterExprs() {
		int i = isStatic() ? 0 : 1;
		Expr[] exprs = new Expr[args.length - i];
		System.arraycopy(args, i, exprs, 0, exprs.length);
		return exprs;
	}

	@Override
	public Expr[] getArgumentExprs() {
		return args;
	}

	@Override
	public Set<MethodNode> resolveTargets(InvocationResolver res) {		
		String owner = getOwner();
		String name = getName();
		String desc = getDesc();
		
		if(isStatic()) {
			return CollectionUtils.asCollection(SetCreator.getInstance(), res.resolveStaticCall(owner, name, desc));
		} else {
			if(name.equals("<init>")) {
				return CollectionUtils.asCollection(SetCreator.getInstance(), res.resolveVirtualInitCall(owner, desc));
			} else {
				return res.resolveVirtualCalls(owner, name, desc, true);
			}
		}
	}
}
