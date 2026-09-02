# Dependency License Foundation

Step 1 uses Android platform APIs, AndroidX/Jetpack (Compose, Room, Media3, Lifecycle, Navigation, Activity, Core and Test), Hilt/Dagger, Kotlin/coroutines tooling, and JUnit test tooling.

Before production distribution, generate and review a resolved dependency/license report from the successful Gradle build, including transitive dependencies. No FFmpeg runtime, browser/WASM runtime, AI model, or AI inference dependency ships in Step 1; FFmpeg is used only inside CI to generate deterministic test MP4 fixtures.
