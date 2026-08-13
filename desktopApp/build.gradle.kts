import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.GraalvmDistribution
import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.nucleus)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

java {
    // Also makes `run` launch on a JDK 25, instead of whatever JVM runs Gradle.
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(compose.desktop.currentOs)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(libs.nucleus.decorated.window.material3)
}

val releaseVersion =
    System.getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() && it.first().isDigit() }
        ?: "1.0.0"

val nativePackageVersion = releaseVersion.substringBefore("-")

nucleus.application {
    mainClass = "MainKt"

    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.ORACLE
        imageName = "OfflineTranslator"
        // -O3 and PGO only exist on Oracle GraalVM. Community would silently stay on -O2.
        toolchain {
            distribution = GraalvmDistribution.ORACLE
        }
        optimization = NativeImageOptimization.LEVEL_3
        // PGO: `runWithPgoInstrument` records graalvm/pgo/default.iprof, applied
        // automatically by every later build. Opt out with -Pnucleus.graalvm.pgo=off.
    }

    nativeDistributions {
        // Zip is the silent macOS updater payload; DMG stays the first-install image.
        targetFormats(TargetFormat.Dmg, TargetFormat.Zip, TargetFormat.Nsis, TargetFormat.Deb)
        packageName = "OfflineTranslator"
        packageVersion = releaseVersion
        cleanupNativeLibs = true
        compressionLevel = CompressionLevel.Ultra
        homepage = "https://github.com/kdroidFilter/OfflineTranslator"

        publish {
            github {
                enabled = true
                owner = "kdroidFilter"
                repo = "OfflineTranslator"
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }

        linux {
            iconFile.set(project.file("appIcons/LinuxIcon.png"))
            modules("jdk.security.auth")
            debPackageVersion = releaseVersion
        }
        windows {
            iconFile.set(project.file("appIcons/WindowsIcon.ico"))
            packageVersion = nativePackageVersion
            upgradeUuid = "8f3a2c1d-6b4e-4d90-a7c5-1e9f0b8d4a63"
        }
        macOS {
            iconFile.set(project.file("appIcons/MacosIcon.icns"))
            packageVersion = nativePackageVersion
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
