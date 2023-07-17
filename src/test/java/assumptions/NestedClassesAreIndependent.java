package assumptions;

import org.assertj.core.api.Assertions;

import java.util.LinkedHashSet;
import java.util.Set;

public class NestedClassesAreIndependent {
	static Set<String> initialized = new LinkedHashSet<>();

	public static class Outer {
		static {
			initialized.add("Outer");
		}

		public static void main(String[] args) {
			System.out.println("Outer.main");
			System.out.println(initialized);
			Assertions.assertThat(initialized).containsExactly("Outer");
		}

		public static class Inner {
			static {
				initialized.add("Inner");
			}

			public static void main(String[] args) {
				System.out.println("Inner.main");
				System.out.println(initialized);
				Assertions.assertThat(initialized).containsExactly("Inner");
			}
		}
	}
}