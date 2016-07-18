package org.rsdeob.stdlib.ir.stat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public abstract class Statement implements FastGraphVertex {
	
	public static int ID_COUNTER = 1;
	private final long id = ID_COUNTER++;
	
	private List<Statement> parents;
	private Statement[] children;
	private int ptr;

	public Statement() {
		parents = new ArrayList<>();
		children = new Statement[8];
		ptr = 0;
	}
	
	public int deepSize() {
		int size = 1;
		for (int i = 0; i < children.length; i++) 
			if (children[i] != null) 
				size += children[i].deepSize();
		return size;
	}
	
	public int size() {
		int size = 0;
		for (int i = 0; i < children.length; i++) 
			if (children[i] != null) 
				size++;
		return size;
	}

	protected boolean shouldExpand() {
		double max = children.length * 0.50;
		return (double) size() > max;
	}
	
	protected void expand() { 
		if (children.length >= Integer.MAX_VALUE)
			throw new UnsupportedOperationException();
		long len = children.length * 2;
		if (len > Integer.MAX_VALUE)
			len = Integer.MAX_VALUE;
		Statement[] newArray = new Statement[(int) len];
		System.arraycopy(children, 0, newArray, 0, children.length);
		children = newArray;
	}
	
	protected Statement writeAt(int index, Statement s) {
		Statement prev = children[index];
		children[index] = s;
		
		if(prev != null) {
			prev.parents.remove(this);
		}
		if(s != null) {
			if(!s.parents.contains(this)) {
				s.parents.add(this);
			}
		}
		
		return prev;
	}
	
	public Statement read() {
		if (children[ptr] == null)
			return null;
		return children[ptr++];
	}

	public Statement read(int newPtr) {
		if (newPtr < 0 || newPtr >= children.length || (newPtr > 0 && children[newPtr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("%s, ptr=%d, len=%d, addr=%d", this.getClass().getSimpleName(), ptr, children.length, newPtr));
		return children[newPtr];
	}

	public void write(Statement node) {
		if(shouldExpand()) {
			expand();
		}
		
		if (children[ptr] == null) {
			writeAt(ptr, node);
			onChildUpdated(ptr++);
		} else {
			List<Statement> writeable = new ArrayList<>();
			for (int i = ptr; i < children.length; i++) {
				if (children[i] != null) {
					writeable.add(children[i]);
					writeAt(i, null);
				}
			}
			writeAt(ptr, node);
			onChildUpdated(ptr);
			int writePtr = ++ptr;
			for (Statement n : writeable) {
				writeAt(writePtr, n);
				onChildUpdated(writePtr++);
			}
		}
	}

	public void delete() {
		delete(ptr);
	}

	public void delete(int _ptr) {
		if (_ptr < 0 || _ptr >= children.length || (_ptr > 0 && children[_ptr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, _ptr));
		if (children[_ptr] == null)
			throw new UnsupportedOperationException("No statement at " + _ptr);
		
		if ((_ptr + 1) < children.length && children[_ptr + 1] == null) {
			writeAt(_ptr, null);
			onChildUpdated(_ptr);
		} else {
			writeAt(_ptr, null);
			onChildUpdated(_ptr);
			for (int i = _ptr + 1; i < children.length; i++) {
				writeAt(i-1, children[i]);
				onChildUpdated(i - 1);
				writeAt(i, null);
				onChildUpdated(i);
			}
		}
	}

	public Statement overwrite(Statement node) {
		if(shouldExpand()) {
			expand();
		}
		
		if (children[ptr] != node) {
			Statement prev = writeAt(ptr, node);
			onChildUpdated(ptr);
			return prev;
		}
		
		return null;
	}

	public Statement overwrite(Statement node, int newPtr) {
		if(shouldExpand()) {
			expand();
		}
		
		if (newPtr < 0 || newPtr >= children.length || (newPtr > 0 && children[newPtr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, newPtr));
		
		if (children[newPtr] != node) {
			Statement prev = children[newPtr];
			writeAt(newPtr, node);
			onChildUpdated(newPtr);
			return prev;
		}
		
		return null;
	}

	public int indexOf(Statement s) {
		for (int i = 0; i < children.length; i++) {
			if (children[i] == s) {
				return i;
			}
		}
		return -1;
	}

	public List<Statement> getParents() {
		return parents;
	}
	
	public List<Statement> getChildren() {
		List<Statement> list = new ArrayList<>();
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				list.add(children[i]);
			}
		}
		return list;
	}

	public void setChildPointer(int _ptr) {
		if (_ptr < 0 || _ptr >= children.length || (_ptr > 0 && children[_ptr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, _ptr));
		ptr = _ptr;
	}

	public int getChildPointer() {
		return ptr;
	}
	
	public boolean changesIndentation() {
		return false;
	}

	public abstract void onChildUpdated(int ptr);
	
	public abstract void toString(TabbedStringWriter printer);
	
	public abstract void toCode(MethodVisitor visitor, CodeAnalytics analytics);
	
	public abstract boolean canChangeFlow();
	
	public abstract boolean canChangeLogic();
	
	public abstract boolean isAffectedBy(Statement stmt);
	
	@Override
	public String toString() {
		return print(this);
	}
	
	public void debugPrint() {
		System.out.println(getId() + ". " + toString() + " (class=" + getClass().getSimpleName() + ")");
	}
	
	public long _getId() {
		return id;
	}
	
	@Override
	public String getId() {
		return Long.toString(id);
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}
	
	public abstract Statement copy();
	
	public abstract boolean equivalent(Statement s);
	
	public static String print(Statement node) {
		TabbedStringWriter printer = new TabbedStringWriter();
//		if(node.parents.size() == 0) {
//			printer.print(node.getId() + ". ");
//		}
		node.toString(printer);
		return printer.toString();
	}
	
	/**
	 * Computes all statements the given statement contains and itself.
	 * @param stmt Statement to enumerate child statements for
	 * @return a set of all child statements of the statement as well as itself.
	 */
	public static Set<Statement> enumerate(Statement stmt) {
		Set<Statement> stmts = new HashSet<>();
		stmts.add(stmt);
		if(stmt instanceof PhiExpression) {
			throw new UnsupportedOperationException(stmt.toString());
		} else {
			new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement stmt) {
					stmts.add(stmt);
					if(stmt instanceof PhiExpression) {
						PhiExpression phi = (PhiExpression) stmt;
						for(Expression s : phi.getLocals().values()) {
							stmts.addAll(enumerate(s));
						}
					}
					return stmt;
				}
			}.visit();
		}
		return stmts;
	}
}