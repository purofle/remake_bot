import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

group = "com.github.purofle.remakebot"
version = "1.0-SNAPSHOT"

dependencies {
    api(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    api(libs.org.slf4j.slf4j.api)
    api(files("../libs/tdlib-java.jar"))
}