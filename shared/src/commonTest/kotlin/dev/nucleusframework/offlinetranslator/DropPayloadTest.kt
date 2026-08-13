package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.platform.DropChoice
import dev.nucleusframework.offlinetranslator.platform.resolveDrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DropPayloadTest {

    @Test
    fun imageFileWinsOverText() {
        val choice = resolveDrop("hello", listOf("/tmp/notes.txt", "/tmp/shot.PNG"))
        assertIs<DropChoice.ImagePath>(choice)
        assertEquals("/tmp/shot.PNG", choice.path)
    }

    @Test
    fun textFileIsUsedWhenNoImage() {
        val choice = resolveDrop("ignored", listOf("/tmp/notes.md"))
        assertIs<DropChoice.TextPath>(choice)
        assertEquals("/tmp/notes.md", choice.path)
    }

    @Test
    fun unsupportedFileDoesNotFallBackToItsPath() {
        val choice = resolveDrop("/tmp/report.pdf", listOf("/tmp/report.pdf"))
        assertEquals(DropChoice.Unsupported, choice)
    }

    @Test
    fun clipboardTextWhenNoFiles() {
        val choice = resolveDrop("  Bonjour  ", emptyList())
        assertIs<DropChoice.Clipboard>(choice)
        assertEquals("Bonjour", choice.text)
    }

    @Test
    fun linuxUriListIsAnImageFile() {
        val choice = resolveDrop("file:///tmp/shot.png\r\n", emptyList())
        assertIs<DropChoice.ImagePath>(choice)
        assertEquals("/tmp/shot.png", choice.path)
    }

    @Test
    fun linuxUriListDoesNotPasteThePath() {
        val choice = resolveDrop("file:///tmp/notes.md", emptyList())
        assertIs<DropChoice.TextPath>(choice)
        assertEquals("/tmp/notes.md", choice.path)
    }

    @Test
    fun fileUriDecodesSpaces() {
        val choice = resolveDrop("file:///tmp/my%20shot.PNG", emptyList())
        assertIs<DropChoice.ImagePath>(choice)
        assertEquals("/tmp/my shot.PNG", choice.path)
    }

    @Test
    fun emptyWhenNothingReadable() {
        assertEquals(DropChoice.Empty, resolveDrop("   ", emptyList()))
        assertEquals(DropChoice.Empty, resolveDrop(null, emptyList()))
    }
}
