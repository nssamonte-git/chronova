// Top-level build file. Module settings live in the convention plugins (build-logic/convention);
// declaring the plugins here once puts them on one shared classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // verifyModuleGraph: fails on a dependency the three-layer architecture forbids.
    id("chronova.module.graph")
}
