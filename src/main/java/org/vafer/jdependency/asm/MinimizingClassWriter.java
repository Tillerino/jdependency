package org.vafer.jdependency.asm;

import org.objectweb.asm.*;
import org.vafer.jdependency.Dependencies.Thing;
import org.vafer.jdependency.Dependencies.Thing.StaticMethod;

import java.io.InputStream;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;

public class MinimizingClassWriter extends ClassVisitor {
	private final Set<Thing> scope;
	private String className;

	public MinimizingClassWriter(Set<Thing> scope) {
		super(DependenciesClassVisitor.API, new ClassWriter(0));
		this.scope = scope;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		if (!scope.contains(new Thing.Hull(name))) {
			return;
		}
		this.className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (className == null) {
			return null;
		}
		if ((access & Opcodes.ACC_STATIC) != 0 && name.equals("<init>")
				&& !scope.contains(new StaticMethod(this.className, name, descriptor))) {
			return null;
		}
		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	public void visitSource(String source, String debug) {
		if (className == null) {
			return ;
		}
		super.visitSource(source, debug);
	}

	@Override
	public ModuleVisitor visitModule(String name, int access, String version) {
		if (className == null) {
			return null;
		}
		return super.visitModule(name, access, version);
	}

	@Override
	public void visitNestHost(String nestHost) {
		if (className == null) {
			return ;
		}
		super.visitNestHost(nestHost);
	}

	@Override
	public void visitOuterClass(String owner, String name, String descriptor) {
		if (className == null) {
			return ;
		}
		super.visitOuterClass(owner, name, descriptor);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (className == null) {
			return null;
		}
		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		if (className == null) {
			return null;
		}
		return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public void visitAttribute(Attribute attribute) {
		if (className == null) {
			return ;
		}
		super.visitAttribute(attribute);
	}

	@Override
	public void visitNestMember(String nestMember) {
		if (className == null) {
			return ;
		}
		super.visitNestMember(nestMember);
	}

	@Override
	public void visitPermittedSubclass(String permittedSubclass) {
		if (className == null) {
			return ;
		}
		super.visitPermittedSubclass(permittedSubclass);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		if (className == null) {
			return ;
		}
		super.visitInnerClass(name, outerName, innerName, access);
	}

	@Override
	public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
		if (className == null) {
			return null;
		}
		return super.visitRecordComponent(name, descriptor, signature);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		if (className == null) {
			return null;
		}
		return super.visitField(access, name, descriptor, signature, value);
	}

	public static Consumer<InputStream> writeJar(JarOutputStream out, Set<Thing> scope) {
		return is -> {
			try {
				new ClassReader(is).accept(new MinimizingClassWriter(scope), 0);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}
}
