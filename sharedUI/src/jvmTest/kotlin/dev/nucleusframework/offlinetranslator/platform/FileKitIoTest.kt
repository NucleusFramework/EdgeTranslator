package dev.nucleusframework.offlinetranslator.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileKitIoTest {

    @Test
    fun deleteRecursivelyRemovesNestedFiles() {
        val root = Files.createTempDirectory("ot-wipe-").toFile()
        val nested = root.resolve("models").resolve("voices")
        nested.mkdirs()
        nested.resolve("orphan.onnx").writeText("x")
        nested.resolve("orphan.onnx.json").writeText("y")
        root.resolve("models").resolve("keep-parent-marker.txt").writeText("z")

        assertTrue(filekitDeleteRecursively(root.resolve("models").absolutePath))
        assertFalse(root.resolve("models").exists())
        assertTrue(root.exists())
        root.delete()
    }
}
