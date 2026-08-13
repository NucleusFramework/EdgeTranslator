package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.platform.joinPath

data class PiperVoiceSpec(
    val id: String,
    val lang: String,
    val name: String,
    val quality: String,
    val relDir: String,
    val onnxBytes: Long,
    val jsonBytes: Long,
) {
    val fileName: String get() = "$id.onnx"
    val bytes: Long get() = onnxBytes + jsonBytes
    val displayName: String get() = name.replace("_", " ").split(" ").joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }

    fun url(file: String): String = "https://huggingface.co/rhasspy/piper-voices/resolve/main/$relDir/$file"

    fun destDir(): String = PiperVoices.dir()
    fun destOnnx(): String = joinPath(destDir(), fileName)
    fun destJson(): String = joinPath(destDir(), "$fileName.json")
    fun partialOnnx(): String = destOnnx() + ".partial"
    fun partialJson(): String = destJson() + ".partial"

    fun isOnDisk(): Boolean = Platform.fileSize(destOnnx()) >= 1_000_000L && Platform.fileSize(destJson()) >= 100L

    fun removeFromDisk() {
        Platform.delete(destOnnx())
        Platform.delete(destJson())
        Platform.delete(partialOnnx())
        Platform.delete(partialJson())
    }
}

object PiperVoices {
    private var migratedLegacy = false

    fun dir(): String = joinPath(GemmaModels.dir(), "voices")

    fun legacyDir(): String = joinPath(Platform.appDir(), "voices")

    fun migrateLegacy() {
        if (migratedLegacy) return
        migratedLegacy = true
        val dest = dir()
        val from = legacyDir()
        if (from == dest || !Platform.exists(from)) return
        Platform.mkdir(dest)
        catalog.forEach { spec ->
            val srcOnnx = joinPath(from, spec.fileName)
            val srcJson = joinPath(from, "${spec.fileName}.json")
            if (Platform.exists(srcOnnx) && Platform.fileSize(spec.destOnnx()) < 1_000_000L) {
                Platform.rename(srcOnnx, spec.destOnnx())
            }
            if (Platform.exists(srcJson) && Platform.fileSize(spec.destJson()) < 100L) {
                Platform.rename(srcJson, spec.destJson())
            }
        }
        Platform.deleteRecursively(from)
    }

