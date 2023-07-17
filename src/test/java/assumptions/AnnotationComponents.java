package assumptions;

import org.assertj.core.api.Assertions;

import java.lang.annotation.Retention;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationComponents {
	static Set<String> initialized = new LinkedHashSet<>();

	@Retention(RUNTIME)
	@interface AnnotationWithClass {
		Class<?> value();
	}

	static class SomeClass {
		static {
			initialized.add("SomeClass");
		}
	}

	@AnnotationWithClass(SomeClass.class)
	static class AnnotatedClass {
		public static void main(String[] args) {
			assertThat(AnnotatedClass.class.getAnnotations())
				.hasOnlyOneElementSatisfying(a -> assertThat(a).isInstanceOfSatisfying(AnnotationWithClass.class, ann -> assertThat(ann.value().getSimpleName())
					.isEqualTo("SomeClass")));
			assertThat(initialized).isEmpty();
		}
	}
}
