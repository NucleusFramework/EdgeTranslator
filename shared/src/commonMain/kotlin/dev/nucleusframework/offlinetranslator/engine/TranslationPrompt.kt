package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.Languages
import offlinetranslator.shared.generated.resources.Res

data class TranslationPrompt(val system: String, val user: String, val extras: List<String> = emptyList()) {
    fun restore(output: String): String = restoreBmpSafe(output, extras)
}

fun buildAudioPrompt(request: TranslationRequest): TranslationPrompt {
    val source = if (Languages.isAuto(request.sourceLang)) "its original language" else languageName(request.sourceLang)
    val target = languageName(request.targetLang)
    val same = !Languages.isAuto(request.sourceLang) && request.sourceLang == request.targetLang
    val user = if (same) {
        """
        Transcribe the following speech segment in $target into $target text.
        Follow these specific instructions for formatting the answer:
        *   Only output the transcription, with no newlines.
        *   When transcribing numbers, write the digits, i.e. write 1.7 and not one point seven, and write 3 instead of three.
        """.trimIndent()
    } else {
        """
        Transcribe the following speech segment in $source, then translate it into $target.
        When formatting the answer, first output the transcription in $source, then one newline, then output the string '$target: ', then the translation in $target.
        """.trimIndent()
    }
    return TranslationPrompt(
        system = "You transcribe and translate speech. Follow the output format exactly.",
        user = user,
    )
}

fun buildImagePrompt(request: TranslationRequest): TranslationPrompt {
    val source = if (Languages.isAuto(request.sourceLang)) "its original language" else languageName(request.sourceLang)
    val target = languageName(request.targetLang)
    val same = !Languages.isAuto(request.sourceLang) && request.sourceLang == request.targetLang
    val user = if (same) {
        """
        Read every piece of text in the image and write it out in $target.
        Only output the text itself, keeping its original line breaks, with no commentary.
        """.trimIndent()
    } else {
        """
        Read every piece of text in the image, written in $source, then translate it into $target.
        When formatting the answer, first output the text you read keeping its line breaks, then one newline, then the string '$target: ', then the translation in $target.
        Only output text, with no commentary.
        """.trimIndent()
    }
    return TranslationPrompt(
        system = "You read the text in images and translate it. Follow the output format exactly.",
        user = user,
    )
}

// ponytail: pas de repli sur le premier saut de ligne comme l'audio — un OCR est multi-ligne,
// sans le marqueur c'est que le modèle n'a rendu que le texte lu (même langue source et cible).
fun parseImageOutput(raw: String, targetName: String): Pair<String, String> {
    val text = cleanModelOutput(raw)
    val idx = text.indexOf("$targetName:", ignoreCase = true)
    if (idx < 0) return text to text
    val src = text.substring(0, idx).trim()
    val tgt = text.substring(idx + targetName.length + 1).trim()
    return src to tgt.ifBlank { src }
}

fun parseSpeechOutput(raw: String, targetName: String): Pair<String, String> {
    val text = cleanModelOutput(raw)
    val marker = "$targetName:"
    val idx = text.indexOf(marker, ignoreCase = true)
    if (idx >= 0) {
        val src = text.substring(0, idx).trim()
        val tgt = text.substring(idx + marker.length).trim()
        return src to tgt.ifBlank { src }
    }
    val nl = text.indexOf('\n')
    if (nl >= 0) return text.substring(0, nl).trim() to text.substring(nl + 1).trim()
    return text to text
}

suspend fun buildTranslationPrompt(request: TranslationRequest): TranslationPrompt {
    if (request.mode == TranslationMode.Proofread) return buildProofreadPrompt(request)
    val source = if (Languages.isAuto(request.sourceLang)) "any language" else languageName(request.sourceLang)
    val target = languageName(request.targetLang)
    val safe = toBmpSafe(request.text)
    val system = Res.readBytes("files/translate_prompt.txt").decodeToString()
        .replace("{source}", source)
        .replace("{target}", target)
        .replace("{placeholder_rule}", placeholderRule(safe))
        .trim()
    return TranslationPrompt(system = system, user = safe.text, extras = safe.extras)
}

// ponytail: pas de sélecteur de langue — le modèle corrige dans la langue du texte qu'on lui donne.
suspend fun buildProofreadPrompt(request: TranslationRequest): TranslationPrompt {
    val safe = toBmpSafe(request.text)
    val system = Res.readBytes("files/proofread_prompt.txt").decodeToString()
        .replace("{placeholder_rule}", placeholderRule(safe))
        .trim()
    return TranslationPrompt(system = system, user = safe.text, extras = safe.extras)
}

private fun placeholderRule(safe: BmpSafeText): String =
    if (safe.extras.isEmpty()) "" else "\n- Copy tokens like [[#0]] unchanged; they mark original symbols."

fun cleanModelOutput(raw: String): String {
    var text = raw.trim()
    text = text.replace(Regex("(?s)<\\|think\\|>.*?(<\\|/?think\\|>|$)"), "")
    text = text.replace(Regex("<\\|/?[^>]+\\|>"), "")
    text = text.replace(Regex("(?s)```(?:\\w+)?\\s*(.*?)```"), "$1")
    text = text.trim().trim('"').trim('«', '»').trim()
    return text
}

internal data class BmpSafeText(val text: String, val extras: List<String>)

// ponytail: LiteRT JNI uses modified UTF-8; nlohmann then aborts on non-BMP (emoji).
internal fun toBmpSafe(text: String): BmpSafeText {
    if (text.none { it.isSurrogate() }) return BmpSafeText(text, emptyList())
    val extras = ArrayList<String>()
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val end = when {
            c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> i + 2

            c.isSurrogate() -> i + 1

            else -> {
                out.append(c)
                i++
                continue
            }
        }
        extras.add(text.substring(i, end))
        out.append(bmpPlaceholder(extras.lastIndex))
        i = end
    }
    return BmpSafeText(out.toString(), extras)
}

internal fun restoreBmpSafe(text: String, extras: List<String>): String {
    var result = text
    for (i in extras.indices.reversed()) {
        result = result.replace(bmpPlaceholder(i), extras[i])
    }
    return result
}

private fun bmpPlaceholder(index: Int): String = "[[#$index]]"

private fun languageName(code: String): String = Languages.get(code)?.nameEn ?: code
