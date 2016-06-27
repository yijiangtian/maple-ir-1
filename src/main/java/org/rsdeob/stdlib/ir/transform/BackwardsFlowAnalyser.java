package org.rsdeob.stdlib.ir.transform;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

import java.util.Iterator;
import java.util.Set;

public abstract class BackwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataAnalyser<N, E, S> {

	public BackwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	@Override
	public void remove(N n) {
		super.remove(n);
		
		enqueue(n);
	}
	
	@Override
	public void enqueue(N n) {
		Set<E> edgeSet = graph.getEdges(n);
		if (edgeSet != null) {
			for (E e : edgeSet) {
				N dst = e.dst;
				if(!queue.contains(dst)) {
					queue.add(dst);
				}
			}
		}
	}
	
	@Override
	protected void init() {
		// since this is backwards analysis, we
		// set the initial flow states after the
		// exit points of the graph.
		
		// to increase efficiency, instead of
		// calling super.init(), we compute the
		// exits of the graph while inserting
		// the default flow states into our tables.
		
		for(N n : graph.vertices()) {
			queue.add(n);
			in.put(n, newState());
			out.put(n, newState());
			
			if(graph.getEdges(n).size() == 0) {
				out.put(n, newEntryState());
			}
		}
	}
	
//	@Override
//	public void removeImpl(N n) {
//		for(E e : graph.getReverseEdges(n)) {
//			N pred = e.src;
//			enqueue.add(pred);
//			updateImpl(pred);
//		}
//	}
	
	@Override
	public void updateImpl(N n) {
		replaceImpl(n, n);
	}
	
	@Override
	public void replaceImpl(N old, N n) {
		if(graph.getEdges(n).size() == 0) {
			in.put(n, newState());
			out.put(n, newEntryState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
			
			enqueue(old);
			enqueue(n);
		}
	}
	
	@Override
	public void processQueue() {		
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);

			// stored for checking if a change of state
			// happens during the analysis of this
			// instruction. (in because it's backwards).
			S oldIn = newState();
			S currentIn = in.get(n);
			copy(currentIn, oldIn);
			
			S currentOut = out.get(n);
			Set<E> succs = graph.getEdges(n);
			
			if(succs.size() == 1) {
				N succ = succs.iterator().next().dst;
				S succIn = in.get(succ);
				copy(succIn, currentOut);
			} else if(succs.size() > 1) {
				Iterator<E> it = succs.iterator();
				
				N firstSucc = it.next().dst;
				copy(in.get(firstSucc), currentOut);
				
				while(it.hasNext()) {
					S merging = in.get(it.next().dst);
					merge(currentOut, merging);
				}
			}
			
			apply(n, currentOut, currentIn);
			
			// if there was a change, enqueue the predecessors.
			if(!equals(currentIn, oldIn)) {
				for(E e : graph.getReverseEdges(n)) {
					queue.add(e.src);
				}
			}
		}
	}

	@Override
	protected abstract S newState();

	@Override
	protected abstract S newEntryState();

	@Override
	protected abstract void merge(S in1, S in2, S out);
	
	@Override
	protected abstract void copy(S src, S dst);
	
	@Override
	protected abstract boolean equals(S s1, S s2);
	
	@Override
	protected abstract void apply(N n, S in, S out);
}