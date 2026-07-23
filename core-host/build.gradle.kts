plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            // Kotlin 2.3.21's JvmTarget enum tops out at JVM_25 — bytecode target is not
            // the same as runtime JVM. The app still runs on Java 26 (constitution's
            // runtime mandate); the compiled class file version doesn't need to be 26.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    sourceSets {
        // kotlin-test is exposed as `api` from commonMain (not just commonTest) so the
        // shared contract-compliance abstract test class in
        // contract/testkit/ModuleContractComplianceTests.kt can use kotlin.test
        // assertions, and reference modules that depend on core-host can subclass it in
        // their own commonTest. Kotlin/Gradle has no way for one module's test
        // source set to depend on another module's test source set directly.
        //
        // kotlin-test-junit5 is also needed here (not just on jvmTest): the plain
        // kotlin-test artifact only ships assertion helpers (assertTrue, fail, ...) —
        // the actual `kotlin.test.Test` annotation class is supplied by the JUnit5
        // binding artifact. Since this project has only the jvm() target active,
        // commonMain and jvmMain compile together, so commonMain needs it directly.
        commonMain.dependencies {
            api(libs.kotlin.test)
            api(libs.kotlin.test.junit5)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}