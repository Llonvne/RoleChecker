plugins {
    kotlin("jvm")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.10-1.0.9")
    implementation(projects.roleCheckerRuntime)
    implementation("com.squareup:kotlinpoet-ksp:1.12.0")
    implementation(project(":role-checker-annotation"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}