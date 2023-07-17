package org.vafer.jdependency;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Dependencies {
	public Map<Thing, Set<Thing>> dependencies = new LinkedHashMap<>();

	public Dependencies add(Thing from, Thing to) {
		if (to.classNname().startsWith("java/") || to.classNname().startsWith("jdk/") || to.classNname().startsWith("sun/")) {
			return this;
		}
		System.out.println("add " + from + " -> " + to);
		dependencies.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
		return this;
	}

	public Dependencies addFullDependency(String from, String to) {
		return add(new Thing.Hull(from), new Thing.Hull(to))
				.add(new Thing.StaticInit(from), new Thing.StaticInit(to))
				.add(new Thing.NonStatic(from), new Thing.NonStatic(to));
	}

	public Set<Dependency> all() {
		return dependencies.entrySet().stream()
				.flatMap(e -> e.getValue().stream().map(v -> new Dependency(e.getKey(), v)))
				.collect(Collectors.toSet());
	}

	public sealed interface Thing {
		String classNname();

		record Hull(String classNname) implements Thing {
		}

		record StaticInit(String classNname) implements Thing {
		}

		record StaticMethod(String classNname, String methodName, String descriptor) implements Thing {
		}

		record NonStatic(String classNname) implements Thing {
		}
	}

	public record Dependency(Thing from, Thing to) {

	}
}
