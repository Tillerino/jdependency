package org.vafer.jdependency;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.vafer.jdependency.Dependencies.Thing;
import org.vafer.jdependency.Dependencies.Thing.NonStatic;
import org.vafer.jdependency.Dependencies.Thing.StaticMethod;

class DependencyDfsTest {
	public static class UsesTypeUtils {
		public void doSomething() {
			TypeUtils.getRawType(null, null);
		}
	}

	@Test
	void test() throws IOException {
		Clazzpath dp = new Clazzpath();
		dp.addJarClazzpathUnit(getClass().getResourceAsStream("/commons-lang3-3.12.0.jar"), null);
		dp.addClazzpathUnit(List.of(new Clazzpath.Resource("Util.class") {
			@Override
			InputStream getInputStream() throws IOException {
				return getClass().getResourceAsStream("/org/vafer/jdependency/DependencyDfsTest$UsesTypeUtils.class");
			}
		}), null, true);
		Collection<Thing> dependencies = new DependencyDfs(dp.dependencies, List.of(new NonStatic("org/vafer/jdependency/DependencyDfsTest$UsesTypeUtils"))).get();
		dependencies.stream().map(Thing::classNname).distinct().forEach(System.out::println);
	}
}