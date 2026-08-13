package dev.nucleusframework.offlinetranslator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class StringResourcesTest {

    @Test
    fun everyLocaleHasEveryDefaultKey() {
        val root = composeResources()
        val default = parse(root.resolve("values/strings.xml"))
        val locales = root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .sortedBy { it.name }
        assertTrue(locales.isNotEmpty(), "no locale folders under ${root.absolutePath}")

        val report = buildString {
            for (dir in locales) {
                val local = parse(dir.resolve("strings.xml"))
                val missingStrings = (default.strings - local.strings).sorted()
                val missingPlurals = (default.plurals - local.plurals).sorted()
                if (missingStrings.isEmpty() && missingPlurals.isEmpty()) continue
                append(dir.name)
                if (missingStrings.isNotEmpty()) {
                    append(" missing strings: ")
                    append(missingStrings.joinToString())
                }
                if (missingPlurals.isNotEmpty()) {
                    append(" missing plurals: ")
                    append(missingPlurals.joinToString())
                }
                appendLine()
            }
        }
        if (report.isNotEmpty()) {
            fail("Incomplete translations:\n$report")
        }
    }

    @Test
    fun placeholdersMatchDefault() {
        val root = composeResources()
        val default = File(root, "values/strings.xml").readText()
        val defaultPlaceholders = stringPlaceholders(default)
        val mismatches = mutableListOf<String>()
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .sortedBy { it.name }
            .forEach { dir ->
                val localPlaceholders = stringPlaceholders(dir.resolve("strings.xml").readText())
                for ((name, expected) in defaultPlaceholders) {
                    val actual = localPlaceholders[name] ?: continue
                    if (actual != expected) {
                        mismatches += "${dir.name}/$name expected $expected got $actual"
                    }
                }
            }
        if (mismatches.isNotEmpty()) {
            fail("Placeholder mismatch:\n${mismatches.joinToString("\n")}")
        }
    }

    private fun composeResources(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            cwd.resolve("src/commonMain/composeResources"),
            cwd.resolve("sharedUI/src/commonMain/composeResources"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("composeResources not found from ${cwd.absolutePath}")
    }

    private data class Catalog(val strings: Set<String>, val plurals: Set<String>)

    private fun parse(file: File): Catalog {
        val text = file.readText()
        return Catalog(
            strings = NAME_RE.findAll(text).map { it.groupValues[1] }.toSet(),
            plurals = PLURAL_RE.findAll(text).map { it.groupValues[1] }.toSet(),
        )
    }

    private fun stringPlaceholders(xml: String): Map<String, List<String>> = STRING_RE.findAll(xml).associate { match ->
        match.groupValues[1] to PLACEHOLDER_RE.findAll(match.groupValues[2]).map { it.value }.sorted().toList()
    }

    private companion object {
        val NAME_RE = Regex("""<string name="([^"]+)"""")
        val PLURAL_RE = Regex("""<plurals name="([^"]+)"""")
        val STRING_RE = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        val PLACEHOLDER_RE = Regex("""%(?:\d+\$)?\d*[sd]""")
    }
}
