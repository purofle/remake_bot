plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

group = "com.github.purofle.remakebot"
version = "1.0-SNAPSHOT"

dependencies {
    api(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    api(libs.org.slf4j.slf4j.api)
    api(files("../libs/tdlib-java.jar"))
}