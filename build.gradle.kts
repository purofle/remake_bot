import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.github.purofle"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":tdlib"))

    implementation(libs.org.telegram.telegrambots.longpolling)
    implementation(libs.org.telegram.telegrambots.client)
    implementation(libs.io.github.oshai.kotlin.logging.jvm)
    implementation(libs.org.slf4j.slf4j.api)
    implementation(libs.com.squareup.okhttp3.okhttp3.coroutines)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    runtimeOnly(libs.org.apache.logging.log4j.log4j.slf4j2.impl)

    testImplementation(kotlin("test"))
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

application {
    mainClass.set("com.github.purofle.remakebot.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "get fat jar"
    archiveFileName.set("remake_bot.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }

    from(sourceSets.main.get().output)
    from(provider { configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    dependsOn(configurations.runtimeClasspath)

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
}