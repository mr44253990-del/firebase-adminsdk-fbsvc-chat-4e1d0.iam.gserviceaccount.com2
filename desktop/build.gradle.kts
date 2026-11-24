import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.compose") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.ebchat"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.ebchat.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )

            packageName = "ConvoChat"
            packageVersion = "1.0.0"
            vendor = "Convo Chat"
            description = "Convo Chat desktop client for Windows and Linux"
            copyright = "© Convo Chat"

            windows {
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "9ab1a4e2-3d67-45f1-9e88-c7b1e512a701"
                menuGroup = "Convo Chat"
            }

            linux {
                packageName = "convochat"
            }

            modules("java.instrument", "java.management", "jdk.crypto.ec", "jdk.unsupported")

            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        }
    }
}
