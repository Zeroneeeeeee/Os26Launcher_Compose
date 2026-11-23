// Top-level build file where you can add configuration options common to all sub-projects/modules.

// JC: nang KGP cua built-in Kotlin (AGP 9.3.3 mang san 2.2.10) len 2.4.10, vi thu vien kinh Backdrop 2.0.1 build bang Kotlin 2.4.10
// (Kotlin chi doc duoc metadata moi hon minh mot ban). Cach nang theo AGENTS.md muc Build va toolchain.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
}