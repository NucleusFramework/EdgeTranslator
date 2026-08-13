import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.GemmaTranslator
import dev.nucleusframework.offlinetranslator.engine.TranslationMode
import dev.nucleusframework.offlinetranslator.engine.TranslationRequest
import dev.nucleusframework.offlinetranslator.engine.TranslationResult
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * One-shot: run the same [GemmaTranslator] proofread path as the app, then write
 * the raw model output into `proofread_result_placeholder` for every locale.
 */
fun main() = runBlocking {
    FileKit.init(appId = "EdgeTranslator")
    val root = Path.of("sharedUI/src/commonMain/composeResources")
    check(root.resolve("values/strings.xml").exists()) { "run from the repo root" }

    val jobs = Files.list(root)
        .use { stream -> stream.filter { it.isDirectory() && it.fileName.toString().startsWith("values") }.toList() }
        .mapNotNull { dir ->
            val file = dir.resolve("strings.xml")
            val raw = file.readText()
            val input = stringValue(raw, "proofread_placeholder") ?: return@mapNotNull null
            Triple(dir.fileName.toString(), file, unescapeXml(input))
        }
        .sortedBy { it.first }

    check(jobs.isNotEmpty()) { "no proofread_placeholder strings found" }

    val modelPath = GemmaModels.Precise.destPath().takeIf { Path.of(it).exists() }
        ?: GemmaModels.Fast.destPath()
    println("model=$modelPath")
    val translator = GemmaTranslator()
    try {
        translator.preload(modelPath)
        jobs.forEachIndexed { index, (locale, file, input) ->
            print("[${index + 1}/${jobs.size}] $locale … ")
            System.out.flush()
            val result = translator.translate(
                TranslationRequest(
                    text = input,
                    sourceLang = "auto",
                    targetLang = "auto",
                    modelPath = modelPath,
                    mode = TranslationMode.Proofread,
                ),
            )
            val output = when (result) {
                is TranslationResult.Ok -> result.text
                is TranslationResult.Error -> error("$locale: ${result.message}")
                TranslationResult.Unavailable -> error("$locale: model unavailable")
            }
            println("${result.let { (it as TranslationResult.Ok).latencyMs }}ms")
            println(output)
            println("---")
            writeResultPlaceholder(file, output)
        }
    } finally {
        translator.close()
    }
}

private val STRING_RE = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

private fun stringValue(xml: String, name: String): String? =
    STRING_RE.findAll(xml).firstOrNull { it.groupValues[1] == name }?.groupValues?.get(2)

private fun unescapeXml(value: String): String = value
    .replace("\\n", "\n")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("\\'", "'")

private fun escapeXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\n", "\\n")

private fun writeResultPlaceholder(file: Path, output: String) {
    val xml = file.readText()
    val line = """    <string name="proofread_result_placeholder">${escapeXml(output)}</string>"""
    val updated = if (xml.contains("""name="proofread_result_placeholder"""")) {
        xml.replace(Regex("""    <string name="proofread_result_placeholder">.*?</string>""", RegexOption.DOT_MATCHES_ALL), line)
    } else {
        xml.replace(
            Regex("""(    <string name="proofread_placeholder">.*?</string>)""", RegexOption.DOT_MATCHES_ALL),
            "$1\n$line",
        )
    }
    file.writeText(updated)
}
