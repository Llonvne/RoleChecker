plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.roleCheckerRuntime)
    implementation(projects.roleCheckerAnnotation)
    ksp(projects.roleCheckerKsp)
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}