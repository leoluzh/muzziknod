

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "muzziknod"

include(
    ":core-host",
    ":reference-modules:oscillator",
    ":reference-modules:midi-generator",
    ":reference-modules:midi-logger",
    ":modules:midi-sequencer",
)