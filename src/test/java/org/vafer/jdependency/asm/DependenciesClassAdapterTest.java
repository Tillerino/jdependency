package org.vafer.jdependency.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.vafer.jdependency.Dependencies;
import org.vafer.jdependency.Dependencies.Dependency;
import org.vafer.jdependency.Dependencies.Thing.Hull;
import org.vafer.jdependency.Dependencies.Thing.NonStatic;
import org.vafer.jdependency.Dependencies.Thing.StaticInit;
import org.vafer.jdependency.Dependencies.Thing.StaticMethod;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class DependenciesClassAdapterTest {
	private static final String prefix = "org/vafer/jdependency/asm/DependenciesClassAdapterTest$";

	Dependencies dependencies = new Dependencies();

	DependenciesClassAdapter adapter = new DependenciesClassAdapter(dependencies);

	static class EmptyClass {

	}

	@Test
	void emptyClass() throws IOException {
		visitClassFromClassPath(EmptyClass.class);
		assertThat(dependencies.all()).containsExactlyInAnyOrder(
			new Dependency(new NonStatic(prefix + "EmptyClass"), new StaticInit(prefix + "EmptyClass")),
			new Dependency(new StaticInit(prefix + "EmptyClass"), new Hull(prefix + "EmptyClass"))
		);
	}

	static class ClassWithStaticInit {
		static String x = stringInit();

		private static String stringInit() {
			return null;
		}
	}

	@Test
	void staticInit() throws IOException {
		visitClassFromClassPath(ClassWithStaticInit.class);
		assertThat(dependencies.all()).containsExactlyInAnyOrder(
			// always
			new Dependency(new NonStatic(prefix + "ClassWithStaticInit"), new StaticInit(prefix + "ClassWithStaticInit")),
			new Dependency(new StaticInit(prefix + "ClassWithStaticInit"), new Hull(prefix + "ClassWithStaticInit")),
			// added for every static method
			new Dependency(new StaticMethod(prefix + "ClassWithStaticInit", "stringInit", "()Ljava/lang/String;"), new StaticInit(prefix + "ClassWithStaticInit")),
			// because of method call
			new Dependency(new StaticInit(prefix + "ClassWithStaticInit"), new StaticMethod(prefix + "ClassWithStaticInit", "stringInit", "()Ljava/lang/String;"))
		);
	}

	static class ClassWithForeignStaticInit {
		static String x = UtilityClass.aString();
	}

	static class UtilityClass {
		static String aString() {
			return null;
		}
	}

	@Test
	void foreignStaticInit() throws IOException {
		visitClassFromClassPath(ClassWithForeignStaticInit.class);
		assertThat(dependencies.all()).containsExactlyInAnyOrder(
			// always
			new Dependency(new NonStatic(prefix + "ClassWithForeignStaticInit"), new StaticInit(prefix + "ClassWithForeignStaticInit")),
			new Dependency(new StaticInit(prefix + "ClassWithForeignStaticInit"), new Hull(prefix + "ClassWithForeignStaticInit")),
			// because of method call
			new Dependency(new StaticInit(prefix + "ClassWithForeignStaticInit"), new StaticMethod(prefix + "UtilityClass", "aString", "()Ljava/lang/String;"))
		);
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface SomeAnnotation {
		SomeEnum enumValue() default SomeEnum.INSTANCE;
	}

	static enum SomeEnum {
		INSTANCE;
	}

	@SomeAnnotation
	static class AnnotatedClass {

	}

	@Test
	void annotatedClass() throws IOException {
		visitClassFromClassPath(AnnotatedClass.class);
		assertThat(dependencies.all()).containsExactlyInAnyOrder(
			// always
			new Dependency(new NonStatic(prefix + "AnnotatedClass"), new StaticInit(prefix + "AnnotatedClass")),
			new Dependency(new StaticInit(prefix + "AnnotatedClass"), new Hull(prefix + "AnnotatedClass")),
			// because of annotation
			new Dependency(new Hull(prefix + "AnnotatedClass"), new Hull(prefix + "SomeAnnotation"))
		);
	}

	@SomeAnnotation(enumValue = SomeEnum.INSTANCE)
	static class AnnotatedClassWithValue {

	}

	@Test
	void annotatedClassWithValue() throws IOException {
		visitClassFromClassPath(AnnotatedClassWithValue.class);
		assertThat(dependencies.all()).containsExactlyInAnyOrder(
			// always
			new Dependency(new NonStatic(prefix + "AnnotatedClassWithValue"), new StaticInit(prefix + "AnnotatedClassWithValue")),
			new Dependency(new StaticInit(prefix + "AnnotatedClassWithValue"), new Hull(prefix + "AnnotatedClassWithValue")),
			// because of annotation
			new Dependency(new Hull(prefix + "AnnotatedClassWithValue"), new Hull(prefix + "SomeAnnotation")),
			new Dependency(new Hull(prefix + "AnnotatedClassWithValue"), new StaticInit(prefix + "SomeEnum"))
		);
	}

	@Test
	void annotationWithDefaultEnumValue() throws IOException {
		visitClassFromClassPath(SomeAnnotation.class);
		assertThat(dependencies.all()).containsExactlyInAnyOrder(
			// always
			new Dependency(new NonStatic(prefix + "SomeAnnotation"), new StaticInit(prefix + "SomeAnnotation")),
			new Dependency(new StaticInit(prefix + "SomeAnnotation"), new Hull(prefix + "SomeAnnotation")),
			// always
			new Dependency(new Hull(prefix + "SomeAnnotation"), new Hull(prefix + "SomeEnum")),
			// because of default value
			new Dependency(new Hull(prefix + "SomeAnnotation"), new StaticInit(prefix + "SomeEnum"))
		);
	}

	void visitClassFromClassPath(Class<?> clazz) throws IOException {
		new ClassReader(clazz.getName()).accept(adapter, 0);
	}
}