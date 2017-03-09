package org.mapleir.deobimpl2;

import org.mapleir.deobimpl2.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

public class MethodRenamerPass implements IPass {

	@Override
	public boolean isSingletonPass() {
		return false;
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		ApplicationClassSource source = cxt.getApplication();
		
		Map<MethodNode, String> remapped = new HashMap<>();

		int totalMethods = 0;
		
		for(ClassNode cn : source.iterate()) {
			totalMethods += cn.methods.size();
		}
		
		int i = RenamingUtil.computeMinimum(totalMethods);
		
		for(ClassNode cn : source.iterate()) {
			for(MethodNode m : cn.methods) {
				if(remapped.containsKey(m)) {
					continue;
				}
				
				if(Modifier.isStatic(m.access)) {
					if(!m.name.equals("<clinit>")) {
						String newName = RenamingUtil.createName(i++);
						remapped.put(m, newName);
					}
				} else {
					if(!m.name.equals("<init>")) {
						// Set<ClassNode> classes = source.getStructures().dfsTree(m.owner, true, true, true);
						// Set<MethodNode> methods = getVirtualMethods(cxt, classes, m.name, m.desc);
						Set<MethodNode> methods = getHierarchyMethodChain(cxt, m.owner, m.name, m.desc);
						if(canRename(cxt, methods)) {
							String newName = RenamingUtil.createName(i++);
							
							for(MethodNode o : methods) {
								if(remapped.containsKey(o)) {
									throw new IllegalStateException(String.format("m: %s, o: %s, ms: %s, remap[o]: %s", m, o, methods, remapped.get(o)));
								}
								remapped.put(o, newName);
							}
						} else {
							System.out.println("  can't rename: " + methods);
						}
					}
				}
			}
		}
		
		rename(cxt, remapped, true);
		System.out.printf("  Remapped %d/%d methods.%n", remapped.size(), totalMethods);
		
		return remapped.size();
	}
	
