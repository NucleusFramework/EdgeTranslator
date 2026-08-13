import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.GraalvmDistribution
import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

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
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation(project(":shared"))
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
    // Nucleus `run` forks `javaHome`, not the Java plugin toolchain. Point it at JDK 25
    // so a JBR 21 Gradle daemon (typical from IDEA) does not launch class-file 69 bytecode.
    javaHome =
        javaToolchains
            .launcherFor(java.toolchain)
            .get()
            .metadata.installationPath.asFile.absolutePath

    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.ORACLE
        imageName = "EdgeTranslator"
        // -O3 and PGO only exist on Oracle GraalVM. Community would silently stay on -O2.
        toolchain {
            distribution = GraalvmDistribution.ORACLE
        }
        optimization = NativeImageOptimization.LEVEL_3
        // PGO: `runWithPgoInstrument` records graalvm/pgo/default.iprof, applied
        // automatically by every later build. Opt out with -Pnucleus.graalvm.pgo=off.
    }

    nativeDistributions {
        // SQLDelight JdbcSqliteDriver → DriverManager. jlink does not infer java.sql.
        modules("java.sql")
        // Zip is the silent macOS updater payload; DMG stays the first-install image.
        targetFormats(TargetFormat.Dmg, TargetFormat.Zip, TargetFormat.Nsis, TargetFormat.Deb)
        // https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#managing-resources
        appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        packageName = "Edge Translator"
        packageVersion = releaseVersion
        vendor = "NucleusFramework"
        cleanupNativeLibs = true
        compressionLevel = CompressionLevel.Ultra
        homepage = "https://github.com/NucleusFramework/EdgeTranslator"

        publish {
            github {
                enabled = true
                owner = "NucleusFramework"
                repo = "EdgeTranslator"
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }

        linux {
            iconFile.set(project.file("appIcons/LinuxIcon.png"))
            modules("jdk.security.auth")
            debPackageVersion = releaseVersion
            // electron-builder refuses .deb without a maintainer email.
            debMaintainer = "Elie Gambache <elyahou.hadass@gmail.com>"

            // GPG signing (deb) + passwordless self-update.
            // Keys: LinuxSigningSettings defaults from compose.desktop.linux.signing.*
            //   CI: LINUX_GPG_* secrets → root gradle.properties (release-desktop)
            //   Local: packaging/linux-signing.local.properties (gitignored) — see .example
            signing {
                enabled.set(true)
                silentUpdate.set(true)
                val localSigning = file("packaging/linux-signing.local.properties")
                if (localSigning.isFile) {
                    val props =
                        localSigning
                            .readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                            .associate { line ->
                                val i = line.indexOf('=')
                                line.substring(0, i).trim() to line.substring(i + 1).trim()
                            }

                    fun local(name: String): String? = props[name]?.takeIf { it.isNotEmpty() }
                    local("compose.desktop.linux.signing.keyId")?.let { keyId.set(it) }
                    local("compose.desktop.linux.signing.keyFile")?.let { keyFile.set(file(it)) }
                    local("compose.desktop.linux.signing.passphrase")?.let { passphrase.set(it) }
                }
            }
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

// LiteRT-LM pins this DXC drop for Windows GPU (WORKSPACE @directx_shader_compiler).
val dxcUrl = "https://github.com/microsoft/DirectXShaderCompiler/releases/download/v1.9.2602/dxc_2026_02_20.zip"
val windowsAppResources = project.layout.projectDirectory.dir("resources/windows-x64")
val linuxAppResources = project.layout.projectDirectory.dir("resources/linux-x64")

val resolveWindowsDxc = tasks.register("resolveWindowsDxc") {
    val url = dxcUrl
    val dest = windowsAppResources
    inputs.property("url", url)
    outputs.dir(dest)
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    doLast {
        val out = dest.asFile
        out.mkdirs()
        val dxil = out.resolve("dxil.dll")
        val compiler = out.resolve("dxcompiler.dll")
        if (dxil.isFile && compiler.isFile) return@doLast
        val zip = out.resolve("dxc.zip")
        URI.create(url).toURL().openStream().use { input ->
            zip.outputStream().use { input.copyTo(it) }
        }
        ZipFile(zip).use { zf ->
            val wanted = setOf("dxil.dll", "dxcompiler.dll")
            zf.entries().asSequence()
                .filter { !it.isDirectory && File(it.name).name in wanted && it.name.replace('\\', '/').contains("bin/x64/") }
                .forEach { entry ->
                    zf.getInputStream(entry).use { input ->
                        out.resolve(File(entry.name).name).outputStream().use { input.copyTo(it) }
                    }
                }
        }
        zip.delete()
        check(dxil.isFile && compiler.isFile) { "DXC zip missing bin/x64/dxil.dll or dxcompiler.dll" }
    }
}

val linuxSamplerStub = project.layout.projectDirectory.file("src/nativeLinux/litert_webgpu_sampler_stub.c")

val resolveLinuxGpuLibs = tasks.register("resolveLinuxGpuLibs") {
    val dest = linuxAppResources
    val stub = linuxSamplerStub
    inputs.file(stub)
    outputs.dir(dest)
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    doLast {
        val out = dest.asFile
        out.mkdirs()
        // LiteRT dlopens the unversioned name. Distros only ship libOpenCL.so.1.
        val openclSrc = listOf(
            "/usr/lib/x86_64-linux-gnu/libOpenCL.so.1",
            "/usr/lib/aarch64-linux-gnu/libOpenCL.so.1",
            "/usr/lib/libOpenCL.so.1",
        ).map(::File).firstOrNull { it.isFile }
        checkNotNull(openclSrc) { "libOpenCL.so.1 not found (ocl-icd-libopencl1)" }
        val opencl = out.resolve("libOpenCL.so")
        if (!opencl.isFile || opencl.length() < 1_024) {
            openclSrc.copyTo(opencl, overwrite = true)
        }
        fun patchelf(vararg args: String) {
            val code = ProcessBuilder("patchelf", *args).inheritIO().start().waitFor()
            check(code == 0) { "patchelf ${args.joinToString(" ")} failed ($code)" }
        }
        patchelf("--set-soname", "libOpenCL.so", opencl.absolutePath)
        patchelf("--set-rpath", "\$ORIGIN", opencl.absolutePath)

        // Real prebuilt sampler pulls a second Dawn. Stub Create returns
        // UNAVAILABLE so sampler_factory skips the static WebGPU sampler
        // (nvidia-gpucomp SIGILL) and uses CPU sampling on a GPU engine.
        val sampler = out.resolve("libLiteRtTopKWebGpuSampler.so")
        val gcc = ProcessBuilder(
            "gcc", "-shared", "-fPIC",
            "-Wl,-soname,libLiteRtTopKWebGpuSampler.so",
            "-o", sampler.absolutePath,
            stub.asFile.absolutePath,
        ).inheritIO().start().waitFor()
        check(gcc == 0 && sampler.isFile) { "gcc failed to build WebGPU sampler stub" }

        listOf("libwebgpu_dawn.so", "libLiteRt.so").forEach { out.resolve(it).delete() }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(resolveWindowsDxc, resolveLinuxGpuLibs)
}