    private val catalog = listOf(
        spec("fr", "fr_FR-siwis-medium", "siwis", "medium", "fr/fr_FR/siwis/medium", 63201294, 4875),
        spec("fr", "fr_FR-mls-medium", "mls", "medium", "fr/fr_FR/mls/medium", 76733750, 7036),
        spec("fr", "fr_FR-tom-medium", "tom", "medium", "fr/fr_FR/tom/medium", 63511038, 4959),
        spec("fr", "fr_FR-upmc-medium", "upmc", "medium", "fr/fr_FR/upmc/medium", 76733615, 4996),
        spec("en", "en_US-lessac-high", "lessac", "high", "en/en_US/lessac/high", 113895201, 4883),
        spec("en", "en_GB-cori-high", "cori", "high", "en/en_GB/cori/high", 114219352, 4963),
        spec("en", "en_US-libritts-high", "libritts", "high", "en/en_US/libritts/high", 136673811, 20163),
        spec("en", "en_US-ljspeech-high", "ljspeech", "high", "en/en_US/ljspeech/high", 114199011, 4970),
        spec("en", "en_US-ryan-high", "ryan", "high", "en/en_US/ryan/high", 120786792, 4166),
        spec("ar", "ar_JO-kareem-medium", "kareem", "medium", "ar/ar_JO/kareem/medium", 63201294, 5024),
        spec("bn", "bn_BD-google-medium", "google", "medium", "bn/bn_BD/google/medium", 76782515, 5494),
        spec("zh", "zh_CN-chaowen-medium", "chaowen", "medium", "zh/zh_CN/chaowen/medium", 63221984, 2927),
        spec("zh", "zh_CN-huayan-medium", "huayan", "medium", "zh/zh_CN/huayan/medium", 63201294, 4822),
        spec("zh", "zh_CN-xiao_ya-medium", "xiao_ya", "medium", "zh/zh_CN/xiao_ya/medium", 63221984, 2927),
        spec("cs", "cs_CZ-jirka-medium", "jirka", "medium", "cs/cs_CZ/jirka/medium", 63201294, 5025),
        spec("cs", "cs_CZ-kasandra-medium", "kasandra", "medium", "cs/cs_CZ/kasandra/medium", 63511038, 3232),
        spec("da", "da_DK-talesyntese-medium", "talesyntese", "medium", "da/da_DK/talesyntese/medium", 63201294, 4878),
        spec("nl", "nl_BE-nathalie-medium", "nathalie", "medium", "nl/nl_BE/nathalie/medium", 63201294, 4879),
        spec("nl", "nl_NL-alex-medium", "alex", "medium", "nl/nl_NL/alex/medium", 63531476, 4965),
        spec("nl", "nl_NL-mls-medium", "mls", "medium", "nl/nl_NL/mls/medium", 76584246, 5856),
        spec("nl", "nl_NL-pim-medium", "pim", "medium", "nl/nl_NL/pim/medium", 63516050, 5037),
        spec("nl", "nl_BE-rdh-medium", "rdh", "medium", "nl/nl_BE/rdh/medium", 63104526, 4159),
        spec("nl", "nl_NL-ronnie-medium", "ronnie", "medium", "nl/nl_NL/ronnie/medium", 62950044, 5040),
        spec("fi", "fi_FI-harri-medium", "harri", "medium", "fi/fi_FI/harri/medium", 63201294, 4873),
        spec("de", "de_DE-thorsten-high", "thorsten", "high", "de/de_DE/thorsten/high", 113895201, 4875),
        spec("el", "el_GR-joy-medium", "joy", "medium", "el/el_GR/joy/medium", 63516050, 7367),
        spec("el", "el_GR-rapunzelina-medium", "rapunzelina", "medium", "el/el_GR/rapunzelina/medium", 62950044, 4973),
        spec("he", "he_IL-saspeech-medium", "saspeech", "medium", "he/he_IL/saspeech/medium", 63221984, 5269),
        spec("hi", "hi_IN-pratham-medium", "pratham", "medium", "hi/hi_IN/pratham/medium", 63516050, 4970),
        spec("hi", "hi_IN-priyamvada-medium", "priyamvada", "medium", "hi/hi_IN/priyamvada/medium", 63516050, 4973),
        spec("hi", "hi_IN-rohan-medium", "rohan", "medium", "hi/hi_IN/rohan/medium", 62950044, 5041),
        spec("hu", "hu_HU-anna-medium", "anna", "medium", "hu/hu_HU/anna/medium", 63201294, 5018),
        spec("hu", "hu_HU-berta-medium", "berta", "medium", "hu/hu_HU/berta/medium", 63201294, 4961),
        spec("hu", "hu_HU-imre-medium", "imre", "medium", "hu/hu_HU/imre/medium", 63201294, 5019),
        spec("id", "id_ID-news_tts-medium", "news_tts", "medium", "id/id_ID/news_tts/medium", 62950044, 5050),
        spec("it", "it_IT-serena-high", "serena", "high", "it/it_IT/serena/high", 114199010, 4957),
        spec("ko", "ko_KR-kss-medium", "kss", "medium", "ko/ko_KR/kss/medium", 63221984, 5232),
        spec("no", "no_NO-talesyntese-medium", "talesyntese", "medium", "no/no_NO/talesyntese/medium", 63201294, 4880),
        spec("no", "no_NO-nvcc-medium", "nvcc", "medium", "no/no_NO/nvcc/medium", 76770227, 5397),
        spec("fa", "fa_IR-amir-medium", "amir", "medium", "fa/fa_IR/amir/medium", 63531379, 4958),
        spec("fa", "fa_IR-ganji-medium", "ganji", "medium", "fa/fa_IR/ganji/medium", 63516050, 4958),
        spec("fa", "fa_IR-ganji_adabi-medium", "ganji_adabi", "medium", "fa/fa_IR/ganji_adabi/medium", 63516050, 4964),
        spec("fa", "fa_IR-gyro-medium", "gyro", "medium", "fa/fa_IR/gyro/medium", 63122309, 7210),
        spec("fa", "fa_IR-reza_ibrahim-medium", "reza_ibrahim", "medium", "fa/fa_IR/reza_ibrahim/medium", 63511038, 4967),
        spec("pl", "pl_PL-bass-high", "bass", "high", "pl/pl_PL/bass/high", 114204024, 4954),
        spec("pt", "pt_BR-faber-medium", "faber", "medium", "pt/pt_BR/faber/medium", 63201294, 4855),
        spec("pt", "pt_BR-cadu-medium", "cadu", "medium", "pt/pt_BR/cadu/medium", 62950044, 5040),
        spec("pt", "pt_BR-jeff-medium", "jeff", "medium", "pt/pt_BR/jeff/medium", 62950044, 5041),
        spec("pt", "pt_PT-tugão-medium", "tugão", "medium", "pt/pt_PT/tugão/medium", 63201294, 5026),
        spec("ro", "ro_RO-mihai-medium", "mihai", "medium", "ro/ro_RO/mihai/medium", 63201294, 4877),
        spec("ru", "ru_RU-irina-medium", "irina", "medium", "ru/ru_RU/irina/medium", 63201294, 4765),
        spec("ru", "ru_RU-denis-medium", "denis", "medium", "ru/ru_RU/denis/medium", 63201294, 4823),
        spec("ru", "ru_RU-dmitri-medium", "dmitri", "medium", "ru/ru_RU/dmitri/medium", 63201294, 4824),
        spec("ru", "ru_RU-ruslan-medium", "ruslan", "medium", "ru/ru_RU/ruslan/medium", 63201294, 4882),
        spec("es", "es_AR-daniela-high", "daniela", "high", "es/es_AR/daniela/high", 114199011, 7248),
        spec("es", "es_MX-claude-high", "claude", "high", "es/es_MX/claude/high", 63122309, 4963),
        spec("sw", "sw_CD-lanfrica-medium", "lanfrica", "medium", "sw/sw_CD/lanfrica/medium", 63201294, 4905),
        spec("sv", "sv_SE-alma-medium", "alma", "medium", "sv/sv_SE/alma/medium", 63434611, 6059),
        spec("sv", "sv_SE-lisa-medium", "lisa", "medium", "sv/sv_SE/lisa/medium", 63511038, 7239),
        spec("sv", "sv_SE-nst-medium", "nst", "medium", "sv/sv_SE/nst/medium", 63104526, 4157),
        spec("te", "te_IN-maya-medium", "maya", "medium", "te/te_IN/maya/medium", 62950044, 5040),
        spec("te", "te_IN-padmavathi-medium", "padmavathi", "medium", "te/te_IN/padmavathi/medium", 63516050, 4974),
        spec("te", "te_IN-venkatesh-medium", "venkatesh", "medium", "te/te_IN/venkatesh/medium", 63516050, 4973),
        spec("tr", "tr_TR-dfki-medium", "dfki", "medium", "tr/tr_TR/dfki/medium", 63201294, 4960),
        spec("uk", "uk_UA-tetiana-high", "tetiana", "high", "uk/uk_UA/tetiana/high", 114204024, 4905),
        spec("uk", "uk_UA-mykyta-high", "mykyta", "high", "uk/uk_UA/mykyta/high", 114204024, 4904),
        spec("uk", "uk_UA-oleksa-high", "oleksa", "high", "uk/uk_UA/oleksa/high", 114204024, 4904),
        spec("vi", "vi_VN-vais1000-medium", "vais1000", "medium", "vi/vi_VN/vais1000/medium", 63201294, 4860),
    )

