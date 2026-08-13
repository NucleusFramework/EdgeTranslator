package dev.nucleusframework.offlinetranslator.domain

const val AUTO_LANG = "auto"

/**
 * 35-language catalog = CC3M-35L / PaliGemma.
 * [Language.audio] = Gemma 4 FLEURS ASR.
 * [Language.tts] = Piper voice exists on Hugging Face (rhasspy/piper-voices).
 */
object Languages {
    val all: List<Language> = listOf(
        Language("fr", "Français", "French", "Français", audio = true, tts = true),
        Language("en", "Anglais", "English", "English", audio = true, tts = true),
        Language("ar", "Arabe", "Arabic", "العربية", audio = true, tts = true),
        Language("bn", "Bengali", "Bengali", "বাংলা", tts = true),
        Language("zh", "Chinois", "Chinese", "中文", audio = true, tts = true),
        Language("hr", "Croate", "Croatian", "Hrvatski"),
        Language("cs", "Tchèque", "Czech", "Čeština", tts = true),
        Language("da", "Danois", "Danish", "Dansk", tts = true),
        Language("nl", "Néerlandais", "Dutch", "Nederlands", tts = true),
        Language("fil", "Filipino", "Filipino", "Filipino"),
        Language("fi", "Finnois", "Finnish", "Suomi", tts = true),
        Language("de", "Allemand", "German", "Deutsch", audio = true, tts = true),
        Language("el", "Grec", "Greek", "Ελληνικά", tts = true),
        Language("he", "Hébreu", "Hebrew", "עברית", tts = true),
        Language("hi", "Hindi", "Hindi", "हिन्दी", audio = true, tts = true),
        Language("hu", "Hongrois", "Hungarian", "Magyar", tts = true),
        Language("id", "Indonésien", "Indonesian", "Indonesia", tts = true),
        Language("it", "Italien", "Italian", "Italiano", audio = true, tts = true),
        Language("ja", "Japonais", "Japanese", "日本語", audio = true),
        Language("ko", "Coréen", "Korean", "한국어", audio = true, tts = true),
        Language("mi", "Maori", "Māori", "Māori"),
        Language("no", "Norvégien", "Norwegian", "Norsk", tts = true),
        Language("fa", "Persan", "Persian", "فارسی", tts = true),
        Language("pl", "Polonais", "Polish", "Polski", tts = true),
        Language("pt", "Portugais", "Portuguese", "Português", audio = true, tts = true),
        Language("ro", "Roumain", "Romanian", "Română", tts = true),
        Language("ru", "Russe", "Russian", "Русский", audio = true, tts = true),
        Language("es", "Espagnol", "Spanish", "Español", audio = true, tts = true),
        Language("sw", "Swahili", "Swahili", "Kiswahili", tts = true),
        Language("sv", "Suédois", "Swedish", "Svenska", tts = true),
        Language("te", "Télougou", "Telugu", "తెలుగు", tts = true),
        Language("th", "Thaï", "Thai", "ไทย"),
        Language("tr", "Turc", "Turkish", "Türkçe", tts = true),
        Language("uk", "Ukrainien", "Ukrainian", "Українська", tts = true),
        Language("vi", "Vietnamien", "Vietnamese", "Tiếng Việt", tts = true),
    )

    val audioCount: Int = all.count { it.audio }
    val ttsCount: Int = all.count { it.tts }

    private val byCode = all.associateBy { it.code }

    fun isAuto(code: String): Boolean = code == AUTO_LANG

    fun get(code: String): Language? = byCode[code]

    fun hasAudio(code: String): Boolean = isAuto(code) || get(code)?.audio == true

    fun hasTts(code: String): Boolean = get(code)?.tts == true

    fun label(
        code: String,
        ui: UiLanguage,
        style: LangNameStyle = LangNameStyle.System,
    ): String = if (isAuto(code)) "Auto" else byCode[code]?.label(ui, style) ?: code.uppercase()

    fun label(code: String, settings: UserSettings): String =
        label(code, settings.uiLanguage, settings.langNames)

    fun search(
        query: String,
        ui: UiLanguage,
        includeAuto: Boolean = false,
        style: LangNameStyle = LangNameStyle.System,
    ): List<Language> {
        val q = query.trim().lowercase()
        val found = if (q.isEmpty()) all else all.filter {
            it.code.contains(q) ||
                it.nameFr.lowercase().contains(q) ||
                it.nameEn.lowercase().contains(q) ||
                it.native.lowercase().contains(q) ||
                it.label(ui, style).lowercase().contains(q) ||
                (it.audio && q in AUDIO_QUERY) ||
                (it.tts && q in TTS_QUERY)
        }
        if (!includeAuto) return found
        val auto = Language(AUTO_LANG, "Auto", "Auto", "Auto", audio = true)
        val matchAuto = q.isEmpty() || AUTO_LANG.contains(q) || "auto".contains(q) || q in AUDIO_QUERY
        return if (matchAuto) listOf(auto) + found else found
    }
}

private val AUDIO_QUERY = setOf("audio", "mic", "voix")
private val TTS_QUERY = setOf("tts", "speaker", "haut-parleur", "voix")
