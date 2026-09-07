plugins { java }
repositories { google(); mavenCentral() }
dependencies {
    implementation("com.android.tools.build:gradle-api:9.4.0")
    implementation("org.ow2.asm:asm:9.7.1")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
