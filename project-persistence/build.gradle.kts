plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-host"))
            implementation(project(":modules:midi-sequencer"))
            implementation(project(":modules:audio-effects"))
            implementation(project(":modules:sampler"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":core-host"))
            implementation(project(":modules:midi-sequencer"))
            implementation(project(":modules:audio-effects"))
            implementation(project(":modules:sampler"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
