

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Compose Multiplatform's desktop artifacts transitively pull androidx/Google
        // Maven coordinates even for the JVM-only target used by :ui-desktop.
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
    }
}

rootProject.name = "muzziknod"

include(
    ":core-host",
    ":reference-modules:oscillator",
    ":reference-modules:midi-generator",
    ":reference-modules:midi-logger",
    ":modules:midi-sequencer",
    ":modules:audio-effects",
    ":modules:sampler",
    ":ui-desktop",
)