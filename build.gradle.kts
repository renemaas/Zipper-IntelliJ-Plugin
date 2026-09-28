plugins {
    java
    id("org.jetbrains.intellij.platform")
}

group = "com.renemaas.intellij.zipper"
version = "1.2.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2")
    }
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "251"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}
