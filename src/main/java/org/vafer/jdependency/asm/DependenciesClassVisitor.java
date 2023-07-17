/*
 * Copyright 2010-2023 The jdependency developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vafer.jdependency.asm;

import java.util.Optional;

import org.objectweb.asm.*;
import org.vafer.jdependency.Dependencies;
import org.vafer.jdependency.Dependencies.Thing;
import org.vafer.jdependency.Dependencies.Thing.Hull;
import org.vafer.jdependency.Dependencies.Thing.NonStatic;
import org.vafer.jdependency.Dependencies.Thing.StaticInit;
import org.vafer.jdependency.Dependencies.Thing.StaticMethod;

/**
 * Collects dependencies between classes.
 * We split everything into a static and a non-static world.
 *
 * <p>
 * The axiom is that, once an object has been instantiated, all methods can be called.
 * The intuition behind that is that
 * 1) reflection does a lot of unexpected things.
 * 2) analysing method calls with inheritance is insanely hard.
 */
public class DependenciesClassVisitor extends ClassVisitor {
    public static final int API = Opcodes.ASM9;
    private final Dependencies dependencies;
    private String name;
    private Hull hull;
    private StaticInit staticInit;
    private NonStatic nonStatic;

    public DependenciesClassVisitor(Dependencies dependencies) {
        super(API);
        this.dependencies = dependencies;
    }

    private void log(String formatString, Object... args) {
        // replace with something better
        System.out.printf(formatString + "%n", args);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        log("visit: %d %d %s %s %s %s", version, access, name, signature, superName, String.join(", ", interfaces));
        super.visit(version, access, name, signature, superName, interfaces);

        this.name = name;
        hull = new Hull(name);
        staticInit = new StaticInit(name);
        nonStatic = new NonStatic(name);

        dependencies.add(nonStatic, staticInit);
        dependencies.add(staticInit, hull);

        dependencies.addFullDependency(name, superName);
        for (String iface : interfaces) {
            dependencies.addFullDependency(name, iface);
        }
        // TODO handle signature
    }

    @Override public void visitSource(String source, String debug) { /* not relevant */ super.visitSource(source, debug); }

    @Override public ModuleVisitor visitModule(String name, int access, String version) { /* not relevant */ return super.visitModule(name, access, version); }

    @Override
    public void visitNestHost(String nestHost) {
        // TODO
        super.visitNestHost(nestHost);
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        log("visitOuterClass: %s %s %s", owner, name, descriptor);
        super.visitOuterClass(owner, name, descriptor);

        // does it matter for us if this is in a method?
        dependencies.addFullDependency(this.name, owner);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        log("visitAnnotation: %s %s", descriptor, visible);
        super.visitAnnotation(descriptor, visible);

        dependencies.add(hull, new Hull(Type.getType(descriptor).getInternalName()));

        return new MyAnnotationVisitor(hull);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        return new MyAnnotationVisitor(hull);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        // the fuck is this?
        super.visitAttribute(attribute);
    }

