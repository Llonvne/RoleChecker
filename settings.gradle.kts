plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "RoleChecker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include("role-checker-annotation")
include("role-checker-runtime")
include("role-checker-test")
include("role-checker-ksp")
