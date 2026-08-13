package dev.nucleusframework.offlinetranslator.domain

enum class UiLanguage(val code: String, val rtl: Boolean = false) {
    Fr("fr"),
    En("en"),
    Ar("ar", rtl = true),
    Bn("bn"),
    Zh("zh"),
    Hr("hr"),
    Cs("cs"),
    Da("da"),
    Nl("nl"),
    Fil("fil"),
    Fi("fi"),
    De("de"),
    El("el"),
    He("he", rtl = true),
    Hi("hi"),
    Hu("hu"),
    Id("id"),
    It("it"),
    Ja("ja"),
    Ko("ko"),
    Mi("mi"),
    No("no"),
    Fa("fa", rtl = true),
    Pl("pl"),
    Pt("pt"),
    Ro("ro"),
    Ru("ru"),
    Es("es"),
    Sw("sw"),
    Sv("sv"),
    Te("te"),
    Th("th"),
    Tr("tr"),
    Uk("uk"),
    Vi("vi"),
    ;

    val commaDecimal: Boolean
        get() = code in COMMA_DECIMAL

    companion object {
        fun fromCode(code: String): UiLanguage = entries.firstOrNull { it.code == code } ?: Fr
    }
}

private val COMMA_DECIMAL = setOf(
    "fr", "de", "es", "it", "pt", "nl", "da", "sv", "no", "fi",
    "cs", "pl", "hu", "ro", "hr", "el", "tr", "ru", "uk", "id", "vi",
)

enum class ThemeMode { System, Light, Dark }

enum class LlmModel { Fast, Precise }

enum class HistoryFilter { All, Pinned, Last7Days }

enum class LangRole { Source, Target }

enum class LangNameStyle { System, Native }

data class Language(
    val code: String,
    val nameFr: String,
    val nameEn: String,
    val native: String,
    val audio: Boolean = false,
    val tts: Boolean = false,
) {
    fun label(ui: UiLanguage, style: LangNameStyle = LangNameStyle.System): String = when {
        style == LangNameStyle.Native -> native
        ui == UiLanguage.Fr -> nameFr
        else -> nameEn
    }
}

data class HistoryItem(
    val id: String,
    val createdAt: Long,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val targetText: String,
    val pinned: Boolean = false,
)

data class UserSettings(
    val uiLanguage: UiLanguage = UiLanguage.Fr,
    /** True while [uiLanguage] tracks the OS language instead of an explicit pick. */
    val uiLanguageAuto: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    val airplane: Boolean = true,
    val keepHistory: Boolean = true,
    val autoPurge: Boolean = false,
    val purgeAfterDays: Int = 90,
    val launchAtLogin: Boolean = true,
    val threads: Int = 8,
    val shortcut: String = "⌘⌃ T",
    val modelDir: String = "",
    val selectedModel: LlmModel = LlmModel.Fast,
    val langNames: LangNameStyle = LangNameStyle.System,
    val selectedVoices: Map<String, String> = emptyMap(),
)

data class ModelInfo(
    val id: LlmModel = LlmModel.Fast,
    val installed: Boolean = false,
    val installedAt: Long? = null,
    val sha256: String = "",
    val path: String = "",
    val lastChecked: Long? = null,
    val name: String = "Gemma 4 E2B IT",
    val version: String = "1.0",
    val quantization: String = "QAT 2/4/8-bit",
    val expectedBytes: Long = MODEL_BYTES,
)

enum class DownloadPhase {
    DiskCheck,
    Connect,
    Transfer,
    Verify,
    Index,
    Done,
    Cancelled,
    Failed,
    ;

    fun progressedPast(other: DownloadPhase): Boolean {
        if (this == Cancelled || this == Failed) return false
        if (this == Done) return true
        return ordinal > other.ordinal
    }
}

sealed interface DownloadError {
    data object Airplane : DownloadError
    data class DiskFull(val freeBytes: Long) : DownloadError
    data object Interrupted : DownloadError
    data class HttpDenied(val status: Int) : DownloadError
    data object NotFound : DownloadError
    data class Http(val status: Int) : DownloadError
    data object ShaCompute : DownloadError
    data object ShaMismatch : DownloadError
    data object InstallFailed : DownloadError
}

class DownloadFailedException(val error: DownloadError) : Exception()

sealed interface DownloadLog {
    data object DiskOk : DownloadLog
    data class Mirror(val repo: String) : DownloadLog
    data object Ready : DownloadLog
    data object AlreadyPresent : DownloadLog
    data object Transfer : DownloadLog
    data class ReceivedMb(val downloaded: Int, val total: Int) : DownloadLog
}

data class DownloadState(
    val phase: DownloadPhase = DownloadPhase.DiskCheck,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = MODEL_BYTES,
    val paused: Boolean = false,
    val speedBps: Long = 0,
    val logs: List<DownloadLog> = emptyList(),
    val error: DownloadError? = null,
) {
    val fraction: Float get() = if (totalBytes == 0L) 0f else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
    val done: Boolean get() = phase == DownloadPhase.Done
    val running: Boolean get() = !paused && phase != DownloadPhase.Done && phase != DownloadPhase.Cancelled && phase != DownloadPhase.Failed
}

data class VoiceDownloadState(
    val lang: String? = null,
    val queue: List<String> = emptyList(),
    val finished: List<String> = emptyList(),
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val running: Boolean = false,
    val error: DownloadError? = null,
) {
    val fraction: Float get() = if (totalBytes == 0L) 0f else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
    val index: Int get() = finished.size + if (lang != null) 1 else 0
    val total: Int get() = finished.size + (if (lang != null) 1 else 0) + queue.size
}

data class AppData(
    val installed: Boolean = false,
    val installStep: String = "Welcome",
    val settings: UserSettings = UserSettings(),
    val lastSourceLang: String = AUTO_LANG,
    val lastTargetLang: String = "en",
    val history: List<HistoryItem> = emptyList(),
    val model: ModelInfo = ModelInfo(),
)

const val MODEL_BYTES = 2_588_147_712L