    @Override public void visitNestMember(String nestMember) { /* not relevant */ super.visitNestMember(nestMember); }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        // not important
        super.visitPermittedSubclass(permittedSubclass);
    }

    @Override public void visitInnerClass(String name, String outerName, String innerName, int access) { /* we handle the opposite */ super.visitInnerClass(name, outerName, innerName, access); }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        log("visitRecordComponent: %s %s %s", name, descriptor, signature);
        super.visitRecordComponent(name, descriptor, signature);
        interestingType(Type.getType(descriptor)).ifPresent(t -> dependencies.add(hull, new Hull(t.getInternalName())));
        // TODO handle signature
        return new RecordComponentVisitor(API) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                super.visitAnnotation(descriptor, visible);
                return new MyAnnotationVisitor(hull);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                return new MyAnnotationVisitor(hull);
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                super.visitAttribute(attribute);
                // what is this even?
            }
        };
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        log("visitField: %s %s %s %s", name, descriptor, signature, value);
        super.visitField(access, name, descriptor, signature, value);
        interestingType(Type.getType(descriptor)).ifPresent(t -> dependencies.add(hull, new Hull(t.getInternalName())));
        // TODO handle signature?
        return new FieldVisitor(API) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                super.visitAnnotation(descriptor, visible);
                return new MyAnnotationVisitor(hull);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                return new MyAnnotationVisitor(hull);
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                super.visitAttribute(attribute);
                // what is this even?
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        super.visitMethod(access, name, descriptor, signature, exceptions);

        boolean staticc = (access & Opcodes.ACC_STATIC) != 0;
        log("visitMethod: %s %d %s %s %s %s", staticc ? "static" : "", access, name, descriptor, signature, exceptions != null ? String.join(", ", exceptions) : "");
        Type type = Type.getMethodType(descriptor);

        // handle hull
        interestingType(type.getReturnType()).ifPresent(t -> dependencies.add(hull, new Hull(t.getInternalName())));
        for (Type t : type.getArgumentTypes()) {
            interestingType(t).ifPresent(t2 -> dependencies.add(hull, new Hull(t2.getInternalName())));
        }

        Thing source;
        if (staticc) {
            if (name.equals("<clinit>")) {
                source = staticInit;
            } else {
                source = new StaticMethod(hull.classNname(), name, descriptor);
                dependencies.add(source, staticInit);
            }
        } else {
            source = nonStatic;
        }

        return new MethodVisitor(API) {
            @Override public void visitParameter(String name, int access) { /* not relevant */ super.visitParameter(name, access); }

            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                super.visitAnnotationDefault();
                log("visitAnnotationDefault");
                return new MyAnnotationVisitor(hull);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                super.visitAnnotation(descriptor, visible);
                return new MyAnnotationVisitor(hull);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                return new MyAnnotationVisitor(hull);
            }

            @Override public void visitAnnotableParameterCount(int parameterCount, boolean visible) { /* not relevant */ super.visitAnnotableParameterCount(parameterCount, visible); }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                super.visitParameterAnnotation(parameter, descriptor, visible);
                return new MyAnnotationVisitor(hull);

            }

            @Override public void visitAttribute(Attribute attribute) { /* not relevant */ super.visitAttribute(attribute); }

            @Override public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) { /* not relevant */ super.visitFrame(type, numLocal, local, numStack, stack); }

            @Override
            public void visitInsn(int opcode) { /* not relevant */ super.visitInsn(opcode); }

            @Override public void visitIntInsn(int opcode, int operand) { /* not relevant */ super.visitIntInsn(opcode, operand); }

            @Override public void visitVarInsn(int opcode, int varIndex) { super.visitVarInsn(opcode, varIndex); }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                System.out.println("XXXXXXXXXXXXXXXX");
                log("visitTypeInsn: %d %s", opcode, type);
                // TODO
                super.visitTypeInsn(opcode, type);
            }

            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                boolean staticc = (access & Opcodes.ACC_STATIC) != 0;
                log("visitFieldInsn: %s %d %s %s %s", staticc ? "static" : "", opcode, owner, name, descriptor);
                super.visitFieldInsn(opcode, owner, name, descriptor);
                if (staticc && !owner.equals(hull.classNname())) {
                    dependencies.add(source, new StaticInit(owner));
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                log("visitMethodInsn: %d %s %s %s %s", opcode, owner, name, descriptor, isInterface);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                dependencies.add(source, (opcode & Opcodes.INVOKESTATIC) != 0 && !name.equals("<init>")
                  ? new StaticMethod(owner, name, descriptor)
                  : new NonStatic(owner));
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                log("visitInvokeDynamicInsn: %s %s %s %s", name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                // TODO
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }

            @Override public void visitJumpInsn(int opcode, Label label) { super.visitJumpInsn(opcode, label); }

            @Override public void visitLabel(Label label) { super.visitLabel(label); }

            @Override
            public void visitLdcInsn(Object value) {
                log("visitLdcInsn: %s", value);
                // TODO
                super.visitLdcInsn(value);
            }

            @Override public void visitIincInsn(int varIndex, int increment) { /* not relevant */ super.visitIincInsn(varIndex, increment); }

            @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { /* not relevant */ super.visitTableSwitchInsn(min, max, dflt, labels); }

            @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { /* not relevant */ super.visitLookupSwitchInsn(dflt, keys, labels); }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                log("visitMultiANewArrayInsn: %s %d", descriptor, numDimensions);
                // TODO
                super.visitMultiANewArrayInsn(descriptor, numDimensions);
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                log("visitInsnAnnotation: %d %s %s %s", typeRef, typePath, descriptor, visible);
                // TODO
                return super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                log("visitTryCatchBlock: %s %s %s %s", start, end, handler, type);
                // TODO
                super.visitTryCatchBlock(start, end, handler, type);
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                log("visitTryCatchAnnotation: %d %s %s %s", typeRef, typePath, descriptor, visible);
                // TODO
                return super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                log("visitLocalVariable: %s %s %s %s %s %d", name, descriptor, signature, start, end, index);
                // TODO?
                super.visitLocalVariable(name, descriptor, signature, start, end, index);
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
                log("visitLocalVariableAnnotation: %d %s %s %s %s %s %s", typeRef, typePath, start, end, index, descriptor, visible);
                return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
            }

            @Override public void visitLineNumber(int line, Label start) { /* not relevant */ super.visitLineNumber(line, start); }

            @Override public void visitMaxs(int maxStack, int maxLocals) { /* not relevant */ super.visitMaxs(maxStack, maxLocals); }
        };
    }

    Optional<Type> interestingType(Type t) {
        if (t.getSort() == Type.OBJECT) {
            return Optional.of(t);
        } else if (t.getSort() == Type.ARRAY) {
            return interestingType(t.getElementType());
        } else {
            return Optional.empty();
        }
    }

    class MyAnnotationVisitor extends AnnotationVisitor {
        private final Thing source;

        MyAnnotationVisitor(Thing source) {
            super(API);
            this.source = source;
        }

        @Override
        public void visit(String name, Object value) {
            log("AnnotationVisitor -> visit: %s %s", name, value);
            super.visit(name, value);
            if(value instanceof Type t) {
                interestingType(t).ifPresent(t2 -> dependencies.add(source, new StaticInit(t2.getInternalName())));
            }
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            log("AnnotationVisitor -> visitEnum: %s %s %s", name, descriptor, value);
            super.visitEnum(name, descriptor, value);
            dependencies.add(source, new StaticInit(Type.getType(descriptor).getInternalName()));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            log("AnnotationVisitor -> visitAnnotation: %s %s", name, descriptor);
            return this;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            log("AnnotationVisitor -> visitArray: %s", name);
            return this;
        }
    }
}
