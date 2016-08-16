package org.mapleir.ir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.mapleir.AnalyticsTest;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.ControlFlowGraphBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Test {

	public static void main(String[] args) throws IOException {
		ClassReader cr = new ClassReader(AnalyticsTest.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

			if(!m.toString().equals("org/mapleir/AnalyticsTest.test111()V")) {
				continue;
			}
			
			System.out.println("Processing " + m + "\n");
			
			long now = System.nanoTime();
			
			for(int i=0; i < 100; i++) {
				ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
			}
			
			long el = System.nanoTime() - now;
			el /= 100;
			
			System.out.println("el: " + (el / 1000) + "ms");
			
//			SSABlockLivenessAnalyser live = new SSABlockLivenessAnalyser(cfg);
//			live.compute();
//			
//			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config =
//					new BasicDotConfiguration<>(GraphType.DIRECTED);
//			
//			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = 
//					new DotWriter<>(config, cfg)
//					.add(new ControlFlowGraphDecorator().setFlags(ControlFlowGraphDecorator.OPT_DEEP))
//					.add(new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(live))
//					.setName("live-new");
//			writer.export();
		}
	}
}