    private val byId = catalog.associateBy { it.id }
    private val byLang = catalog.groupBy { it.lang }

    val langs: List<String> = Languages.all.map { it.code }.filter { it in byLang }

    fun all(): List<PiperVoiceSpec> = catalog
    fun of(token: String): PiperVoiceSpec? = byId[token] ?: defaultFor(token)
    fun forLang(lang: String): List<PiperVoiceSpec> = byLang[lang].orEmpty()
    fun defaultFor(lang: String): PiperVoiceSpec? = byLang[lang]?.firstOrNull()
    fun installed(): Set<String> = catalog.filter { it.isOnDisk() }.map { it.lang }.toSet()
    fun installedIds(): Set<String> = catalog.filter { it.isOnDisk() }.map { it.id }.toSet()

    /**
     * Languages worth listing up front in Settings: in use, downloading, or already on disk.
     * Everything else stays behind the "add a voice" picker — the full catalog is 30 languages.
     */
    fun visibleLangs(active: Set<String>, busy: String?, installed: Set<String>): List<String> =
        langs.filter { it in active || it in installed || covers(busy, it) }

    fun covers(token: String?, lang: String): Boolean {
        if (token == null) return false
        if (token == lang) return true
        return of(token)?.lang == lang
    }

    fun defaultPicks(ui: UiLanguage): Set<String> = buildSet {
        defaultFor("en")?.id?.let(::add)
        defaultFor(ui.code)?.id?.let(::add)
    }

    fun defaultIds(): Set<String> = langs.mapNotNull { defaultFor(it)?.id }.toSet()

    private fun spec(lang: String, id: String, name: String, quality: String, dir: String, onnxBytes: Long, jsonBytes: Long) =
        PiperVoiceSpec(id, lang, name, quality, dir, onnxBytes, jsonBytes)
}
