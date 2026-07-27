plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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
            implementation(project(":reference-modules:oscillator"))
            implementation(project(":reference-modules:midi-generator"))
            implementation(project(":reference-modules:midi-logger"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(project(":core-host"))
            implementation(project(":modules:midi-sequencer"))
            implementation(project(":modules:audio-effects"))
            implementation("org.jetbrains.compose.ui:ui-test:${libs.versions.compose.multiplatform.get()}")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.muzziknod.ui.desktop.MainKt"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
