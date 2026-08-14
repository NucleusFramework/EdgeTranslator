package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.app.AppDialog
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.app.AppKey
import dev.nucleusframework.offlinetranslator.app.AppViewModel
import dev.nucleusframework.offlinetranslator.app.ConfirmAction
import dev.nucleusframework.offlinetranslator.app.visibleHistory
import dev.nucleusframework.offlinetranslator.data.MemoryHistoryStore
import dev.nucleusframework.offlinetranslator.data.MemoryStore
import dev.nucleusframework.offlinetranslator.data.decodeSnapshot
import dev.nucleusframework.offlinetranslator.data.encodeSnapshot
import dev.nucleusframework.offlinetranslator.data.seedData
import dev.nucleusframework.offlinetranslator.domain.AUTO_LANG
import dev.nucleusframework.offlinetranslator.domain.DownloadLog
import dev.nucleusframework.offlinetranslator.domain.DownloadPhase
import dev.nucleusframework.offlinetranslator.domain.HistoryFilter
import dev.nucleusframework.offlinetranslator.domain.HistoryItem
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.LangRole
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.domain.LlmKeepAlive
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.MODEL_IDLE_RELEASE_MS
import dev.nucleusframework.offlinetranslator.domain.ModelInfo
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.paragraphCount
import dev.nucleusframework.offlinetranslator.engine.CatalogModel
import dev.nucleusframework.offlinetranslator.engine.GemmaModel
import dev.nucleusframework.offlinetranslator.engine.DownloadedModel
import dev.nucleusframework.offlinetranslator.engine.IdleDownloader
import dev.nucleusframework.offlinetranslator.engine.ImagePicker
import dev.nucleusframework.offlinetranslator.engine.MicRecorder
import dev.nucleusframework.offlinetranslator.engine.ModelDownloader
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.engine.SilentMic
import dev.nucleusframework.offlinetranslator.engine.SilentTts
import dev.nucleusframework.offlinetranslator.engine.TranslationMode
import dev.nucleusframework.offlinetranslator.engine.TranslationRequest
import dev.nucleusframework.offlinetranslator.engine.TranslationResult
import dev.nucleusframework.offlinetranslator.engine.Translator
import dev.nucleusframework.offlinetranslator.engine.TtsSpeaker
import dev.nucleusframework.offlinetranslator.engine.UnavailableTranslator
import dev.nucleusframework.offlinetranslator.engine.buildTranslationPrompt
import dev.nucleusframework.offlinetranslator.engine.cleanModelOutput
import dev.nucleusframework.offlinetranslator.engine.parseImageOutput
import dev.nucleusframework.offlinetranslator.engine.parseSpeechOutput
import dev.nucleusframework.offlinetranslator.engine.pcm16leToWav
import dev.nucleusframework.offlinetranslator.engine.restoreBmpSafe
import dev.nucleusframework.offlinetranslator.engine.toBmpSafe
import dev.nucleusframework.offlinetranslator.translation.MicPhase
import dev.nucleusframework.offlinetranslator.translation.TranslationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppViewModelTest {

    private fun vm(
        store: MemoryStore = MemoryStore(seedData()),
        history: MemoryHistoryStore = MemoryHistoryStore(),
        now: Long = 1_700_000_000_000L,
        translator: Translator = UnavailableTranslator,
        downloader: ModelDownloader = IdleDownloader,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        translateDelayMs: Long = 350,
        idleReleaseMs: Long = MODEL_IDLE_RELEASE_MS,
        mic: MicRecorder = SilentMic,
        pickImage: ImagePicker = ImagePicker { null },
        tts: TtsSpeaker = SilentTts,
        modelOnDisk: (CatalogModel) -> Boolean = { false },
        modelOwnedByApp: (CatalogModel) -> Boolean = { false },
        deleteModelFiles: (CatalogModel) -> Unit = {},
        voicesOnDisk: () -> Set<String> = { emptySet() },
        voiceOnDisk: (dev.nucleusframework.offlinetranslator.engine.PiperVoiceSpec) -> Boolean = { false },
        deleteVoiceFiles: (String) -> Unit = {},
        wipeDownloadDirs: () -> Unit = {},
        migrateVoices: () -> Unit = {},
        hostRamBytes: () -> Long = { 32L * 1_073_741_824L },
    ): AppViewModel = AppViewModel(
        store = store,
        historyStore = history,
        translator = translator,
        downloader = downloader,
        dispatcher = dispatcher,
        ioDispatcher = dispatcher,
        clock = { now },
        translateDelayMs = translateDelayMs,
        idleReleaseMs = idleReleaseMs,
        mic = mic,
        imagePicker = pickImage,
        tts = tts,
        modelOnDisk = modelOnDisk,
        modelOwnedByApp = modelOwnedByApp,
        deleteModelFiles = deleteModelFiles,
        voicesOnDisk = voicesOnDisk,
        voiceOnDisk = voiceOnDisk,
        deleteVoiceFiles = deleteVoiceFiles,
        wipeDownloadDirs = wipeDownloadDirs,
        migrateVoices = migrateVoices,
        hostRamBytes = hostRamBytes,
    )

    @Test
    fun swapExchangesLanguages() {
        val vm = vm()
        vm.onIntent(AppIntent.ChooseLanguage("fr", LangRole.Source))
        vm.onIntent(AppIntent.ChooseLanguage("en", LangRole.Target))
        vm.onIntent(AppIntent.SwapLanguages)
        val after = vm.state.value.translation
        assertEquals("en", after.sourceLang)
        assertEquals("fr", after.targetLang)
    }

    @Test
    fun installFlowAdvancesAndOpensApp() {
        val vm = vm()
        assertEquals(AppKey.Welcome, vm.backStack.last())
        vm.onIntent(AppIntent.StartInstall)
        assertEquals(AppKey.Download, vm.backStack.last())
        vm.onIntent(AppIntent.CompleteDownload)
        assertTrue(vm.state.value.data.model.installed)
        vm.onIntent(AppIntent.OpenApp)
        assertEquals(AppKey.Translate, vm.backStack.last())
        assertTrue(vm.state.value.data.installed)
    }

    @Test
    fun downloadPipelineCompletesThroughVerifyAndIndex() {
        val downloader = ModelDownloader { dest, _, sha, bytes, onConnect, onVerify, onProgress ->
            onConnect()
            onProgress(bytes, bytes, 0, DownloadLog.Transfer)
            onVerify()
            DownloadedModel(dest, sha, bytes)
        }
        val vm = vm(downloader = downloader, dispatcher = Dispatchers.Unconfined)
        vm.onIntent(AppIntent.StartInstall)
        assertEquals(DownloadPhase.Done, vm.state.value.download.phase)
        assertTrue(vm.state.value.data.model.installed)
        assertTrue(vm.state.value.download.done)
    }

    @Test
    fun installBackPopsStack() {
        val vm = vm()
        vm.onIntent(AppIntent.StartInstall)
        assertEquals(listOf(AppKey.Welcome, AppKey.Download), vm.backStack.toList())
        vm.onIntent(AppIntent.InstallBack)
        assertEquals(listOf(AppKey.Welcome), vm.backStack.toList())
    }

    @Test
    fun navigateSwitchesDestination() {
        val vm = vm()
        vm.onIntent(AppIntent.Navigate(AppKey.History))
        assertEquals(AppKey.History, vm.backStack.last())
    }

    @Test
    fun sourceTextUpdatesCounts() {
        val vm = vm()
        val text = "Bonjour.\n\nDeuxième paragraphe."
        vm.onIntent(AppIntent.SetSourceText(text))
        val t = vm.state.value.translation
        assertEquals(text.length, t.sourceChars)
        assertEquals(2, t.sourceParagraphs)
    }

    @Test
    fun sourceTextIsCapped() {
        val vm = vm()
        val over = "a".repeat(GemmaModel.MAX_INPUT_CHARS + 50)
        vm.onIntent(AppIntent.SetSourceText(over))
        assertEquals(GemmaModel.MAX_INPUT_CHARS, vm.state.value.translation.sourceText.length)
    }

    @Test
    fun lastLanguagesAreRemembered() {
        val store = MemoryStore(seedData())
        val vm = vm(store = store)
        vm.onIntent(AppIntent.ChooseLanguage("de", LangRole.Source))
        vm.onIntent(AppIntent.ChooseLanguage("fr", LangRole.Target))
        val loaded = store.load()
        assertEquals("de", loaded.lastSourceLang)
        assertEquals("fr", loaded.lastTargetLang)
    }

    @Test
    fun saveToHistoryCreatesEntry() {
        val translator = Translator {
            TranslationResult.Ok("confidentiality clause", highlight = "clause")
        }
        val vm = vm(translator = translator, dispatcher = Dispatchers.Unconfined, now = 1L, translateDelayMs = 0)
        vm.onIntent(AppIntent.SetSourceText("clause de confidentialité"))
        assertEquals("confidentiality clause", vm.state.value.translation.targetText)
        vm.onIntent(AppIntent.SaveToHistory)
        assertEquals(1, vm.state.value.data.history.size)
        assertEquals("clause de confidentialité", vm.state.value.data.history.first().sourceText)
        assertTrue(vm.state.value.translation.saved)
        vm.onIntent(AppIntent.SetSourceText("autre texte"))
        assertFalse(vm.state.value.translation.saved)
    }

    @Test
    fun proofreadUsesProofreadModeAndApplyReplacesInput() {
        val translator = Translator { req ->
            assertEquals(TranslationMode.Proofread, req.mode)
            TranslationResult.Ok("corrigé:${req.text}")
        }
        val vm = vm(translator = translator, dispatcher = Dispatchers.Unconfined, now = 1L, translateDelayMs = 0)
        vm.onIntent(AppIntent.SetProofreadText("bonjour"))
        assertEquals("corrigé:bonjour", vm.state.value.proofread.result)
        assertEquals(TranslationStatus.Ready, vm.state.value.proofread.status)
        vm.onIntent(AppIntent.ApplyProofread)
        assertEquals("corrigé:bonjour", vm.state.value.proofread.text)
        vm.onIntent(AppIntent.CopyProofread)
        assertTrue(vm.state.value.proofread.copied)
        vm.onIntent(AppIntent.SetProofreadText(""))
        assertEquals("", vm.state.value.proofread.result)
        assertEquals(TranslationStatus.Idle, vm.state.value.proofread.status)
    }

    @Test
    fun copyTranslationMarksCopiedUntilTargetChanges() {
        val translator = Translator { req -> TranslationResult.Ok("t:${req.text}") }
        val vm = vm(translator = translator, dispatcher = Dispatchers.Unconfined, now = 1L, translateDelayMs = 0)
        vm.onIntent(AppIntent.CopyTranslation)
        assertFalse(vm.state.value.translation.copied)
        assertEquals(null, vm.state.value.message)
        vm.onIntent(AppIntent.SetSourceText("bonjour"))
        assertEquals("t:bonjour", vm.state.value.translation.targetText)
        vm.onIntent(AppIntent.CopyTranslation)
        assertTrue(vm.state.value.translation.copied)
        assertEquals(null, vm.state.value.message)
        vm.onIntent(AppIntent.SetSourceText("autre"))
        assertEquals("t:autre", vm.state.value.translation.targetText)
        assertFalse(vm.state.value.translation.copied)
    }

    @Test
    fun historySearchAndPin() {
        val history = MemoryHistoryStore(
            listOf(
                HistoryItem("1", 10, "fr", "en", "contrat secret", "secret contract", pinned = false),
                HistoryItem("2", 20, "en", "fr", "hello", "bonjour", pinned = false),
            ),
        )
        val vm = vm(history = history, now = 30)
        vm.onIntent(AppIntent.SetHistoryQuery("contrat"))
        assertEquals(1, vm.state.value.visibleHistory().size)
        vm.onIntent(AppIntent.ToggleHistoryPin("2"))
        assertTrue(vm.state.value.data.history.first { it.id == "2" }.pinned)
        vm.onIntent(AppIntent.OpenHistory("1"))
        assertEquals(AppKey.Translate, vm.backStack.last())
        assertEquals("contrat secret", vm.state.value.translation.sourceText)
    }

    @Test
    fun deleteAndClearHistory() {
        val history = MemoryHistoryStore(
            listOf(
                HistoryItem("1", 10, "fr", "en", "a", "b", pinned = true),
                HistoryItem("2", 20, "en", "fr", "c", "d", pinned = false),
            ),
        )
        val vm = vm(history = history, now = 30)
        vm.onIntent(AppIntent.DeleteHistory("2"))
        assertEquals(listOf("1"), vm.state.value.data.history.map { it.id })
        vm.onIntent(AppIntent.ClearHistory)
        assertEquals(ConfirmAction.PurgeHistory, (vm.state.value.dialog as AppDialog.Confirm).action)
        vm.onIntent(AppIntent.ConfirmDialog)
        assertTrue(vm.state.value.data.history.isEmpty())
        assertTrue(history.all().isEmpty())
    }

    @Test
    fun settingsPersistInStore() {
        val store = MemoryStore(seedData())
        val vm = vm(store = store, now = 1L)
        vm.onIntent(AppIntent.SelectModel(LlmModel.Precise))
        vm.onIntent(AppIntent.SetUiLanguage(UiLanguage.En))
        vm.onIntent(AppIntent.SetLangNameStyle(LangNameStyle.Native))
        vm.onIntent(AppIntent.SetLlmBackend(LlmBackend.Cpu))
        vm.onIntent(AppIntent.SetLlmKeepAlive(LlmKeepAlive.AlwaysOn))
        vm.onIntent(AppIntent.SetMtp(true))
        val loaded = store.load()
        assertEquals(LlmModel.Precise, loaded.settings.selectedModel)
        assertEquals(UiLanguage.En, loaded.settings.uiLanguage)
        assertEquals(LangNameStyle.Native, loaded.settings.langNames)
        assertEquals(LlmBackend.Cpu, loaded.settings.backend)
        assertEquals(LlmKeepAlive.AlwaysOn, loaded.settings.keepAlive)
        assertTrue(loaded.settings.mtp)
        vm.onIntent(AppIntent.SetMtp(false))
    }

    @Test
    fun completeDownloadUsesSelectedModel() {
        val vm = vm()
        vm.onIntent(AppIntent.SelectModel(LlmModel.Precise))
        vm.onIntent(AppIntent.CompleteDownload)
        val model = vm.state.value.data.model
        assertTrue(model.installed)
        assertEquals(LlmModel.Precise, model.id)
        assertEquals("Gemma 4 E4B IT", model.name)
    }

    @Test
    fun deleteModelClearsInstallAfterConfirm() {
        val store = MemoryStore(seedData())
        val removed = mutableListOf<LlmModel>()
        val vm = vm(store = store, deleteModelFiles = { removed += it.id })
        vm.onIntent(AppIntent.CompleteDownload)
        assertTrue(vm.state.value.data.model.installed)
        vm.onIntent(AppIntent.DeleteModel(LlmModel.Fast))
        val dialog = assertIs<AppDialog.Confirm>(vm.state.value.dialog)
        assertEquals(ConfirmAction.DeleteModel(LlmModel.Fast), dialog.action)
        vm.onIntent(AppIntent.ConfirmDialog)
        assertEquals(AppDialog.Hidden, vm.state.value.dialog)
        assertFalse(vm.state.value.data.model.installed)
        assertEquals("", vm.state.value.data.model.path)
        assertEquals(listOf(LlmModel.Fast), removed)
        assertFalse(store.load().model.installed)
    }

    @Test
    fun deleteModelFallsBackToOtherInstalledModel() {
        val onDisk = mutableSetOf(LlmModel.Fast, LlmModel.Precise)
        val vm = vm(
            modelOnDisk = { it.id in onDisk },
            deleteModelFiles = { onDisk.remove(it.id) },
        )
        vm.onIntent(AppIntent.CompleteDownload)
        vm.onIntent(AppIntent.DeleteModel(LlmModel.Fast))
        vm.onIntent(AppIntent.ConfirmDialog)
        assertTrue(vm.state.value.data.model.installed)
        assertEquals(LlmModel.Precise, vm.state.value.data.model.id)
        assertEquals(LlmModel.Precise, vm.state.value.data.settings.selectedModel)
        assertEquals(setOf(LlmModel.Precise), onDisk)
    }

    @Test
    fun selectingMissingModelAfterInstallDoesNotStartDownload() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Fast),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Fast },
        )
        vm.onIntent(AppIntent.SelectModel(LlmModel.Precise))
        val state = vm.state.value
        assertEquals(LlmModel.Fast, state.data.settings.selectedModel)
        assertEquals(LlmModel.Fast, state.data.model.id)
        assertTrue(state.data.model.installed)
        assertFalse(state.download.running)
    }

    @Test
    fun downloadModelStartsDownload() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Fast),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Fast },
        )
        vm.onIntent(AppIntent.DownloadModel(LlmModel.Precise))
        val state = vm.state.value
        assertEquals(LlmModel.Precise, state.data.settings.selectedModel)
        assertEquals(LlmModel.Fast, state.data.model.id)
        assertTrue(state.data.model.installed)
        assertTrue(state.download.running)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun pauseAndCancelDownloadFromSettings() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Fast),
                ),
            ),
            dispatcher = UnconfinedTestDispatcher(),
            modelOnDisk = { it.id == LlmModel.Fast },
        )
        vm.onIntent(AppIntent.DownloadModel(LlmModel.Precise))
        assertTrue(vm.state.value.download.running)
        vm.onIntent(AppIntent.PauseDownload)
        assertTrue(vm.state.value.download.paused)
        assertFalse(vm.state.value.download.running)
        vm.onIntent(AppIntent.ResumeDownload)
        assertTrue(vm.state.value.download.running)
        vm.onIntent(AppIntent.CancelDownload)
        assertEquals(DownloadPhase.Done, vm.state.value.download.phase)
        assertFalse(vm.state.value.download.running)
        assertFalse(vm.state.value.download.paused)
        assertEquals(LlmModel.Fast, vm.state.value.data.model.id)
        assertEquals(LlmModel.Fast, vm.state.value.data.settings.selectedModel)
    }

    @Test
    fun restoringCancelledSettingsDownloadIsIdle() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Fast),
                    settings = seedData().settings.copy(selectedModel = LlmModel.Precise),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Fast },
        )
        val state = vm.state.value
        assertEquals(LlmModel.Fast, state.data.settings.selectedModel)
        assertFalse(state.download.running)
        assertFalse(state.download.paused)
        assertTrue(state.download.done)
    }

    @Test
    fun restoringInstalledModelIsNotDownloading() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Precise),
                    settings = seedData().settings.copy(selectedModel = LlmModel.Precise),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Precise },
        )
        assertFalse(vm.state.value.download.running)
        assertTrue(vm.state.value.download.done)
    }

    @Test
    fun startInstallBlockedUnderEightGigabytes() {
        val vm = vm(hostRamBytes = { 6L * 1_073_741_824L })
        vm.onIntent(AppIntent.StartInstall)
        assertEquals(AppKey.Welcome, vm.backStack.last())
        assertEquals("Welcome", vm.state.value.data.installStep)
    }

    @Test
    fun startInstallAllowedAtEightGigabytes() {
        val vm = vm(hostRamBytes = { 8L * 1_073_741_824L })
        vm.onIntent(AppIntent.StartInstall)
        assertEquals(AppKey.Download, vm.backStack.last())
    }

    @Test
    fun precisionSelectionBlockedUnderSixteenGigabytes() {
        val vm = vm(hostRamBytes = { 12L * 1_073_741_824L })
        vm.onIntent(AppIntent.SelectModel(LlmModel.Precise))
        assertEquals(LlmModel.Fast, vm.state.value.data.settings.selectedModel)
    }

    @Test
    fun precisionDownloadBlockedUnderSixteenGigabytes() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Fast),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Fast },
            hostRamBytes = { 12L * 1_073_741_824L },
        )
        vm.onIntent(AppIntent.DownloadModel(LlmModel.Precise))
        assertEquals(LlmModel.Fast, vm.state.value.data.settings.selectedModel)
        assertFalse(vm.state.value.download.running)
    }

    @Test
    fun onboardingDropsPrecisionWhenRamIsTooLow() {
        val vm = vm(
            store = MemoryStore(seedData().copy(settings = seedData().settings.copy(selectedModel = LlmModel.Precise))),
            hostRamBytes = { 12L * 1_073_741_824L },
        )
        assertEquals(LlmModel.Fast, vm.state.value.data.settings.selectedModel)
    }

    @Test
    fun installedPrecisionStaysSelectedWhenRamIsLow() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    model = seedData().model.copy(installed = true, id = LlmModel.Precise),
                    settings = seedData().settings.copy(selectedModel = LlmModel.Precise),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Precise },
            hostRamBytes = { 12L * 1_073_741_824L },
        )
        assertEquals(LlmModel.Precise, vm.state.value.data.settings.selectedModel)
        assertEquals(LlmModel.Precise, vm.state.value.data.model.id)
    }

    @Test
    fun unknownRamDoesNotBlockPrecision() {
        val vm = vm(hostRamBytes = { 0L })
        vm.onIntent(AppIntent.SelectModel(LlmModel.Precise))
        assertEquals(LlmModel.Precise, vm.state.value.data.settings.selectedModel)
    }

    @Test
    fun selectAlternativeReplacesTerm() {
        val translator = Translator {
            TranslationResult.Ok("Any amendment to these stipulations.", highlight = "stipulations")
        }
        val vm = vm(translator = translator, dispatcher = Dispatchers.Unconfined, now = 1L, translateDelayMs = 0)
        vm.onIntent(AppIntent.SetSourceText("Toute modification des stipulations"))
        assertEquals("stipulations", vm.state.value.translation.highlightTerm)
        vm.onIntent(AppIntent.SelectAlternative("provisions"))
        assertEquals("Any amendment to these provisions.", vm.state.value.translation.targetText)
    }

    @Test
    fun translationStreamsPartialText() {
        lateinit var vm: AppViewModel
        val translator = Translator { request ->
            request.onPartial("Hel")
            assertEquals("Hel", vm.state.value.translation.targetText)
            assertEquals(TranslationStatus.WaitingEngine, vm.state.value.translation.status)
            request.onPartial("Hello")
            assertEquals("Hello", vm.state.value.translation.targetText)
            TranslationResult.Ok("Hello world")
        }
        vm = vm(translator = translator, dispatcher = Dispatchers.Unconfined, now = 1L, translateDelayMs = 0)
        vm.onIntent(AppIntent.SetSourceText("bonjour"))
        assertEquals("Hello world", vm.state.value.translation.targetText)
        assertEquals(TranslationStatus.Ready, vm.state.value.translation.status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun typingDuringDebounceTranslatesOnce() = runTest {
        val seen = mutableListOf<String>()
        val translator = Translator { req ->
            seen += req.text
            TranslationResult.Ok("t:${req.text}")
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = vm(translator = translator, dispatcher = dispatcher, translateDelayMs = 350)
        vm.onIntent(AppIntent.SetSourceText("B"))
        testScheduler.advanceTimeBy(100)
        vm.onIntent(AppIntent.SetSourceText("Bo"))
        testScheduler.advanceTimeBy(100)
        vm.onIntent(AppIntent.SetSourceText("Bon"))
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        assertEquals(listOf("Bon"), seen)
        assertEquals("t:Bon", vm.state.value.translation.targetText)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun typingDoesNotRestartInFlightTranslation() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val seen = mutableListOf<String>()
        val translator = Translator { req ->
            seen += req.text
            gate.await()
            TranslationResult.Ok("t:${req.text}")
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = vm(translator = translator, dispatcher = dispatcher, translateDelayMs = 350)
        vm.onIntent(AppIntent.SetSourceText("Bon"))
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        assertEquals(listOf("Bon"), seen)

        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals(listOf("Bon"), seen)

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(TranslationStatus.WaitingEngine, vm.state.value.translation.status)

        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        assertEquals(listOf("Bon", "Bonjour"), seen)
        assertEquals("t:Bonjour", vm.state.value.translation.targetText)
        assertEquals(TranslationStatus.Ready, vm.state.value.translation.status)
    }

    @Test
    fun newTranslationClearsDraft() {
        val vm = vm()
        vm.onIntent(AppIntent.SetSourceText("bonjour"))
        vm.onIntent(AppIntent.NewTranslation)
        assertEquals("", vm.state.value.translation.sourceText)
        assertEquals(AppKey.Translate, vm.backStack.last())
    }

    @Test
    fun paragraphCountSplitsBlankLines() {
        assertEquals(0, paragraphCount("   "))
        assertEquals(2, paragraphCount("a\n\nb"))
    }

    @Test
    fun installedAppSkipsWizard() {
        val data = seedData().copy(
            installed = true,
            model = seedData().model.copy(installed = true),
        )
        val vm = vm(store = MemoryStore(data), now = 1L)
        assertEquals(AppKey.Translate, vm.backStack.last())
    }

    @Test
    fun forceOnboardingOpensWelcomeWhenInstalled() {
        val store = MemoryStore(
            seedData().copy(
                installed = true,
                model = seedData().model.copy(installed = true),
            ),
        )
        val vm = AppViewModel(
            store = store,
            historyStore = MemoryHistoryStore(),
            translator = UnavailableTranslator,
            downloader = IdleDownloader,
            dispatcher = Dispatchers.Default,
            clock = { 1L },
            forceOnboarding = true,
        )
        assertEquals(AppKey.Welcome, vm.backStack.last())
        assertFalse(vm.state.value.data.installed)
        assertTrue(store.load().installed)
    }

    @Test
    fun snapshotRoundTrip() {
        val original = seedData().copy(
            installed = true,
            settings = seedData().settings.copy(
                selectedModel = LlmModel.Precise,
                langNames = LangNameStyle.Native,
                backend = LlmBackend.Gpu,
                keepAlive = LlmKeepAlive.AlwaysOn,
                mtp = true,
            ),
            model = seedData().model.copy(id = LlmModel.Precise, installed = true),
        )
        val restored = decodeSnapshot(encodeSnapshot(original))
        assertEquals(original.installed, restored.installed)
        assertEquals(LlmModel.Precise, restored.settings.selectedModel)
        assertEquals(LangNameStyle.Native, restored.settings.langNames)
        assertEquals(LlmBackend.Gpu, restored.settings.backend)
        assertEquals(LlmKeepAlive.AlwaysOn, restored.settings.keepAlive)
        assertTrue(restored.settings.mtp)
        assertEquals(original.lastSourceLang, restored.lastSourceLang)
        assertEquals(original.lastTargetLang, restored.lastTargetLang)
        assertEquals(LlmModel.Precise, restored.model.id)
        assertEquals("Gemma 4 E4B IT", restored.model.name)
    }

    @Test
    fun uiLanguageAutoSurvivesRoundTripAndDefaultsOffForOlderSnapshots() {
        val auto = seedData().let { it.copy(settings = it.settings.copy(uiLanguageAuto = true)) }
        assertTrue(decodeSnapshot(encodeSnapshot(auto)).settings.uiLanguageAuto)

        val explicit = seedData().let {
            it.copy(settings = it.settings.copy(uiLanguage = UiLanguage.De, uiLanguageAuto = false))
        }
        val restored = decodeSnapshot(encodeSnapshot(explicit))
        assertFalse(restored.settings.uiLanguageAuto)
        assertEquals(UiLanguage.De, restored.settings.uiLanguage)

        // A snapshot written before the key existed holds an explicit pick — it must not start
        // following the OS language on upgrade.
        val legacy = encodeSnapshot(explicit).lineSequence().filterNot { it.startsWith("uiAuto=") }.joinToString("\n")
        assertFalse(decodeSnapshot(legacy).settings.uiLanguageAuto)
        assertEquals(UiLanguage.De, decodeSnapshot(legacy).settings.uiLanguage)
    }

    @Test
    fun llmBackendSurvivesRoundTripAndDefaultsToAutoForOlderSnapshots() {
        val gpu = seedData().let { it.copy(settings = it.settings.copy(backend = LlmBackend.Gpu)) }
        assertEquals(LlmBackend.Gpu, decodeSnapshot(encodeSnapshot(gpu)).settings.backend)

        val npu = seedData().let { it.copy(settings = it.settings.copy(backend = LlmBackend.Npu)) }
        assertEquals(LlmBackend.Npu, decodeSnapshot(encodeSnapshot(npu)).settings.backend)

        val legacy = encodeSnapshot(gpu).lineSequence().filterNot { it.startsWith("backend=") }.joinToString("\n")
        assertEquals(LlmBackend.Auto, decodeSnapshot(legacy).settings.backend)
    }

    @Test
    fun keepAliveSurvivesRoundTripAndDefaultsToOnDemandForOlderSnapshots() {
        val always = seedData().let { it.copy(settings = it.settings.copy(keepAlive = LlmKeepAlive.AlwaysOn)) }
        assertEquals(LlmKeepAlive.AlwaysOn, decodeSnapshot(encodeSnapshot(always)).settings.keepAlive)

        val legacy = encodeSnapshot(always).lineSequence().filterNot { it.startsWith("keepAlive=") }.joinToString("\n")
        assertEquals(LlmKeepAlive.OnDemand, decodeSnapshot(legacy).settings.keepAlive)
    }

    @Test
    fun mtpSurvivesRoundTripAndDefaultsOffForOlderSnapshots() {
        val on = seedData().let { it.copy(settings = it.settings.copy(mtp = true)) }
        assertTrue(decodeSnapshot(encodeSnapshot(on)).settings.mtp)

        val legacy = encodeSnapshot(on).lineSequence().filterNot { it.startsWith("mtp=") }.joinToString("\n")
        assertFalse(decodeSnapshot(legacy).settings.mtp)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onDemandDoesNotPreloadAtStartupAndReleasesAfterIdle() = runTest {
        val translator = TrackingTranslator()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = vm(
            store = MemoryStore(installedModel()),
            translator = translator,
            dispatcher = dispatcher,
            translateDelayMs = 0,
            idleReleaseMs = 5_000,
        )
        testScheduler.runCurrent()
        assertEquals(0, translator.preloads)

        vm.onIntent(AppIntent.SetSourceText("bonjour"))
        testScheduler.runCurrent()
        assertEquals(1, translator.translates)
        assertEquals(0, translator.releases)

        testScheduler.advanceTimeBy(4_999)
        testScheduler.runCurrent()
        assertEquals(0, translator.releases)
        testScheduler.advanceTimeBy(1)
        testScheduler.runCurrent()
        assertEquals(1, translator.releases)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun alwaysOnPreloadsAtStartupAndKeepsModelAfterIdle() = runTest {
        val translator = TrackingTranslator()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = vm(
            store = MemoryStore(installedModel(LlmKeepAlive.AlwaysOn)),
            translator = translator,
            dispatcher = dispatcher,
            translateDelayMs = 0,
            idleReleaseMs = 5_000,
        )
        testScheduler.runCurrent()
        assertEquals(1, translator.preloads)

        vm.onIntent(AppIntent.SetSourceText("bonjour"))
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(0, translator.releases)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingToAlwaysOnPreloadsAndSwitchingBackSchedulesRelease() = runTest {
        val translator = TrackingTranslator()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = vm(
            store = MemoryStore(installedModel()),
            translator = translator,
            dispatcher = dispatcher,
            translateDelayMs = 0,
            idleReleaseMs = 5_000,
        )
        testScheduler.runCurrent()
        assertEquals(0, translator.preloads)

        vm.onIntent(AppIntent.SetLlmKeepAlive(LlmKeepAlive.AlwaysOn))
        testScheduler.runCurrent()
        assertEquals(1, translator.preloads)
        assertEquals(0, translator.releases)

        vm.onIntent(AppIntent.SetLlmKeepAlive(LlmKeepAlive.OnDemand))
        testScheduler.runCurrent()
        assertEquals(1, translator.releases)
    }

    @Test
    fun historyFilterPinned() {
        val history = MemoryHistoryStore(
            listOf(
                HistoryItem("1", 10, "fr", "en", "a", "b", pinned = true),
                HistoryItem("2", 20, "fr", "en", "c", "d", pinned = false),
            ),
        )
        val vm = vm(history = history, now = 30)
        vm.onIntent(AppIntent.SetHistoryFilter(HistoryFilter.Pinned))
        assertEquals(listOf("1"), vm.state.value.visibleHistory().map { it.id })
    }

    @Test
    fun translationPromptMentionsLanguages() = runTest {
        val prompt = buildTranslationPrompt(
            dev.nucleusframework.offlinetranslator.engine.TranslationRequest(
                text = "tacite reconduction",
                sourceLang = "fr",
                targetLang = "en",
            ),
        )
        assertTrue(prompt.system.contains("French"))
        assertTrue(prompt.system.contains("English"))
        assertEquals("tacite reconduction", prompt.user)
    }

    @Test
    fun autoSourceUsesSameSystemPrompt() = runTest {
        val prompt = buildTranslationPrompt(
            dev.nucleusframework.offlinetranslator.engine.TranslationRequest(
                text = "hola",
                sourceLang = AUTO_LANG,
                targetLang = "en",
            ),
        )
        assertTrue(prompt.system.contains("professional translator"))
        assertTrue(prompt.system.contains("from any language to English"))
        assertEquals("hola", prompt.user)
    }

    @Test
    fun cleanModelOutputStripsFencesAndTokens() {
        val raw = "<|think|>reasoning<|think|>```\nHello world\n```"
        assertEquals("Hello world", cleanModelOutput(raw))
    }

    @Test
    fun bmpSafeStripsEmojiThenRestores() {
        val src = "🌟 Key Features\n📱 Cross-Platform"
        val safe = toBmpSafe(src)
        assertFalse(safe.text.any { it.isSurrogate() })
        assertTrue(safe.text.contains("[[#0]]"))
        assertTrue(safe.text.contains("Key Features"))
        assertEquals(src, restoreBmpSafe(safe.text, safe.extras))
    }

    @Test
    fun translationPromptShieldsEmoji() = runTest {
        val prompt = buildTranslationPrompt(
            dev.nucleusframework.offlinetranslator.engine.TranslationRequest(
                text = "🌟 Hello",
                sourceLang = "en",
                targetLang = "fr",
            ),
        )
        assertFalse(prompt.user.any { it.isSurrogate() })
        assertTrue(prompt.system.contains("[[#0]]"))
        assertEquals("🌟 Bonjour", prompt.restore("[[#0]] Bonjour"))
    }

    @Test
    fun wavHeaderIsRiff16kMono() {
        val wav = pcm16leToWav(ByteArray(3200))
        assertEquals("RIFF", wav.decodeToString(0, 4))
        assertEquals("WAVE", wav.decodeToString(8, 12))
        assertEquals(44 + 3200, wav.size)
        val rate = (wav[24].toInt() and 0xFF) or
            ((wav[25].toInt() and 0xFF) shl 8) or
            ((wav[26].toInt() and 0xFF) shl 16) or
            ((wav[27].toInt() and 0xFF) shl 24)
        assertEquals(16_000, rate)
    }

    @Test
    fun parseSpeechSplitsTranscriptionAndTranslation() {
        val (src, tgt) = parseSpeechOutput("Bonjour le monde\nEnglish: Hello world", "English")
        assertEquals("Bonjour le monde", src)
        assertEquals("Hello world", tgt)
    }

    @Test
    fun parseImageKeepsMultilineTextWhenOnlyOcr() {
        val (src, tgt) = parseImageOutput("Menu du jour\nSoupe à l'oignon\nEnglish: Soup of the day", "English")
        assertEquals("Menu du jour\nSoupe à l'oignon", src)
        assertEquals("Soup of the day", tgt)
        // No marker: same source and target language, the model only read the image.
        val (only, same) = parseImageOutput("Line one\nLine two", "English")
        assertEquals("Line one\nLine two", only)
        assertEquals(only, same)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun translateImageFillsSourceAndTarget() = runTest {
        var seen: ByteArray? = null
        val translator = Translator { request ->
            seen = request.image
            TranslationResult.Ok(text = "Stop", transcription = "Arrêt")
        }
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = translator,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            pickImage = { byteArrayOf(1, 2, 3) },
        )
        vm.onIntent(AppIntent.TranslateImage)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<Byte>(1, 2, 3), seen?.toList())
        assertEquals("Arrêt", vm.state.value.translation.sourceText)
        assertEquals("Stop", vm.state.value.translation.targetText)
        assertEquals(MicPhase.Idle, vm.state.value.translation.micPhase)
        assertFalse(vm.state.value.translation.imageBusy)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun droppedImageSkipsThePicker() = runTest {
        var seen: ByteArray? = null
        val translator = Translator { request ->
            seen = request.image
            TranslationResult.Ok(text = "Stop", transcription = "Arrêt")
        }
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = translator,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            pickImage = { error("picker must not run") },
        )
        vm.onIntent(AppIntent.DropUnsupported)
        assertIs<dev.nucleusframework.offlinetranslator.app.AppMessage.DropUnsupported>(vm.state.value.message)
        vm.onIntent(AppIntent.DismissMessage)

        vm.onIntent(AppIntent.TranslateDroppedImage(byteArrayOf(9, 8, 7)))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<Byte>(9, 8, 7), seen?.toList())
        assertEquals("Arrêt", vm.state.value.translation.sourceText)
        assertEquals("Stop", vm.state.value.translation.targetText)
        assertFalse(vm.state.value.translation.imageBusy)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun cancelledImagePickerLeavesTheTextAlone() = runTest {
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = Translator { TranslationResult.Ok("Hello") },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            pickImage = { null },
        )
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        testScheduler.advanceUntilIdle()
        vm.onIntent(AppIntent.TranslateImage)
        testScheduler.advanceUntilIdle()
        assertEquals("Bonjour", vm.state.value.translation.sourceText)
        assertEquals("Hello", vm.state.value.translation.targetText)
        assertEquals(MicPhase.Idle, vm.state.value.translation.micPhase)
        assertFalse(vm.state.value.translation.imageBusy)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun toggleMicFillsSourceAndTarget() = runTest {
        val translator = Translator {
            TranslationResult.Ok(text = "Hello", transcription = "Bonjour")
        }
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = translator,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            mic = FakeMic(ByteArray(4000)),
        )
        vm.onIntent(AppIntent.ToggleMic)
        testScheduler.advanceUntilIdle()
        assertEquals("Bonjour", vm.state.value.translation.sourceText)
        assertEquals("Hello", vm.state.value.translation.targetText)
        assertEquals(MicPhase.Idle, vm.state.value.translation.micPhase)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun toggleSpeakReadsTranslation() = runTest {
        val speaker = FakeTts()
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = Translator { TranslationResult.Ok("Hello") },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            tts = speaker,
            voicesOnDisk = { setOf("en") },
        )
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        testScheduler.advanceUntilIdle()
        vm.onIntent(AppIntent.ToggleSpeak(target = true))
        testScheduler.advanceUntilIdle()
        assertEquals("Hello", speaker.lastText)
        assertEquals("en", speaker.lastLang)
        assertTrue(vm.state.value.translation.ttsReady)
    }

    @Test
    fun micShowsStartingBeforeTheLineOpens() {
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            mic = FakeMic(ByteArray(0)),
        )
        vm.onIntent(AppIntent.ToggleMic)
        // Nothing has run on the VM scope yet: the pane must already be up.
        assertEquals(MicPhase.Starting, vm.state.value.translation.micPhase)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun listeningDoesNotRewriteAppState() = runTest {
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            mic = FakeMic(ByteArray(4000)),
        )
        vm.onIntent(AppIntent.ToggleMic)
        testScheduler.advanceTimeBy(100)
        val snap = vm.state.value
        assertEquals(MicPhase.Listening, snap.translation.micPhase)
        testScheduler.advanceTimeBy(1_000)
        assertSame(snap, vm.state.value)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun slowVoiceLoadShowsLoaderUntilAudioStarts() = runTest {
        val speaker = FakeTts().apply { loadMs = 2_000 }
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = Translator { TranslationResult.Ok("Hello") },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            tts = speaker,
            voicesOnDisk = { setOf("en") },
        )
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        testScheduler.advanceUntilIdle()
        vm.onIntent(AppIntent.ToggleSpeak(target = true))
        testScheduler.advanceTimeBy(100)
        assertFalse(vm.state.value.translation.speakLoading, "no loader before the grace delay")
        testScheduler.advanceTimeBy(400)
        assertTrue(vm.state.value.translation.speakLoading, "loader while the model loads")
        testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.translation.speakLoading, "loader gone once audio starts")
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun toggleSpeakPausesAndResumesPlayback() = runTest {
        val speaker = FakeTts().apply { holdUntilStop = true }
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = Translator { TranslationResult.Ok("Hello") },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            tts = speaker,
            voicesOnDisk = { setOf("en") },
        )
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        testScheduler.advanceUntilIdle()
        vm.onIntent(AppIntent.ToggleSpeak(target = true))
        testScheduler.runCurrent()
        assertTrue(vm.state.value.translation.speakPlaying)
        assertFalse(vm.state.value.translation.speakPaused)

        vm.onIntent(AppIntent.ToggleSpeak(target = true))
        assertTrue(speaker.paused)
        assertTrue(vm.state.value.translation.speakPaused)

        vm.onIntent(AppIntent.ToggleSpeak(target = true))
        assertFalse(speaker.paused)
        assertFalse(vm.state.value.translation.speakPaused)
        assertTrue(vm.state.value.translation.speakPlaying)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun stopSpeakEndsPlayback() = runTest {
        val speaker = FakeTts().apply { holdUntilStop = true }
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
            translator = Translator { TranslationResult.Ok("Hello") },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            translateDelayMs = 0,
            tts = speaker,
            voicesOnDisk = { setOf("en") },
        )
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        testScheduler.advanceUntilIdle()
        vm.onIntent(AppIntent.ToggleSpeak(target = true))
        testScheduler.runCurrent()
        assertTrue(vm.state.value.translation.speakPlaying)
        val stops = speaker.stops

        vm.onIntent(AppIntent.StopSpeak)
        testScheduler.runCurrent()
        assertEquals(stops + 1, speaker.stops)
        assertNull(vm.state.value.translation.speakTarget)
        assertFalse(vm.state.value.translation.speakBusy)
        assertFalse(vm.state.value.translation.speakPlaying)
        assertFalse(vm.state.value.translation.speakPaused)
        assertFalse(vm.state.value.translation.speakLoading)
    }

    @Test
    fun toggleSpeakWithoutVoiceShowsUnavailable() {
        val vm = vm(
            store = MemoryStore(seedData().copy(installed = true, model = seedData().model.copy(installed = true))),
        )
        vm.onIntent(AppIntent.SetSourceText("こんにちは"))
        vm.onIntent(AppIntent.ChooseLanguage("ja", LangRole.Source))
        vm.onIntent(AppIntent.ToggleSpeak(target = false))
        assertIs<dev.nucleusframework.offlinetranslator.app.AppMessage.TtsUnavailable>(vm.state.value.message)
        assertFalse(vm.state.value.translation.ttsReady)
    }

    @Test
    fun toggleSpeakWithoutInstalledVoiceOpensPopup() {
        val vm = vm(tts = FakeTts())
        vm.onIntent(AppIntent.ChooseLanguage("fr", LangRole.Source))
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        vm.onIntent(AppIntent.ToggleSpeak(target = false))
        val dialog = assertIs<AppDialog.InstallVoice>(vm.state.value.dialog)
        assertEquals("fr", dialog.lang)
    }

    @Test
    fun toggleSpeakDuringVoiceDownloadDoesNotReopenPopup() {
        val vm = vm(tts = FakeTts(), dispatcher = Dispatchers.Unconfined)
        vm.onIntent(AppIntent.ChooseLanguage("fr", LangRole.Source))
        vm.onIntent(AppIntent.SetSourceText("Bonjour"))
        vm.onIntent(AppIntent.ToggleSpeak(target = false))
        assertIs<AppDialog.InstallVoice>(vm.state.value.dialog)
        vm.onIntent(AppIntent.DownloadVoices(listOf("fr")))
        assertEquals(AppDialog.Hidden, vm.state.value.dialog)
        assertTrue(vm.state.value.voiceDownload.running)
        assertTrue(PiperVoices.covers(vm.state.value.voiceDownload.lang, "fr"))
        vm.onIntent(AppIntent.ToggleSpeak(target = false))
        assertEquals(AppDialog.Hidden, vm.state.value.dialog)
    }

    @Test
    fun voicesStepFollowsModelWhenTtsAvailable() {
        val vm = vm(tts = FakeTts())
        vm.onIntent(AppIntent.StartInstall)
        vm.onIntent(AppIntent.CompleteDownload)
        vm.onIntent(AppIntent.GoToStep(dev.nucleusframework.offlinetranslator.app.InstallStep.Voices))
        assertEquals(AppKey.Voices, vm.backStack.last())
        assertTrue(vm.state.value.voicePicks.any { PiperVoices.covers(it, "en") })
        assertTrue(vm.state.value.voicePicks.any { PiperVoices.covers(it, "fr") })
    }

    @Test
    fun selectingMissingVoiceDoesNotStartDownload() {
        val vm = vm(tts = FakeTts())
        vm.onIntent(AppIntent.SelectVoice("fr"))
        assertFalse(vm.state.value.voiceDownload.running)
        assertTrue(vm.state.value.data.settings.selectedVoices.isEmpty())
    }

    @Test
    fun downloadVoiceStartsDownload() {
        val vm = vm(tts = FakeTts(), dispatcher = Dispatchers.Unconfined)
        vm.onIntent(AppIntent.DownloadVoices(listOf("fr")))
        assertTrue(vm.state.value.voiceDownload.running)
        assertTrue(PiperVoices.covers(vm.state.value.voiceDownload.lang, "fr"))
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun pauseAndCancelVoiceDownloadFromSettings() {
        val vm = vm(tts = FakeTts(), dispatcher = UnconfinedTestDispatcher())
        vm.onIntent(AppIntent.DownloadVoices(listOf("fr")))
        assertTrue(vm.state.value.voiceDownload.running)
        vm.onIntent(AppIntent.PauseVoiceDownload)
        assertTrue(vm.state.value.voiceDownload.paused)
        assertFalse(vm.state.value.voiceDownload.running)
        vm.onIntent(AppIntent.ResumeVoiceDownload)
        assertTrue(vm.state.value.voiceDownload.running)
        assertFalse(vm.state.value.voiceDownload.paused)
        vm.onIntent(AppIntent.CancelVoiceDownload)
        assertFalse(vm.state.value.voiceDownload.running)
        assertFalse(vm.state.value.voiceDownload.paused)
        assertFalse(vm.state.value.voiceDownload.busy)
        assertEquals(null, vm.state.value.voiceDownload.lang)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun downloadVoicesMarksInstalled() = runTest {
        val downloader = ModelDownloader { dest, _, sha, bytes, onConnect, onVerify, onProgress ->
            onConnect()
            onProgress(bytes, bytes, 0, null)
            onVerify()
            DownloadedModel(dest, sha, bytes)
        }
        val vm = vm(
            tts = FakeTts(),
            downloader = downloader,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        vm.onIntent(AppIntent.DownloadVoices(listOf("fr")))
        testScheduler.advanceUntilIdle()
        assertTrue("fr" in vm.state.value.translation.installedVoices)
        assertFalse(vm.state.value.voiceDownload.running)
    }

    @Test
    fun deleteVoiceRemovesInstall() {
        val removed = mutableListOf<String>()
        val vm = vm(
            tts = FakeTts(),
            voicesOnDisk = { setOf("fr") },
            deleteVoiceFiles = { removed += it },
        )
        assertTrue("fr" in vm.state.value.translation.installedVoices)
        vm.onIntent(AppIntent.DeleteVoice("fr"))
        val dialog = assertIs<AppDialog.Confirm>(vm.state.value.dialog)
        assertEquals(ConfirmAction.DeleteVoice("fr"), dialog.action)
        vm.onIntent(AppIntent.ConfirmDialog)
        assertEquals(AppDialog.Hidden, vm.state.value.dialog)
        assertFalse("fr" in vm.state.value.translation.installedVoices)
        assertEquals(listOf("fr"), removed)
    }

    @Test
    fun resetAppWipesStateAfterConfirm() {
        val store = MemoryStore(seedData())
        val history = MemoryHistoryStore(
            listOf(HistoryItem("1", 1, "fr", "en", "bonjour", "hello", pinned = true)),
        )
        val removedModels = mutableListOf<LlmModel>()
        val removedVoices = mutableListOf<String>()
        var wipedDirs = false
        val vm = vm(
            store = store,
            history = history,
            tts = FakeTts(),
            modelOwnedByApp = { true },
            deleteModelFiles = { removedModels += it.id },
            voicesOnDisk = { setOf("fr") },
            deleteVoiceFiles = { removedVoices += it },
            wipeDownloadDirs = { wipedDirs = true },
        )
        vm.onIntent(AppIntent.CompleteDownload)
        vm.onIntent(AppIntent.OpenApp)
        vm.onIntent(AppIntent.SetUiLanguage(UiLanguage.En))
        vm.onIntent(AppIntent.SetLangNameStyle(LangNameStyle.Native))
        vm.onIntent(AppIntent.ResetApp)
        val dialog = assertIs<AppDialog.Confirm>(vm.state.value.dialog)
        assertEquals(ConfirmAction.ResetApp, dialog.action)
        vm.onIntent(AppIntent.ConfirmDialog)
        val state = vm.state.value
        assertEquals(AppDialog.Hidden, state.dialog)
        assertEquals(AppKey.Welcome, vm.backStack.last())
        assertFalse(state.data.installed)
        assertFalse(state.data.model.installed)
        assertEquals(UiLanguage.Fr, state.data.settings.uiLanguage)
        assertEquals(LangNameStyle.System, state.data.settings.langNames)
        assertTrue(state.data.history.isEmpty())
        assertTrue(history.all().isEmpty())
        assertFalse(store.load().installed)
        assertEquals(setOf(LlmModel.Fast, LlmModel.Precise), removedModels.toSet())
        assertEquals(PiperVoices.all().map { it.id }.toSet(), removedVoices.toSet())
        assertTrue(wipedDirs)
        assertTrue(state.translation.ttsReady)
    }

    @Test
    fun resetAppKeepsModelsNotInstalledByApp() {
        val removedModels = mutableListOf<LlmModel>()
        val vm = vm(
            store = MemoryStore(seedData()),
            modelOwnedByApp = { false },
            deleteModelFiles = { removedModels += it.id },
        )
        vm.onIntent(AppIntent.CompleteDownload)
        vm.onIntent(AppIntent.OpenApp)
        vm.onIntent(AppIntent.ResetApp)
        vm.onIntent(AppIntent.ConfirmDialog)
        assertTrue(removedModels.isEmpty())
        assertFalse(vm.state.value.data.installed)
    }

    @Test
    fun restoreAdoptsModelAlreadyOnDisk() {
        val store = MemoryStore(
            seedData().copy(
                installed = true,
                settings = seedData().settings.copy(selectedModel = LlmModel.Precise),
                model = seedData().model.copy(id = LlmModel.Precise, installed = false, path = ""),
            ),
        )
        val vm = vm(store = store, now = 42L, modelOnDisk = { it.id == LlmModel.Precise })
        val model = vm.state.value.data.model
        assertTrue(model.installed)
        assertEquals(LlmModel.Precise, model.id)
        assertEquals(42L, model.installedAt)
        assertTrue(model.path.endsWith("model.litertlm"))
        assertTrue(vm.state.value.download.done)
        assertTrue(store.load().model.installed)
    }

    @Test
    fun openAppAdoptsModelAlreadyOnDisk() {
        val vm = vm(
            store = MemoryStore(seedData().copy(settings = seedData().settings.copy(selectedModel = LlmModel.Precise))),
            modelOnDisk = { it.id == LlmModel.Precise },
        )
        assertTrue(vm.state.value.data.model.installed)
        vm.onIntent(AppIntent.OpenApp)
        assertTrue(vm.state.value.data.installed)
        assertTrue(vm.state.value.data.model.installed)
        assertEquals(LlmModel.Precise, vm.state.value.data.model.id)
    }

    @Test
    fun selectAlreadyChosenOnDiskModelMarksInstalled() {
        val vm = vm(
            store = MemoryStore(
                seedData().copy(
                    installed = true,
                    settings = seedData().settings.copy(selectedModel = LlmModel.Precise),
                    model = seedData().model.copy(id = LlmModel.Precise, installed = false, path = ""),
                ),
            ),
            modelOnDisk = { it.id == LlmModel.Precise },
        )
        assertTrue(vm.state.value.data.model.installed)
        vm.onIntent(AppIntent.SelectModel(LlmModel.Precise))
        assertTrue(vm.state.value.data.model.installed)
        assertEquals(LlmModel.Precise, vm.state.value.data.model.id)
    }
}

private fun installedModel(keepAlive: LlmKeepAlive = LlmKeepAlive.OnDemand) = seedData().copy(
    installed = true,
    settings = seedData().settings.copy(keepAlive = keepAlive),
    model = ModelInfo(installed = true, path = "."),
)

private class TrackingTranslator : Translator {
    var preloads = 0
    var releases = 0
    var translates = 0

    override suspend fun translate(request: TranslationRequest): TranslationResult {
        translates++
        return TranslationResult.Ok("ok")
    }

    override suspend fun preload(path: String) {
        preloads++
    }

    override suspend fun release() {
        releases++
    }
}

private class FakeTts : TtsSpeaker {
    override val available: Boolean = true
    var lastText: String = ""
    var lastLang: String = ""
    var loadMs: Long = 0
    var holdUntilStop: Boolean = false
    var paused: Boolean = false
    var stops: Int = 0
    private var hold = kotlinx.coroutines.CompletableDeferred<Unit>()
    override fun canSpeak(lang: String): Boolean = Languages.hasTts(lang)
    override suspend fun speak(text: String, lang: String, voiceId: String?, onReady: () -> Unit) {
        hold = kotlinx.coroutines.CompletableDeferred()
        lastText = text
        lastLang = lang
        kotlinx.coroutines.delay(loadMs)
        onReady()
        if (holdUntilStop) hold.await()
    }
    override fun pause() {
        paused = true
    }
    override fun resume() {
        paused = false
    }
    override fun stop() {
        stops++
        paused = false
        hold.complete(Unit)
    }
}

private class FakeMic(private val wav: ByteArray) : MicRecorder {
    override val available: Boolean = true
    override val levels: StateFlow<List<Float>> = MutableStateFlow(listOf(0.4f, 0.7f, 0.3f))
    override suspend fun start() {}
    override suspend fun stop(): ByteArray = wav
}