	public static void rename(IContext cxt, Map<MethodNode, String> remapped, boolean warn) {
		ApplicationClassSource source = cxt.getApplication();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(ClassNode cn : source.iterate()) {
			{
				if(cn.outerMethod != null) {
//					ClassNode owner = tree.getClass(cn.outerClass);
					System.out.println("Outer: " + cn.outerClass + "." + cn.outerMethod + " " + cn.outerMethodDesc);
					cn.outerClass = null;
					cn.outerMethod = null;
					cn.outerMethodDesc = null;
					//					System.out.println(owner.name);
//					do {
//						for(MethodNode m : owner.methods) {
//							System.out.println(m);
//							if(m.name.equals(cn.outerMethod) && m.desc.equals(cn.outerMethodDesc)) {
//								System.out.println("m: " + m);
//							}
//						}
//						owner = tree.getClass(owner.superName);
//						System.out.println(cn.superName);
//						System.out.println(owner);
//					} while(owner != null);
				}
			}
			
			Set<Expr> visited = new HashSet<>();
			
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getCFGS().getIR(m);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							
							if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpr invoke = (InvocationExpr) e;
								
								if(visited.contains(invoke)) {
									throw new RuntimeException(invoke.toString());
								}
								visited.add(invoke);
								
								if(invoke.getOwner().startsWith("[")) {
									System.err.println("  ignore array object invoke: " + invoke + ", owner: " + invoke.getOwner());
									continue;
								}
								
								if(invoke.getInstanceExpression() == null) {
									MethodNode site = resolver.findStaticCall(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
									if(site != null) {
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(warn && mustMark(source, invoke.getOwner())) {
												System.err.println("  invalid site(s): " + invoke);
											}
										}
									} else {
										if(mustMark(source, invoke.getOwner())) {
											System.err.println("  can't resolve(s) " + invoke);
										}
									}
								} else {
//									 Set<MethodNode> sites = resolver.resolveVirtualCalls(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									// Set<ClassNode> classes = source.getStructures().dfsTree(cn, true, true, true);
									// Set<MethodNode> sites = getVirtualMethods(cxt, classes, invoke.getName(), invoke.getDesc());
									Set<MethodNode> sites = getHierarchyMethodChain(cxt, source.findClassNode(invoke.getOwner()), invoke.getName(), invoke.getDesc());
									if(sites.size() > 0) {
										/* all of the sites must be linked by the same name,
										 * so we can use any to find the new name. */
										
										boolean anyContains = false;
										boolean allContains = true;
										for(MethodNode s : sites) {
											anyContains |= remapped.containsKey(s);
											allContains &= remapped.containsKey(s);
										}
										
										if(anyContains && !allContains) {
											System.err.println("mismatch: ");
											// System.err.println(classes);
											System.err.println(sites);
											throw new RuntimeException();
										}
										
										MethodNode site = sites.iterator().next();
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(warn && !site.name.equals("<init>") && canRename(cxt, sites)) {
												System.err.println("  invalid site(v): " + invoke + ", " + sites);
											}
										}
									} else {
										if(mustMark(source, invoke.getOwner())) {
											System.err.println("  can't resolve(v) " + invoke + ", owner: " + invoke.getOwner() + " desc " + invoke.getDesc());
											// System.err.println("  classes: " + classes);
										}
									}
								}
							} else if(e.getOpcode() == Opcode.DYNAMIC_INVOKE) {
								throw new UnsupportedOperationException();
							}
						}
					}
				}
			}
		}
		
		/* Rename the methods after as we need to resolve
		 * them using the old names during the invocation 
		 * analysis above. */
		for(Entry<MethodNode, String> e : remapped.entrySet()) {
			System.out.println(e.getKey());
			e.getKey().name = e.getValue();
			System.out.println("  post: " + e.getKey());
		}
	}
	
	private static boolean mustMark(ApplicationClassSource tree, String owner) {
		ClassNode cn = tree.findClassNode(owner);
		return cn == null || !tree.isLibraryClass(owner);
	}
	
	private static boolean canRename(IContext cxt, Set<MethodNode> methods) {
		for(MethodNode m : methods) {
			if(cxt.getApplication().isLibraryClass(m.owner.name)) {
				/* inherited from runtime class */
				return false;
			}
		}
		return true;
	}
	
	public static MethodNode findClassMethod(ClassNode cn, String name, String desc) {
		MethodNode findM = null;
		
		for(MethodNode m : cn.methods) {
			if(!Modifier.isStatic(m.access)) {
				if(m.name.equals(name) && m.desc.equals(desc)) {
					
					if(findM != null) {
						throw new IllegalStateException(String.format("%s contains %s and %s", cn.name, findM, m));
					}
					
					findM = m;
				}
			}
		}
		
		return findM;
	}
	
	public static Set<MethodNode> getHierarchyMethodChain(IContext cxt, ClassNode cn, String name, String desc) {
		ApplicationClassSource app = cxt.getApplication();
		ClassTree structures = app.getStructures();
		
		// TODO: Use existing SimpleDFS code
		check: { for (ClassNode viable : structures.dfsTree(cn, true, true, true))
			if (viable.getMethod(name, desc, false) != null)
				break check;
			System.err.println("cn: " + cn);
			System.err.println("name: " + name);
			System.err.println("desc: " + desc);
			System.err.println("Searched: " + structures.dfsTree(cn, true, true, true));
			throw new IllegalArgumentException("You must be really dense because that method doesn't even exist.");
		}
		
		Set<ClassNode> visited = new HashSet<>();
		visited.addAll(structures.dfsTree(cn, false, true, true));
		visited.add(cn);
		// System.out.println("visited " + visited);
		
		Map<ClassNode, MethodNode> results = new HashMap<>();
		Queue<ClassNode> visitHeads = new LinkedList<>();
		for(ClassNode current : visited) {
			MethodNode m = findClassMethod(current, name, desc);
		    if(m != null) {
				results.put(current, m);
		        visitHeads.add(current);
		    }
		}
		visited.clear();
		visited.addAll(visitHeads);
		
//		System.out.println("visitHeads start with " + visitHeads);
		while(!visitHeads.isEmpty()) {
		    ClassNode current = visitHeads.remove();
		    
		    Set<String> directSupers = new HashSet<>();
		    directSupers.add(current.superName);
		    directSupers.addAll(current.interfaces);
		    directSupers.remove(null);
//		    System.out.println(directSupers);
		    for(String s : directSupers) {
				ClassNode parent = app.findClassNode(s);
				MethodNode m = findClassMethod(parent, name, desc);
				if(m != null) {
					results.remove(current);
					// System.out.println("  -" + current + "   \t" + results.keySet());
				}
				if (visited.add(parent)) {
					if (m != null) {
						if (results.containsKey(parent)) {
							throw new IllegalStateException();
						}
						results.put(parent, m);
						// System.out.println("  +" + parent + "   \t" + results.keySet());
					}
					visitHeads.add(parent);
				}
			}
		}
		// System.out.println("results " + results.keySet());

		Set<ClassNode> classes = new HashSet<>();
		for (ClassNode top : results.keySet()) {
		    classes.addAll(structures.dfsTree(top, false, true, true));
		}
		// System.out.println("classes " + classes);
		
		Set<MethodNode> methods = new HashSet<>();
		for(ClassNode c : classes) {
			if(results.containsKey(c)) {
				methods.add(results.get(c));
			} else {
				MethodNode m = findClassMethod(c, name, desc);
				if(m != null) {
					methods.add(m);
				}
			}
		}
		
		return methods;
	}
}