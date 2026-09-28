import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "Zipper"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
