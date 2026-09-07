package com.videoflow.buildlogic;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Narrow cleanup correction for the pinned Media3 1.11.0 multi-input graph. */
public abstract class Media3EglReleaseVisitor implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    @Override public boolean isInstrumentable(ClassData data) {
        return data.getClassName().equals("androidx.media3.effect.MultipleInputVideoGraph$SingleContextGlObjectsProvider");
    }

    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            private boolean found;
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("release") || !descriptor.equals("(Landroid/opengl/EGLDisplay;)V")) return delegate;
                found = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            // The original method has already destroyed its owned context here.
                            // Release this GL worker's EGL TLS/driver connection, without terminating
                            // the process-wide display used by a concurrent editor preview.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "android/opengl/EGL14", "eglReleaseThread", "()Z", false);
                            super.visitInsn(Opcodes.POP);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            @Override public void visitEnd() {
                if (!found) throw new IllegalStateException("Pinned Media3 EGL cleanup method changed; review the compatibility correction.");
                super.visitEnd();
            }
        };
    }
}
