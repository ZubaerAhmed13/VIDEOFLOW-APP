package com.videoflow.buildlogic;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Pinned Media3 1.11.0 lifecycle and causal-CFR corrections, verified again in APK bytecode. */
public abstract class Media3EglReleaseVisitor implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    private static final String WRAPPER = "androidx.media3.effect.FinalShaderProgramWrapper";
    private static final String COMPOSITOR = "androidx.media3.effect.DefaultVideoCompositor";
    private static final String PROVIDER = "androidx.media3.effect.MultipleInputVideoGraph$SingleContextGlObjectsProvider";

    @Override public boolean isInstrumentable(ClassData data) {
        String name = data.getClassName();
        return name.equals(WRAPPER) || name.equals(COMPOSITOR) || name.equals(PROVIDER);
    }

    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        String className = context.getCurrentClassData().getClassName();
        return new ClassVisitor(Opcodes.ASM9, next) {
            private int replacements;
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean wrapper = className.equals(WRAPPER) && name.equals("release") && descriptor.equals("()V");
                boolean compositor = className.equals(COMPOSITOR) && name.equals("getFramesToComposite");
                boolean provider = className.equals(PROVIDER) && name.equals("release") && descriptor.equals("(Landroid/opengl/EGLDisplay;)V");
                if (!wrapper && !compositor && !provider) return delegate;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                        if (compositor && owner.equals("java/lang/Math") && method.equals("abs") && desc.equals("(J)J")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/videoflow/app/render/CausalFrameSelection", "distanceUs", "(J)J", false);
                            replacements++;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                        if (wrapper && owner.equals("androidx/media3/common/util/GlUtil") && method.equals("destroyEglSurface")) {
                            // Remain inside the original GlException handling. Each processor owns
                            // its placeholder surface even when multiple processors share a context.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, "androidx/media3/effect/FinalShaderProgramWrapper", "eglDisplay", "Landroid/opengl/EGLDisplay;");
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, "androidx/media3/effect/FinalShaderProgramWrapper", "placeholderSurface", "Landroid/opengl/EGLSurface;");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, method, desc, isInterface);
                            replacements++;
                        }
                    }
                    @Override public void visitInsn(int opcode) {
                        if (provider && opcode == Opcodes.RETURN) {
                            // Context destruction alone leaves EGL thread-local state.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "android/opengl/EGL14", "eglReleaseThread", "()Z", false);
                            super.visitInsn(Opcodes.POP);
                            replacements++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            @Override public void visitEnd() {
                if (replacements != 1) throw new IllegalStateException("Pinned Media3 correction changed: " + className + " replacements=" + replacements);
                System.out.println("STEP5_MEDIA3_INSTRUMENTED " + className);
                super.visitEnd();
            }
        };
    }
}
