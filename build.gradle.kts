import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.0"
}

group = "com.github.rusticflare"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:3.1.0")
    implementation("com.sksamuel.scrimage:scrimage-core:4.0.17")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.time.ExperimentalTime"
}

tasks.withType<KotlinCompile>().configureEach {

}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.register<Copy>("packageForNpm") {
    dependsOn(tasks.build)
    from("$buildDir/libs/pixel-sorter.jar", "README.md", "package.json")
    into("$buildDir/npm")
}

tasks.build {
    finalizedBy("packageForNpm")
}