import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.nucleus)
}

// GraalVM CE 25 native-image cannot load class file 70 (JDK 26). Pin to 17 with the rest of the project.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(compose.desktop.currentOs)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(libs.nucleus.decorated.window.material3)
}

nucleus.application {
    mainClass = "MainKt"

    graalvm.isEnabled.set(true)

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)
        packageName = "OfflineTranslator"
        packageVersion = "1.0.0"
        cleanupNativeLibs = true
        compressionLevel = CompressionLevel.Ultra

        linux {
            iconFile.set(project.file("appIcons/LinuxIcon.png"))
            modules("jdk.security.auth")
        }
        windows {
            iconFile.set(project.file("appIcons/WindowsIcon.ico"))
        }
        macOS {
            iconFile.set(project.file("appIcons/MacosIcon.icns"))
            bundleID = "dev.nucleusframework.offlinetranslator.desktopApp"
            infoPlist {
                extraKeysRawXml = """
                    <key>NSMicrophoneUsageDescription</key>
                    <string>Record speech to translate offline on this device.</string>
                """.trimIndent()
            }
        }
    }
}
