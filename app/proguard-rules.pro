# Release minification remains disabled in this phase, but keep ONNX Runtime classes so enabling
# R8 later cannot strip JNI-reached runtime types.
-keep class ai.onnxruntime.** { *; }
