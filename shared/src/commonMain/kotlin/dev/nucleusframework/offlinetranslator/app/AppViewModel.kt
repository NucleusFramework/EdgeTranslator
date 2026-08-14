package dev.nucleusframework.offlinetranslator.app

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import dev.nucleusframework.offlinetranslator.data.AppStore
import dev.nucleusframework.offlinetranslator.data.HistoryStore
import dev.nucleusframework.offlinetranslator.data.seedData
import dev.nucleusframework.offlinetranslator.di.Io
import dev.nucleusframework.offlinetranslator.domain.AppData
import dev.nucleusframework.offlinetranslator.domain.DownloadError
import dev.nucleusframework.offlinetranslator.domain.DownloadFailedException
import dev.nucleusframework.offlinetranslator.domain.DownloadLog
import dev.nucleusframework.offlinetranslator.domain.DownloadPhase
import dev.nucleusframework.offlinetranslator.domain.DownloadState
import dev.nucleusframework.offlinetranslator.domain.HistoryItem
import dev.nucleusframework.offlinetranslator.domain.LangRole
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.domain.LlmKeepAlive
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.MODEL_IDLE_RELEASE_MS
import dev.nucleusframework.offlinetranslator.domain.VoiceDownloadState
import dev.nucleusframework.offlinetranslator.domain.allowedOn
import dev.nucleusframework.offlinetranslator.domain.filterHistory
import dev.nucleusframework.offlinetranslator.domain.newId
import dev.nucleusframework.offlinetranslator.domain.replaceTerm
import dev.nucleusframework.offlinetranslator.engine.CatalogModel
import dev.nucleusframework.offlinetranslator.engine.DownloadedModel
import dev.nucleusframework.offlinetranslator.engine.FileImagePicker
import dev.nucleusframework.offlinetranslator.engine.GemmaModel
import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.ImagePicker
import dev.nucleusframework.offlinetranslator.engine.LlmRuntime
import dev.nucleusframework.offlinetranslator.engine.MIC_MAX_MS
import dev.nucleusframework.offlinetranslator.engine.MicRecorder
import dev.nucleusframework.offlinetranslator.engine.ModelDownloader
import dev.nucleusframework.offlinetranslator.engine.PiperVoiceSpec
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.engine.SilentMic
import dev.nucleusframework.offlinetranslator.engine.SilentTts
import dev.nucleusframework.offlinetranslator.engine.TranslationMode
import dev.nucleusframework.offlinetranslator.engine.TranslationRequest
import dev.nucleusframework.offlinetranslator.engine.TranslationResult
import dev.nucleusframework.offlinetranslator.engine.Translator
import dev.nucleusframework.offlinetranslator.engine.TtsSpeaker
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.platform.systemUiLanguage
import dev.nucleusframework.offlinetranslator.translation.MicPhase
import dev.nucleusframework.offlinetranslator.translation.TranslationState
import dev.nucleusframework.offlinetranslator.translation.TranslationStatus
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AssistedInject
class AppViewModel(
    private val store: AppStore,
    private val historyStore: HistoryStore,
    private val translator: Translator,
    private val downloader: ModelDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    @Io private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = { Platform.now() },
    @Assisted private val onQuit: () -> Unit = {},
    @Assisted private val forceOnboarding: Boolean = false,
    private val translateDelayMs: Long = 350,
    private val idleReleaseMs: Long = MODEL_IDLE_RELEASE_MS,
    private val mic: MicRecorder = SilentMic,
    private val imagePicker: ImagePicker = FileImagePicker,
    private val tts: TtsSpeaker = SilentTts,
    private val modelOnDisk: (CatalogModel) -> Boolean = { it.isOnDisk() },
    private val modelOwnedByApp: (CatalogModel) -> Boolean = { it.ownedByApp() },
    private val deleteModelFiles: (CatalogModel) -> Unit = { it.removeFromDisk() },
    private val voicesOnDisk: () -> Set<String> = { PiperVoices.installed() },
    private val voiceOnDisk: (PiperVoiceSpec) -> Boolean = { it.isOnDisk() },
    private val deleteVoiceFiles: (String) -> Unit = { PiperVoices.of(it)?.removeFromDisk() },
    private val wipeDownloadDirs: () -> Unit = {
        Platform.deleteRecursively(PiperVoices.dir())
        Platform.deleteRecursively(PiperVoices.legacyDir())
    },
    private val migrateVoices: () -> Unit = { PiperVoices.migrateLegacy() },
    private val hostRamBytes: () -> Long = { Platform.totalRamBytes() },
) : ViewModel() {

    @AssistedFactory
    fun interface Factory {
        fun create(onQuit: () -> Unit, forceOnboarding: Boolean): AppViewModel
    }

    private val job = SupervisorJob()

    @StructuredScope
    private val scope = CoroutineScope(job + dispatcher)

    private val restored = run {
        migrateVoices()
        restore()
    }
    private val _state = MutableStateFlow(restored.state)
    val state: StateFlow<AppState> = _state.asStateFlow()
    val micLevels: StateFlow<List<Float>> get() = mic.levels
    val backStack: NavBackStack<AppKey> = NavBackStack(*restored.keys.toTypedArray())

    private var saveJob: Job? = null
    private var downloadJob: Job? = null
    private var translateDebounceJob: Job? = null
    private var translateJob: Job? = null
    private var translating = false
    private var proofreadDebounceJob: Job? = null
    private var proofreadJob: Job? = null
    private var proofreading = false
    private var recordJob: Job? = null
    private var speakJob: Job? = null
    private var voiceJob: Job? = null
    private var idleReleaseJob: Job? = null

    init {
        val s = _state.value
        if (backStack.last() == AppKey.Download && !s.download.done) {
            startDownload()
        }
        preloadIfKeptReady(s.data.model.path)
    }

    override fun onCleared() {
        recordJob?.cancel()
        speakJob?.cancel()
        voiceJob?.cancel()
        cancelIdleRelease()
        tts.stop()
        tts.close()
        scope.launch { runCatching { mic.stop() } }
        translator.close()
        job.cancel()
        super.onCleared()
    }

    fun onIntent(intent: AppIntent) {
        if (!allowed(intent)) return
        when (intent) {
            AppIntent.Quit -> {
                tts.close()
                onQuit()
            }

            AppIntent.CopyTranslation -> copyTranslation()

            AppIntent.CopyProofread -> copyProofread()

            AppIntent.PauseDownload -> pauseDownload()

            AppIntent.ResumeDownload -> startDownload()

            AppIntent.CancelDownload -> cancelDownload()

            AppIntent.RetryDownload -> {
                mutate { it.copy(download = DownloadState()) }
                startDownload()
            }

            AppIntent.CompleteDownload -> completeDownload()

            AppIntent.ConfirmDialog -> {
                when (val action = (_state.value.dialog as? AppDialog.Confirm)?.action) {
                    is ConfirmAction.DeleteModel -> deleteModel(action.id)
                    is ConfirmAction.DeleteVoice -> deleteVoice(action.lang)
                    ConfirmAction.ResetApp -> resetApp()
                    else -> mutate { confirmAction(it) }
                }
            }

            AppIntent.ToggleMic -> toggleMic()

            AppIntent.CancelMic -> cancelMic()

            AppIntent.TranslateImage -> translateImage()

            is AppIntent.TranslateDroppedImage -> translateImage(intent.bytes)

            is AppIntent.ToggleSpeak -> toggleSpeak(intent.target)

            AppIntent.StopSpeak -> stopSpeak()

            is AppIntent.DownloadVoices -> startVoiceDownload(intent.langs)

            AppIntent.PauseVoiceDownload -> pauseVoiceDownload()

            AppIntent.ResumeVoiceDownload, AppIntent.RetryVoiceDownload -> resumeVoiceDownload()

            AppIntent.CancelVoiceDownload -> cancelVoiceDownload()

            else -> {
                applyNavigation(intent)
                mutate { reduce(it, intent) }
                afterReduce(intent)
            }
        }
    }

    private fun afterReduce(intent: AppIntent) {
        when (intent) {
            AppIntent.StartInstall -> {
                persist(now = true)
                if (_state.value.installStep() == InstallStep.Download && !_state.value.download.done) {
                    startDownload()
                }
            }

            AppIntent.OpenApp -> persist(now = true)

            is AppIntent.SelectModel -> {
                persist(now = true)
                val catalog = GemmaModels.of(intent.id)
                if (modelOnDisk(catalog)) preloadIfKeptReady(catalog.destPath())
            }

            is AppIntent.SetLlmBackend -> {
                LlmRuntime.preference = intent.backend
                persist(now = true)
                preloadIfKeptReady(_state.value.data.model.path)
            }

            is AppIntent.SetLlmKeepAlive -> {
                persist(now = true)
                if (intent.mode == LlmKeepAlive.AlwaysOn) {
                    preloadIfKeptReady(_state.value.data.model.path)
                } else {
                    cancelIdleRelease()
                    scope.launch { translator.release() }
                }
            }

            is AppIntent.DownloadModel -> {
                persist(now = true)
                val catalog = GemmaModels.of(intent.id)
                if (modelOnDisk(catalog)) {
                    preloadIfKeptReady(catalog.destPath())
                } else {
                    downloadJob?.cancel()
                    downloadJob = null
                    startDownload()
                }
            }

            is AppIntent.SetSourceText -> scheduleTranslate()

            is AppIntent.SetProofreadText -> scheduleProofread()

            AppIntent.ApplyProofread -> Unit

            AppIntent.NewTranslation -> {
                cancelMic()
                cancelTranslateJobs()
            }

            AppIntent.SwapLanguages, is AppIntent.ChooseLanguage -> {
                scheduleTranslate()
                persist(now = true)
            }

            is AppIntent.SetHistoryQuery,
            AppIntent.DismissMessage, AppIntent.DismissDialog,
            is AppIntent.DownloadTick, is AppIntent.DownloadPhase,
            -> Unit

            else -> persist(now = true)
        }
    }

    private data class Restored(val state: AppState, val keys: List<AppKey>)

    private fun restore(): Restored {
        val loaded = store.load()
        val missingModel = loaded.model.installed && loaded.model.path.isNotBlank() && !Platform.exists(loaded.model.path)
        var data = if (missingModel) {
            loaded.copy(
                installed = false,
                installStep = InstallStep.Download.name,
                model = loaded.model.copy(installed = false, installedAt = null, sha256 = "", path = ""),
            )
        } else {
            loaded
        }
        // Re-resolve on every launch: the OS language can change between runs.
        if (data.settings.uiLanguageAuto) {
            data = data.copy(settings = data.settings.copy(uiLanguage = systemUiLanguage()))
        }
        if (data.settings.autoPurge) {
            val cut = clock() - data.settings.purgeAfterDays.toLong() * 24 * 60 * 60 * 1000
            historyStore.purgeOlderThan(cut)
        }
        data = data.copy(history = historyStore.all())
        if (forceOnboarding) {
            data = data.copy(installed = false, installStep = InstallStep.Welcome.name)
        }
        val translation = TranslationState(
            sourceLang = data.lastSourceLang,
            targetLang = data.lastTargetLang,
            ttsReady = tts.available,
            installedVoices = voicesOnDisk(),
        )
        val keys = if (data.installed && data.model.installed) {
            listOf(AppKey.Translate)
        } else {
            installStack(parseInstallStep(data.installStep))
        }
        val voicePicks = if (parseInstallStep(data.installStep) == InstallStep.Voices && tts.available) {
            PiperVoices.defaultPicks(data.settings.uiLanguage)
        } else {
            emptySet()
        }
        if (data.installed && !modelOnDisk(GemmaModels.of(data.settings.selectedModel))) {
            val fallback = data.model.id.takeIf { data.model.installed && modelOnDisk(GemmaModels.of(it)) }
                ?: GemmaModels.all.firstOrNull { modelOnDisk(it) }?.id
            if (fallback != null) {
                data = data.copy(settings = data.settings.copy(selectedModel = fallback))
            }
        }
        val ram = hostRamBytes()
        data = coerceModelForRam(data, ram)
        LlmRuntime.preference = data.settings.backend
        val catalog = GemmaModels.of(data.settings.selectedModel)
        if (modelOnDisk(catalog) && (!data.model.installed || data.model.id != catalog.id)) {
            data = data.copy(model = catalog.toInfo(clock()))
            store.save(data.copy(history = emptyList()))
        }
        val download = if (modelOnDisk(catalog)) {
            DownloadState(phase = DownloadPhase.Done, bytesDownloaded = catalog.bytes, totalBytes = catalog.bytes)
        } else {
            DownloadState(
                phase = if (data.installed) DownloadPhase.Cancelled else DownloadPhase.DiskCheck,
                totalBytes = catalog.bytes,
            )
        }
        return Restored(
            state = AppState(
                data = data,
                translation = translation,
                voicePicks = voicePicks,
                download = download,
                hostRamBytes = ram,
            ),
            keys = keys,
        )
    }

    private fun applyNavigation(intent: AppIntent) {
        when (intent) {
            AppIntent.StartInstall -> push(AppKey.Download)
            AppIntent.InstallBack -> if (backStack.size > 1) backStack.removeLast()
            AppIntent.OpenApp, AppIntent.NewTranslation -> setMain(AppKey.Translate)
            is AppIntent.GoToStep -> setInstall(intent.step)
            is AppIntent.Navigate -> setMain(intent.destination)
            is AppIntent.OpenHistory -> setMain(AppKey.Translate)
            else -> Unit
        }
    }

    private fun push(key: AppKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    private fun setInstall(step: InstallStep) {
        val next = installStack(step)
        backStack.clear()
        backStack.addAll(next)
    }

    private fun setMain(key: AppKey) {
        backStack.clear()
        backStack.add(key)
    }

    private fun reduce(s: AppState, intent: AppIntent): AppState = when (intent) {
        AppIntent.StartInstall -> s.gotoInstall(InstallStep.Download)

        AppIntent.InstallBack -> s.gotoInstall(s.installStep().previous())

        AppIntent.OpenApp -> {
            val catalog = GemmaModels.of(s.data.settings.selectedModel)
            s.copy(
                data = s.data.copy(
                    installed = true,
                    installStep = InstallStep.Download.name,
                    model = if (modelOnDisk(catalog)) catalog.toInfo(clock()) else s.data.model,
                ),
            )
        }

        is AppIntent.GoToStep -> {
            val next = s.gotoInstall(intent.step)
            if (intent.step == InstallStep.Voices && next.voicePicks.isEmpty()) {
                next.copy(voicePicks = PiperVoices.defaultPicks(next.data.settings.uiLanguage))
            } else {
                next
            }
        }

        is AppIntent.Navigate -> s.copy(message = null)

        AppIntent.NewTranslation -> s.copy(
            translation = s.translation.copy(
                sourceText = "",
                targetText = "",
                alternatives = emptyList(),
                highlightTerm = "",
                alternativesFor = "",
                selectedAlternative = "",
                status = TranslationStatus.Idle,
                latencyMs = null,
                error = null,
                micPhase = MicPhase.Idle,
                imageBusy = false,
            ),
        )

        AppIntent.SwapLanguages -> {
            val t = s.translation
            val source = t.targetLang
            val target = if (Languages.isAuto(t.sourceLang)) {
                if (t.targetLang == "en") "fr" else "en"
            } else {
                t.sourceLang
            }
            s.withLangs(source, target)
        }

        is AppIntent.SelectAlternative -> {
            val from = s.translation.highlightTerm.ifBlank { s.translation.selectedAlternative }
            val replaced = replaceTerm(s.translation.targetText, from, intent.term)
            s.copy(
                translation = s.translation.copy(
                    targetText = replaced,
                    selectedAlternative = intent.term,
                    highlightTerm = intent.term,
                    alternativesFor = intent.term,
                ),
            )
        }

        AppIntent.SaveToHistory -> saveToHistory(s)

        is AppIntent.SetSourceText -> {
            val text = GemmaModel.capInput(intent.text)
            s.copy(
                translation = s.translation.copy(
                    sourceText = text,
                    status = if (text.isBlank()) TranslationStatus.Idle else TranslationStatus.WaitingEngine,
                ),
            )
        }

        is AppIntent.SetProofreadText -> {
            val text = GemmaModel.capInput(intent.text)
            s.copy(
                proofread = s.proofread.copy(
                    text = text,
                    status = if (text.isBlank()) TranslationStatus.Idle else TranslationStatus.WaitingEngine,
                ),
            )
        }

        AppIntent.ApplyProofread -> if (s.proofread.result.isBlank()) {
            s
        } else {
            s.copy(proofread = s.proofread.copy(text = GemmaModel.capInput(s.proofread.result)))
        }

        is AppIntent.ChooseLanguage -> chooseLanguage(s, intent.code, intent.role)

        is AppIntent.SetUiLanguage -> s.updateSettings {
            it.copy(uiLanguage = intent.language ?: systemUiLanguage(), uiLanguageAuto = intent.language == null)
        }

        is AppIntent.SetLangNameStyle -> s.updateSettings { it.copy(langNames = intent.style) }

        AppIntent.DropUnsupported -> s.copy(message = AppMessage.DropUnsupported)

        is AppIntent.SetLlmBackend -> s.updateSettings { it.copy(backend = intent.backend) }

        is AppIntent.SetLlmKeepAlive -> s.updateSettings { it.copy(keepAlive = intent.mode) }

        is AppIntent.DownloadTick -> s.copy(
            download = s.download.copy(
                phase = DownloadPhase.Transfer,
                bytesDownloaded = intent.bytes,
                totalBytes = if (intent.totalBytes > 0) intent.totalBytes else s.download.totalBytes,
                speedBps = intent.speedBps,
                logs = intent.log?.let { (s.download.logs + it).takeLast(12) } ?: s.download.logs,
            ),
        )

        is AppIntent.DownloadPhase -> s.copy(download = s.download.copy(phase = intent.phase))

        is AppIntent.SetHistoryQuery -> s.copy(historyQuery = intent.query)

        is AppIntent.SetHistoryFilter -> s.copy(historyFilter = intent.filter)

        is AppIntent.OpenHistory -> openHistory(s, intent.id)

        is AppIntent.ToggleHistoryPin -> {
            historyStore.togglePin(intent.id)
            s.copy(data = s.data.copy(history = historyStore.all()))
        }

        is AppIntent.DeleteHistory -> {
            historyStore.delete(intent.id)
            s.copy(data = s.data.copy(history = historyStore.all()))
        }

        AppIntent.ClearHistory -> s.copy(dialog = AppDialog.Confirm(ConfirmAction.PurgeHistory))

        is AppIntent.SelectModel -> selectModel(s, intent.id)

        is AppIntent.DownloadModel -> downloadModel(s, intent.id)

        is AppIntent.DeleteModel -> s.copy(dialog = AppDialog.Confirm(ConfirmAction.DeleteModel(intent.id)))

        is AppIntent.DeleteVoice -> s.copy(dialog = AppDialog.Confirm(ConfirmAction.DeleteVoice(intent.lang)))

        AppIntent.ResetApp -> s.copy(dialog = AppDialog.Confirm(ConfirmAction.ResetApp))

        is AppIntent.SelectVoice -> {
            val spec = PiperVoices.of(intent.id) ?: return s
            if (!voiceOnDisk(spec)) return s
            s.updateSettings { it.copy(selectedVoices = it.selectedVoices + (spec.lang to spec.id)) }
        }

        is AppIntent.ToggleVoicePick -> {
            val id = PiperVoices.of(intent.code)?.id ?: return s
            val next = if (id in s.voicePicks) s.voicePicks - id else s.voicePicks + id
            s.copy(voicePicks = next)
        }

        is AppIntent.SelectAllVoices -> {
            val add = if (intent.lang != null) {
                PiperVoices.forLang(intent.lang).map { it.id }.toSet()
            } else {
                PiperVoices.defaultIds()
            }
            s.copy(voicePicks = s.voicePicks + add)
        }

        is AppIntent.ClearVoicePicks -> {
            if (intent.lang == null) {
                s.copy(voicePicks = emptySet())
            } else {
                s.copy(voicePicks = s.voicePicks.filter { PiperVoices.of(it)?.lang != intent.lang }.toSet())
            }
        }

        is AppIntent.SetTheme -> s.updateSettings { it.copy(theme = intent.mode) }

        is AppIntent.SetAirplane -> s.updateSettings { it.copy(airplane = intent.on) }

        is AppIntent.SetKeepHistory -> s.updateSettings { it.copy(keepHistory = intent.on) }

        is AppIntent.SetAutoPurge -> {
            val next = s.updateSettings { it.copy(autoPurge = intent.on) }
            if (intent.on) {
                val cut = clock() - next.data.settings.purgeAfterDays.toLong() * 24 * 60 * 60 * 1000
                historyStore.purgeOlderThan(cut)
                next.copy(data = next.data.copy(history = historyStore.all()))
            } else {
                next
            }
        }

        is AppIntent.SetLaunchAtLogin -> s.updateSettings { it.copy(launchAtLogin = intent.on) }

        AppIntent.ConfirmDialog -> confirmAction(s)

        AppIntent.DismissDialog -> s.copy(dialog = AppDialog.Hidden)

        AppIntent.DismissMessage -> s.copy(message = null)

        AppIntent.Quit,
        AppIntent.CopyTranslation,
        AppIntent.CopyProofread,
        AppIntent.PauseDownload,
        AppIntent.ResumeDownload,
        AppIntent.CancelDownload,
        AppIntent.RetryDownload,
        AppIntent.CompleteDownload,
        AppIntent.ToggleMic,
        AppIntent.CancelMic,
        AppIntent.TranslateImage,
        is AppIntent.TranslateDroppedImage,
        is AppIntent.ToggleSpeak,
        AppIntent.StopSpeak,
        is AppIntent.DownloadVoices,
        AppIntent.PauseVoiceDownload,
        AppIntent.ResumeVoiceDownload,
        AppIntent.CancelVoiceDownload,
        AppIntent.RetryVoiceDownload,
        -> s
    }

    private fun allowed(intent: AppIntent): Boolean {
        val ram = _state.value.hostRamBytes
        return when (intent) {
            AppIntent.StartInstall -> LlmModel.Fast.allowedOn(ram)
            is AppIntent.SelectModel -> intent.id.allowedOn(ram)
            is AppIntent.DownloadModel -> intent.id.allowedOn(ram) || modelOnDisk(GemmaModels.of(intent.id))
            else -> true
        }
    }

    /**
     * Drop Precision if the host cannot run it, unless that model is already installed and selected.
     * Onboarding always falls back to Fast so the picker never starts on a locked card.
     */
    private fun coerceModelForRam(data: AppData, ram: Long): AppData {
        val id = data.settings.selectedModel
        if (id.allowedOn(ram)) return data
        if (data.installed && modelOnDisk(GemmaModels.of(id))) return data
        val fallback = LlmModel.Fast
        if (fallback == id) return data
        val catalog = GemmaModels.of(fallback)
        return data.copy(
            settings = data.settings.copy(selectedModel = fallback),
            model = if (modelOnDisk(catalog)) catalog.toInfo(clock()) else data.model,
        )
    }

    private fun selectModel(s: AppState, id: LlmModel): AppState {
        if (!id.allowedOn(s.hostRamBytes)) return s
        val catalog = GemmaModels.of(id)
        val onDisk = modelOnDisk(catalog)
        if (!onDisk && s.data.installed) return s
        if (s.data.settings.selectedModel == id && (onDisk || s.download.running || !s.data.installed)) {
            return if (onDisk && (!s.data.model.installed || s.data.model.id != id)) {
                s.copy(
                    data = s.data.copy(model = catalog.toInfo(clock())),
                    download = DownloadState(
                        phase = DownloadPhase.Done,
                        bytesDownloaded = catalog.bytes,
                        totalBytes = catalog.bytes,
                    ),
                )
            } else {
                s
            }
        }
        val next = s.updateSettings { it.copy(selectedModel = id) }
        return if (onDisk) {
            next.copy(
                data = next.data.copy(model = catalog.toInfo(clock())),
                download = DownloadState(phase = DownloadPhase.Done, bytesDownloaded = catalog.bytes, totalBytes = catalog.bytes),
            )
        } else {
            next.copy(download = DownloadState(totalBytes = catalog.bytes))
        }
    }

    private fun downloadModel(s: AppState, id: LlmModel): AppState {
        val catalog = GemmaModels.of(id)
        if (!id.allowedOn(s.hostRamBytes) && !modelOnDisk(catalog)) return s
        if (modelOnDisk(catalog)) return selectModel(s, id)
        if (s.data.settings.selectedModel == id && (s.download.running || s.download.paused)) return s
        return s.updateSettings { it.copy(selectedModel = id) }
            .copy(download = DownloadState(totalBytes = catalog.bytes))
    }

    private fun chooseLanguage(s: AppState, code: String, role: LangRole): AppState {
        val t = s.translation
        val next = when (role) {
            LangRole.Source -> t.copy(sourceLang = code)
            LangRole.Target -> if (Languages.isAuto(code)) t else t.copy(targetLang = code)
        }
        return s.withLangs(next.sourceLang, next.targetLang)
    }

    private fun saveToHistory(s: AppState): AppState {
        val src = s.translation.sourceText.trim()
        val tgt = s.translation.targetText.trim()
        if (src.isEmpty() || tgt.isEmpty()) {
            return s.copy(message = AppMessage.NothingToSave)
        }
        if (!s.data.settings.keepHistory) {
            return s.copy(message = AppMessage.HistoryDisabled)
        }
        val item = HistoryItem(
            id = newId(clock()),
            createdAt = clock(),
            sourceLang = s.translation.sourceLang,
            targetLang = s.translation.targetLang,
            sourceText = src,
            targetText = tgt,
        )
        historyStore.insert(item)
        return s.copy(
            data = s.data.copy(history = historyStore.all()),
            translation = s.translation.copy(savedSource = src, savedTarget = tgt),
        )
    }

    private fun openHistory(s: AppState, id: String): AppState {
        val item = historyStore.get(id) ?: return s
        return s.withLangs(item.sourceLang, item.targetLang).copy(
            translation = s.translation.copy(
                sourceLang = item.sourceLang,
                targetLang = item.targetLang,
                sourceText = GemmaModel.capInput(item.sourceText),
                targetText = item.targetText,
                savedSource = item.sourceText.trim(),
                savedTarget = item.targetText.trim(),
                status = TranslationStatus.Ready,
                alternatives = emptyList(),
                highlightTerm = "",
                alternativesFor = "",
                selectedAlternative = "",
            ),
        )
    }

    private fun confirmAction(s: AppState): AppState {
        val d = s.dialog as? AppDialog.Confirm ?: return s.copy(dialog = AppDialog.Hidden)
        return when (d.action) {
            ConfirmAction.PurgeHistory -> {
                historyStore.clear()
                s.copy(
                    dialog = AppDialog.Hidden,
                    data = s.data.copy(history = historyStore.all()),
                )
            }

            is ConfirmAction.DeleteModel -> s.copy(dialog = AppDialog.Hidden)

            is ConfirmAction.DeleteVoice -> s.copy(dialog = AppDialog.Hidden)

            ConfirmAction.ResetApp -> s.copy(dialog = AppDialog.Hidden)
        }
    }

    private fun resetApp() {
        saveJob?.cancel()
        downloadJob?.cancel()
        downloadJob = null
        voiceJob?.cancel()
        voiceJob = null
        cancelTranslateJobs()
        cancelProofreadJobs()
        recordJob?.cancel()
        recordJob = null
        speakJob?.cancel()
        speakJob = null
        tts.stop()
        tts.unload()
        scope.launch { runCatching { mic.stop() } }
        unloadEngine()
        LlmRuntime.preference = LlmBackend.Auto
        GemmaModels.all.forEach { catalog ->
            if (modelOwnedByApp(catalog)) deleteModelFiles(catalog)
            else Platform.delete(catalog.partialPath())
        }
        PiperVoices.all().forEach { deleteVoiceFiles(it.id) }
        wipeDownloadDirs()
        historyStore.clear()
        mutate {
            AppState(
                data = seedData(),
                translation = TranslationState(ttsReady = it.translation.ttsReady),
            )
        }
        persist(now = true)
        setInstall(InstallStep.Welcome)
    }

    private fun deleteModel(id: LlmModel) {
        val catalog = GemmaModels.of(id)
        val selected = _state.value.data.settings.selectedModel
        if (_state.value.download.running && selected == id) {
            downloadJob?.cancel()
            downloadJob = null
        }
        if (_state.value.data.model.id == id) unloadEngine()
        deleteModelFiles(catalog)
        val fallback = GemmaModels.all.firstOrNull { it.id != id && modelOnDisk(it) }
        mutate { current ->
            val active = current.data.model.id == id || current.data.settings.selectedModel == id
            val nextModel = when {
                fallback != null && active -> fallback.toInfo(clock())
                active -> current.data.model.copy(installed = false, installedAt = null, sha256 = "", path = "")
                else -> current.data.model
            }
            val nextSettings = if (nextModel.installed && nextModel.id != current.data.settings.selectedModel) {
                current.data.settings.copy(selectedModel = nextModel.id)
            } else {
                current.data.settings
            }
            val nextDownload = if (current.data.settings.selectedModel == id) {
                DownloadState(totalBytes = catalog.bytes)
            } else {
                current.download
            }
            current.copy(
                dialog = AppDialog.Hidden,
                data = current.data.copy(settings = nextSettings, model = nextModel),
                download = nextDownload,
            )
        }
        persist(now = true)
        val next = _state.value.data.model
        if (next.installed) preloadIfKeptReady(next.path)
    }

    private fun unloadEngine() {
        cancelTranslateJobs()
        cancelProofreadJobs()
        cancelIdleRelease()
        translator.close()
    }

    private fun cancelTranslateJobs() {
        translateDebounceJob?.cancel()
        translateDebounceJob = null
        translateJob?.cancel()
        translateJob = null
        translating = false
    }

    private fun cancelProofreadJobs() {
        proofreadDebounceJob?.cancel()
        proofreadDebounceJob = null
        proofreadJob?.cancel()
        proofreadJob = null
        proofreading = false
    }

    private fun copyTranslation() {
        val text = _state.value.translation.targetText
        if (text.isBlank()) return
        Platform.copyToClipboard(text)
        mutate { it.copy(translation = it.translation.copy(copiedTarget = text.trim())) }
    }

    private fun copyProofread() {
        val text = _state.value.proofread.result
        if (text.isBlank()) return
        Platform.copyToClipboard(text)
        mutate { it.copy(proofread = it.proofread.copy(copiedResult = text.trim())) }
    }

    private fun startDownload() {
        if (downloadJob?.isActive == true) return
        val catalog = GemmaModels.of(_state.value.data.settings.selectedModel)
        if (!catalog.id.allowedOn(_state.value.hostRamBytes) && !modelOnDisk(catalog)) return
        downloadJob = scope.launch {
            mutate {
                it.copy(
                    download = it.download.copy(
                        paused = false,
                        error = null,
                        phase = DownloadPhase.DiskCheck,
                        totalBytes = catalog.bytes,
                    ),
                )
            }
            val (dest, already, free) = withContext(ioDispatcher) {
                val destPath = catalog.destPath()
                val dir = catalog.modelDir()
                Platform.mkdir(dir)
                val have = Platform.fileSize(destPath).let { if (it > 0) it else Platform.fileSize(catalog.partialPath()) }
                Triple(destPath, have, Platform.freeSpace(dir))
            }
            val needed = (catalog.bytes - already).coerceAtLeast(0) + GemmaModel.DISK_BUFFER_BYTES
            if (free in 1 until needed) {
                mutate {
                    it.copy(
                        download = it.download.copy(
                            phase = DownloadPhase.Failed,
                            error = DownloadError.DiskFull(free),
                        ),
                    )
                }
                return@launch
            }
            onIntent(AppIntent.DownloadPhase(DownloadPhase.DiskCheck))
            appendLog(DownloadLog.DiskOk)
            if (!isActive) return@launch
            onIntent(AppIntent.DownloadPhase(DownloadPhase.Connect))
            appendLog(DownloadLog.Mirror(catalog.repo))
            try {
                val downloaded = downloader.download(
                    destPath = dest,
                    url = catalog.url,
                    expectedSha256 = catalog.sha256,
                    expectedBytes = catalog.bytes,
                    onConnect = {
                        onIntent(AppIntent.DownloadPhase(DownloadPhase.Connect))
                    },
                    onVerify = {
                        onIntent(AppIntent.DownloadPhase(DownloadPhase.Verify))
                    },
                    onProgress = { bytes, total, speed, log ->
                        onIntent(AppIntent.DownloadTick(bytes, speed, log, total))
                    },
                )
                if (!isActive) return@launch
                if (downloaded.createdByApp) {
                    withContext(ioDispatcher) { catalog.markOwned() }
                }
                onIntent(AppIntent.DownloadPhase(DownloadPhase.Index))
                if (keepModelReady()) {
                    try {
                        translator.preload(downloaded.path)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                finishDownload(downloaded, catalog, DownloadLog.Ready)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? DownloadFailedException)?.error
                    ?: (e.cause as? DownloadFailedException)?.error
                    ?: DownloadError.Interrupted
                mutate {
                    it.copy(
                        download = it.download.copy(
                            phase = DownloadPhase.Failed,
                            error = error,
                            paused = false,
                        ),
                    )
                }
            }
        }
    }

    private fun pauseDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutate { it.copy(download = it.download.copy(paused = true)) }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val catalog = GemmaModels.of(_state.value.data.settings.selectedModel)
        scope.launch { withContext(ioDispatcher) { Platform.delete(catalog.partialPath()) } }
        mutate { s ->
            val fallback = s.data.model.id.takeIf { s.data.installed && s.data.model.installed }
            val next = if (fallback != null) s.updateSettings { it.copy(selectedModel = fallback) } else s
            val download = if (fallback != null) {
                val installed = GemmaModels.of(fallback)
                DownloadState(phase = DownloadPhase.Done, bytesDownloaded = installed.bytes, totalBytes = installed.bytes)
            } else {
                DownloadState(phase = DownloadPhase.Cancelled, totalBytes = catalog.bytes)
            }
            next.copy(download = download)
        }
        persist(now = true)
    }

    private fun completeDownload() {
        val catalog = GemmaModels.of(_state.value.data.settings.selectedModel)
        val dest = catalog.destPath()
        val bytes = Platform.fileSize(dest).takeIf { it > 0 } ?: catalog.bytes
        finishDownload(DownloadedModel(dest, catalog.sha256, bytes), catalog, readyLog = DownloadLog.Ready)
    }

    private fun finishDownload(downloaded: DownloadedModel, catalog: CatalogModel, readyLog: DownloadLog) {
        downloadJob?.cancel()
        downloadJob = null
        val now = clock()
        mutate { s ->
            s.copy(
                download = s.download.copy(
                    phase = DownloadPhase.Done,
                    bytesDownloaded = downloaded.bytes,
                    totalBytes = downloaded.bytes,
                    paused = false,
                    logs = (s.download.logs + readyLog).takeLast(12),
                ),
                data = s.data.copy(
                    model = catalog.toInfo(now).copy(
                        sha256 = downloaded.sha256.take(8),
                        path = downloaded.path,
                        expectedBytes = downloaded.bytes,
                    ),
                ),
            )
        }
        persist(now = true)
    }

    private fun keepModelReady(): Boolean = _state.value.data.settings.keepAlive == LlmKeepAlive.AlwaysOn

    private fun preloadIfKeptReady(path: String) {
        if (path.isBlank() || !keepModelReady()) return
        preloadModel(path)
    }

    private fun preloadModel(path: String) {
        if (path.isBlank()) return
        cancelIdleRelease()
        scope.launch {
            try {
                translator.preload(path)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun cancelIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = null
    }

    private fun scheduleIdleRelease() {
        cancelIdleRelease()
        if (keepModelReady()) return
        idleReleaseJob = scope.launch {
            delay(idleReleaseMs)
            translator.release()
        }
    }

    private fun appendLog(line: DownloadLog) {
        mutate { s ->
            s.copy(download = s.download.copy(logs = (s.download.logs + line).takeLast(12)))
        }
    }

    private fun stopSpeak() {
        speakJob?.cancel()
        speakJob = null
        tts.stop()
        mutate { it.copy(translation = it.translation.idleSpeak()) }
    }

    private fun toggleSpeak(target: Boolean) {
        val t = _state.value.translation
        if (t.speakTarget == target) {
            when {
                t.speakPlaying && !t.speakPaused -> {
                    tts.pause()
                    mutate { it.copy(translation = it.translation.copy(speakPaused = true)) }
                }
                t.speakPaused -> {
                    tts.resume()
                    mutate { it.copy(translation = it.translation.copy(speakPaused = false)) }
                }
                else -> stopSpeak()
            }
            return
        }
        val lang = if (target) t.targetLang else t.sourceLang
        val text = if (target) t.targetText else t.sourceText
        if (!tts.available || !Languages.hasTts(lang)) {
            mutate { it.copy(message = AppMessage.TtsUnavailable) }
            return
        }
        if (lang !in t.installedVoices) {
            val dl = _state.value.voiceDownload
            if (dl.busy && downloadCovers(dl, lang)) return
            mutate { it.copy(dialog = AppDialog.InstallVoice(lang)) }
            return
        }
        if (text.isBlank()) return
        speakJob?.cancel()
        tts.stop()
        val voiceId = _state.value.data.settings.selectedVoices[lang]
        speakJob = scope.launch {
            mutate {
                it.copy(
                    translation = it.translation.copy(
                        speakTarget = target,
                        speakBusy = true,
                        speakLoading = false,
                        speakPlaying = false,
                        speakPaused = false,
                    ),
                )
            }
            // A cold voice model takes seconds to load and synthesise before a sound comes out.
            // ponytail: 250 ms de sursis avant d'afficher le loader — modèle déjà chargé, pas de clignotement.
            val loader = launch {
                delay(250)
                mutate { it.copy(translation = it.translation.copy(speakLoading = true)) }
            }
            fun hideLoader() {
                loader.cancel()
                mutate { it.copy(translation = it.translation.copy(speakLoading = false)) }
            }
            try {
                withContext(ioDispatcher) {
                    tts.speak(text.trim(), lang, voiceId) {
                        loader.cancel()
                        mutate {
                            it.copy(
                                translation = it.translation.copy(
                                    speakLoading = false,
                                    speakPlaying = true,
                                ),
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                hideLoader()
                throw e
            } catch (_: Exception) {
                hideLoader()
                mutate { it.copy(message = AppMessage.TtsFailed, translation = it.translation.idleSpeak()) }
                return@launch
            }
            hideLoader()
            mutate { it.copy(translation = it.translation.idleSpeak()) }
        }
    }

    private fun startVoiceDownload(langs: List<String>?) {
        val s = _state.value
        val requested = (langs ?: s.voicePicks.toList())
            .mapNotNull { PiperVoices.of(it) }
            .filter { !voiceOnDisk(it) }
            .map { it.id }
            .distinct()
        if (s.dialog is AppDialog.InstallVoice) {
            mutate { it.copy(dialog = AppDialog.Hidden) }
        }
        if (requested.isEmpty()) return
        if (voiceJob?.isActive == true) {
            mutate {
                it.copy(
                    voiceDownload = it.voiceDownload.copy(
                        queue = it.voiceDownload.queue + requested.filter { id ->
                            id != it.voiceDownload.lang && id !in it.voiceDownload.queue
                        },
                    ),
                )
            }
            return
        }
        voiceJob = scope.launch {
            val missing = requested.toMutableList()
            val dir = PiperVoices.dir()
            val needed = missing.sumOf { PiperVoices.of(it)?.bytes ?: 0L }
            val free = withContext(ioDispatcher) {
                Platform.mkdir(dir)
                Platform.freeSpace(dir)
            }
            if (free in 1 until needed) {
                mutate {
                    it.copy(
                        voiceDownload = VoiceDownloadState(
                            queue = missing,
                            running = false,
                            error = DownloadError.DiskFull(free),
                            totalBytes = needed,
                        ),
                    )
                }
                return@launch
            }
            val finished = mutableListOf<String>()
            while (missing.isNotEmpty()) {
                val lang = missing.removeFirst()
                val spec = PiperVoices.of(lang) ?: continue
                mutate {
                    it.copy(
                        voiceDownload = VoiceDownloadState(
                            lang = lang,
                            queue = missing.toList(),
                            finished = finished.toList(),
                            bytesDownloaded = 0,
                            totalBytes = spec.bytes,
                            running = true,
                        ),
                    )
                }
                try {
                    withContext(ioDispatcher) {
                        downloader.download(
                            destPath = spec.destOnnx(),
                            url = spec.url(spec.fileName),
                            expectedSha256 = "",
                            expectedBytes = spec.onnxBytes,
                            onConnect = {},
                            onVerify = {},
                            onProgress = { bytes, _, speed, _ ->
                                mutate {
                                    it.copy(
                                        voiceDownload = it.voiceDownload.copy(
                                            bytesDownloaded = bytes,
                                            totalBytes = spec.bytes,
                                            speedBps = speed,
                                        ),
                                    )
                                }
                            },
                        )
                        downloader.download(
                            destPath = spec.destJson(),
                            url = spec.url("${spec.fileName}.json"),
                            expectedSha256 = "",
                            expectedBytes = spec.jsonBytes,
                            onConnect = {},
                            onVerify = {},
                            onProgress = { bytes, _, speed, _ ->
                                mutate {
                                    it.copy(
                                        voiceDownload = it.voiceDownload.copy(
                                            bytesDownloaded = spec.onnxBytes + bytes,
                                            totalBytes = spec.bytes,
                                            speedBps = speed,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val error = (e as? DownloadFailedException)?.error
                        ?: (e.cause as? DownloadFailedException)?.error
                        ?: DownloadError.Interrupted
                    mutate { it.copy(voiceDownload = it.voiceDownload.copy(running = false, error = error)) }
                    return@launch
                }
                finished += spec.id
                mutate {
                    it.copy(
                        data = it.data.copy(
                            settings = it.data.settings.copy(
                                selectedVoices = it.data.settings.selectedVoices + (spec.lang to spec.id),
                            ),
                        ),
                        translation = it.translation.copy(installedVoices = it.translation.installedVoices + spec.lang),
                        voiceDownload = it.voiceDownload.copy(
                            lang = null,
                            finished = finished.toList(),
                            queue = missing.toList(),
                            bytesDownloaded = spec.bytes,
                            totalBytes = spec.bytes,
                            running = missing.isNotEmpty(),
                        ),
                    )
                }
            }
            persist(now = true)
        }
    }

    private fun pauseVoiceDownload() {
        if (!_state.value.voiceDownload.running) return
        voiceJob?.cancel()
        voiceJob = null
        mutate { it.copy(voiceDownload = it.voiceDownload.copy(running = false, paused = true)) }
    }

    private fun resumeVoiceDownload() {
        val dl = _state.value.voiceDownload
        startVoiceDownload((listOfNotNull(dl.lang) + dl.queue).ifEmpty { null })
    }

    private fun cancelVoiceDownload() {
        voiceJob?.cancel()
        voiceJob = null
        val lang = _state.value.voiceDownload.lang
        if (lang != null) {
            val spec = PiperVoices.of(lang)
            if (spec != null) {
                scope.launch {
                    withContext(ioDispatcher) {
                        Platform.delete(spec.partialOnnx())
                        Platform.delete(spec.partialJson())
                    }
                }
            }
        }
        mutate { it.copy(voiceDownload = VoiceDownloadState()) }
    }

    private fun deleteVoice(token: String) {
        val spec = PiperVoices.of(token)
        val lang = spec?.lang ?: token
        val dl = _state.value.voiceDownload
        if (dl.busy && downloadCovers(dl, lang)) {
            cancelVoiceDownload()
        }
        deleteVoiceFiles(token)
        val still = PiperVoices.forLang(lang).any { voiceOnDisk(it) }
        mutate {
            val selected = it.data.settings.selectedVoices
            val dropSelected = selected[lang] == token || selected[lang] == spec?.id
            it.copy(
                dialog = AppDialog.Hidden,
                data = it.data.copy(
                    settings = it.data.settings.copy(
                        selectedVoices = if (dropSelected) selected - lang else selected,
                    ),
                ),
                translation = it.translation.copy(
                    installedVoices = if (still) it.translation.installedVoices else it.translation.installedVoices - lang,
                ),
            )
        }
        persist(now = true)
    }

    private fun downloadCovers(dl: VoiceDownloadState, lang: String): Boolean =
        PiperVoices.covers(dl.lang, lang) || dl.queue.any { PiperVoices.covers(it, lang) }

    private fun toggleMic() {
        when (_state.value.translation.micPhase) {
            MicPhase.Listening -> {
                recordJob?.cancel()
                recordJob = null
                scope.launch { finishRecording(transcribe = true) }
            }

            // Tapping the loader aborts the opening line.
            MicPhase.Starting -> cancelMic()

            MicPhase.Processing -> Unit

            MicPhase.Idle -> startRecording()
        }
    }

    private fun cancelMic() {
        recordJob?.cancel()
        recordJob = null
        scope.launch { runCatching { mic.stop() } }
        mutate {
            it.copy(
                translation = it.translation.copy(
                    micPhase = MicPhase.Idle,
                ),
            )
        }
    }

    private fun startRecording() {
        if (!mic.available) {
            mutate { it.copy(message = AppMessage.MicUnavailable) }
            return
        }
        if (!_state.value.data.model.installed || _state.value.translation.imageBusy) return
        recordJob?.cancel()
        // The pane goes up before mic.start(): opening the line is slow cold (OS permission
        // prompt, device wake-up), and a button that does nothing for a second reads as broken.
        mutate { it.copy(translation = it.translation.copy(micPhase = MicPhase.Starting)) }
        recordJob = scope.launch {
            try {
                mic.start()
            } catch (_: Exception) {
                mutate { it.copy(message = AppMessage.MicFailed, translation = it.translation.copy(micPhase = MicPhase.Idle)) }
                return@launch
            }
            mutate { it.copy(translation = it.translation.copy(micPhase = MicPhase.Listening)) }
            var elapsed = 0L
            var heard = false
            var quietMs = 0L
            while (isActive && elapsed < MIC_MAX_MS) {
                delay(50)
                elapsed += 50
                val peak = mic.levels.value.lastOrNull() ?: 0f
                if (peak > 0.12f) {
                    heard = true
                    quietMs = 0
                } else if (heard) {
                    quietMs += 50
                }
                if (heard && quietMs >= 1400 && elapsed >= 1200) break
            }
            if (isActive) finishRecording(transcribe = true)
        }
    }

    private suspend fun finishRecording(transcribe: Boolean) {
        recordJob = null
        val wav = try {
            mic.stop()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            mutate { it.copy(message = AppMessage.MicFailed, translation = it.translation.copy(micPhase = MicPhase.Idle)) }
            return
        }
        if (!transcribe || wav.isEmpty()) {
            mutate { it.copy(translation = it.translation.copy(micPhase = MicPhase.Idle)) }
            return
        }
        mutate {
            it.copy(
                translation = it.translation.copy(
                    micPhase = MicPhase.Processing,
                    targetText = "",
                    alternatives = emptyList(),
                    status = TranslationStatus.WaitingEngine,
                    error = null,
                ),
            )
        }
        val s = _state.value
        cancelIdleRelease()
        val result = translator.translate(
            TranslationRequest(
                text = "",
                sourceLang = s.translation.sourceLang,
                targetLang = s.translation.targetLang,
                modelPath = s.data.model.path,
                audioWav = wav,
            ),
        )
        applyReadResult(result, failMessage = AppMessage.MicFailed)
        scheduleIdleRelease()
    }

    private fun translateImage(bytes: ByteArray? = null) {
        val s = _state.value
        if (!s.data.model.installed || s.translation.micPhase != MicPhase.Idle || s.translation.imageBusy) return
        recordJob?.cancel()
        recordJob = scope.launch {
            val image: ByteArray? = if (bytes != null) {
                bytes.takeIf { it.isNotEmpty() }
            } else {
                try {
                    imagePicker.pick()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            if (image == null || image.isEmpty()) return@launch
            mutate {
                it.copy(
                    translation = it.translation.copy(
                        imageBusy = true,
                        sourceText = "",
                        targetText = "",
                        alternatives = emptyList(),
                        status = TranslationStatus.WaitingEngine,
                        error = null,
                    ),
                )
            }
            val current = _state.value
            cancelIdleRelease()
            val result = translator.translate(
                TranslationRequest(
                    text = "",
                    sourceLang = current.translation.sourceLang,
                    targetLang = current.translation.targetLang,
                    modelPath = current.data.model.path,
                    image = image,
                ),
            )
            // The target panel already shows the failure; a toast about the mic would be a lie.
            applyReadResult(result, failMessage = null)
            scheduleIdleRelease()
        }
    }

    /** Dictation and image share the shape: the model returns the source text and its translation. */
    private fun applyReadResult(result: TranslationResult, failMessage: AppMessage?) {
        mutate { current ->
            val t = current.translation.copy(micPhase = MicPhase.Idle, imageBusy = false)
            when (result) {
                TranslationResult.Unavailable -> current.copy(
                    translation = t.copy(status = TranslationStatus.WaitingEngine, targetText = ""),
                )

                is TranslationResult.Error -> current.copy(
                    translation = t.copy(status = TranslationStatus.Error, error = result.message),
                    message = failMessage ?: current.message,
                )

                is TranslationResult.Ok -> current.copy(
                    translation = t.copy(
                        sourceText = GemmaModel.capInput(result.transcription.ifBlank { result.text }),
                        targetText = result.text,
                        status = TranslationStatus.Ready,
                        latencyMs = result.latencyMs,
                        error = null,
                    ),
                )
            }
        }
    }

    private fun scheduleTranslate() {
        val snapshot = _state.value.translation
        if (snapshot.sourceText.isBlank()) {
            cancelTranslateJobs()
            mutate {
                it.copy(
                    translation = it.translation.copy(
                        targetText = "",
                        alternatives = emptyList(),
                        status = TranslationStatus.Idle,
                        error = null,
                    ),
                )
            }
            return
        }
        cancelIdleRelease()
        translateDebounceJob?.cancel()
        translateDebounceJob = scope.launch {
            if (translateDelayMs > 0) delay(translateDelayMs)
            if (translating) return@launch
            val t = _state.value.translation
            val text = t.sourceText
            val from = t.sourceLang
            val to = t.targetLang
            if (text.isBlank()) return@launch
            translateJob = scope.launch {
                translating = true
                try {
                    runTranslation(text, from, to)
                } finally {
                    translating = false
                }
                val next = _state.value.translation
                if (next.sourceText.isNotBlank() &&
                    (next.sourceText != text || next.sourceLang != from || next.targetLang != to)
                ) {
                    scheduleTranslate()
                }
            }
        }
    }

    private suspend fun runTranslation(text: String, from: String, to: String) {
        val s = _state.value
        if (!sameTranslateInput(s.translation, text, from, to)) return
        mutate {
            if (!sameTranslateInput(it.translation, text, from, to)) it
            else {
                it.copy(
                    translation = it.translation.copy(
                        targetText = "",
                        alternatives = emptyList(),
                        highlightTerm = "",
                        alternativesFor = "",
                        selectedAlternative = "",
                        status = TranslationStatus.WaitingEngine,
                        error = null,
                        latencyMs = null,
                    ),
                )
            }
        }
        val result = translator.translate(
            TranslationRequest(
                text = text,
                sourceLang = from,
                targetLang = to,
                modelPath = s.data.model.path,
                onPartial = { partial ->
                    mutate { current ->
                        if (!sameTranslateInput(current.translation, text, from, to)) current
                        else {
                            current.copy(
                                translation = current.translation.copy(
                                    targetText = partial,
                                    status = TranslationStatus.WaitingEngine,
                                    error = null,
                                ),
                            )
                        }
                    }
                },
            ),
        )
        mutate { current ->
            val t = current.translation
            if (!sameTranslateInput(t, text, from, to)) current
            else {
                when (result) {
                    TranslationResult.Unavailable -> t.copy(
                        status = TranslationStatus.WaitingEngine,
                        targetText = "",
                        alternatives = emptyList(),
                        error = null,
                        latencyMs = null,
                    )

                    is TranslationResult.Error -> t.copy(
                        status = TranslationStatus.Error,
                        error = result.message,
                    )

                    is TranslationResult.Ok -> t.copy(
                        targetText = result.text,
                        alternatives = result.alternatives,
                        highlightTerm = result.highlight,
                        alternativesFor = result.highlight,
                        selectedAlternative = result.highlight,
                        status = TranslationStatus.Ready,
                        latencyMs = result.latencyMs,
                        error = null,
                    )
                }.let { current.copy(translation = it) }
            }
        }
        if (sameTranslateInput(_state.value.translation, text, from, to)) scheduleIdleRelease()
    }

    private fun sameTranslateInput(t: TranslationState, text: String, from: String, to: String) =
        t.sourceText == text && t.sourceLang == from && t.targetLang == to

    private fun scheduleProofread() {
        if (_state.value.proofread.text.isBlank()) {
            cancelProofreadJobs()
            mutate { it.copy(proofread = it.proofread.copy(result = "", status = TranslationStatus.Idle, error = null, latencyMs = null)) }
            return
        }
        cancelIdleRelease()
        proofreadDebounceJob?.cancel()
        proofreadDebounceJob = scope.launch {
            if (translateDelayMs > 0) delay(translateDelayMs)
            if (proofreading) return@launch
            val text = _state.value.proofread.text
            if (text.isBlank()) return@launch
            proofreadJob = scope.launch {
                proofreading = true
                try {
                    runProofread(text)
                } finally {
                    proofreading = false
                }
                val next = _state.value.proofread.text
                if (next.isNotBlank() && next != text) scheduleProofread()
            }
        }
    }

    private suspend fun runProofread(text: String) {
        val s = _state.value
        if (s.proofread.text != text) return
        mutate {
            if (it.proofread.text != text) it
            else it.copy(proofread = it.proofread.copy(result = "", status = TranslationStatus.WaitingEngine, error = null, latencyMs = null))
        }
        val result = translator.translate(
            TranslationRequest(
                text = text,
                sourceLang = s.translation.sourceLang,
                targetLang = s.translation.sourceLang,
                modelPath = s.data.model.path,
                mode = TranslationMode.Proofread,
                onPartial = { partial ->
                    mutate {
                        if (it.proofread.text != text) it
                        else it.copy(proofread = it.proofread.copy(result = partial, status = TranslationStatus.WaitingEngine, error = null))
                    }
                },
            ),
        )
        mutate { current ->
            val p = current.proofread
            if (p.text != text) current
            else {
                when (result) {
                    TranslationResult.Unavailable -> p.copy(
                        status = TranslationStatus.WaitingEngine,
                        result = "",
                        error = null,
                        latencyMs = null,
                    )

                    is TranslationResult.Error -> p.copy(status = TranslationStatus.Error, error = result.message)

                    is TranslationResult.Ok -> p.copy(
                        result = result.text,
                        status = TranslationStatus.Ready,
                        latencyMs = result.latencyMs,
                        error = null,
                    )
                }.let { current.copy(proofread = it) }
            }
        }
        if (_state.value.proofread.text == text) scheduleIdleRelease()
    }

    private fun persist(now: Boolean = false) {
        saveJob?.cancel()
        val snapshot = _state.value.data.copy(history = emptyList())
        if (now) {
            store.save(snapshot)
            return
        }
        saveJob = scope.launch {
            delay(200)
            store.save(snapshot)
        }
    }

    private fun mutate(block: (AppState) -> AppState) = _state.update(block)

    private fun AppState.gotoInstall(step: InstallStep): AppState = copy(
        data = data.copy(installStep = step.name),
    )

    private fun AppState.withLangs(source: String, target: String): AppState = copy(
        translation = translation.copy(sourceLang = source, targetLang = target),
        data = data.copy(lastSourceLang = source, lastTargetLang = target),
    )

    private fun AppState.updateSettings(
        block: (dev.nucleusframework.offlinetranslator.domain.UserSettings) -> dev.nucleusframework.offlinetranslator.domain.UserSettings,
    ): AppState = copy(data = data.copy(settings = block(data.settings)))
}

private fun InstallStep.previous(): InstallStep = InstallStep.entries.getOrElse(ordinal - 1) { this }

fun AppState.visibleHistory(): List<HistoryItem> = filterHistory(data.history, historyQuery, historyFilter, Platform.now())
