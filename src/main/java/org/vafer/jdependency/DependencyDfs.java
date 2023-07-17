package org.vafer.jdependency;

import org.vafer.jdependency.Dependencies.Thing;

import java.util.*;

public class DependencyDfs {
	private final Dependencies dependencies;
	private final Set<Thing> queue = new LinkedHashSet<>();
	private final Set<Thing> all = new LinkedHashSet<>();

	public DependencyDfs(Dependencies dependencies, Collection<Thing> roots) {
		this.dependencies = dependencies;
		queue.addAll(roots);
		all.addAll(roots);
	}

	public Collection<Thing> get() {
		while (!queue.isEmpty()) {
			Iterator<Thing> iterator = queue.iterator();
			Thing thing = iterator.next();
			iterator.remove();

			for (Thing dep : dependencies.dependencies.getOrDefault(thing, Collections.emptySet())) {
				if (!all.contains(dep)) {
					queue.add(dep);
					all.add(dep);
				}
			}
		}
		return all;
	}
}
