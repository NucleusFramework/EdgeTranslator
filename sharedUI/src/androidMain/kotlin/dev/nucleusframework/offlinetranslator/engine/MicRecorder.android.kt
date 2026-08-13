package dev.nucleusframework.offlinetranslator.engine

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.nucleusframework.offlinetranslator.platform.androidActivity
import dev.nucleusframework.offlinetranslator.platform.androidContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

actual fun createMicRecorder(): MicRecorder = AndroidMic()

private class AndroidMic : MicRecorder {
    private val _levels = MutableStateFlow(List(MIC_BARS) { 0.04f })
    override val levels: StateFlow<List<Float>> = _levels

    override val available: Boolean
        get() {
            val min = AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            return min != AudioRecord.ERROR && min != AudioRecord.ERROR_BAD_VALUE
        }

    @StructuredScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private val pcm = ByteArrayOutputStream()

    override suspend fun start() {
        stop()
        if (!ensureRecordAudioPermission()) error("RECORD_AUDIO denied")
        pcm.reset()
        _levels.value = List(MIC_BARS) { 0.04f }
        val min = AudioRecord.getMinBufferSize(
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(MIC_SAMPLE_RATE / 10 * 2)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            error("AudioRecord init failed")
        }
        rec.startRecording()
        recorder = rec
        job = scope.launch {
            val chunk = ByteArray(MIC_SAMPLE_RATE / 20 * 2)
            val maxBytes = MIC_SAMPLE_RATE * 2 * (MIC_MAX_MS / 1000)
            while (isActive && pcm.size() < maxBytes) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) break
                synchronized(pcm) { pcm.write(chunk, 0, n) }
                _levels.value = _levels.value.drop(1) + pcm16leRms(chunk, n).coerceAtLeast(0.03f)
            }
        }
    }

    override suspend fun stop(): ByteArray = withContext(Dispatchers.IO) {
        val current = recorder
        recorder = null
        job?.cancel()
        job = null
        try {
            if (current?.recordingState == AudioRecord.RECORDSTATE_RECORDING) current.stop()
            current?.release()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        val raw = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        _levels.value = List(MIC_BARS) { 0.04f }
        if (raw.size < MIC_SAMPLE_RATE / 5) ByteArray(0) else pcm16leToWav(raw)
    }
}

private suspend fun ensureRecordAudioPermission(): Boolean {
    val ctx = androidContext()
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return true
    }
    val activity = androidActivity() ?: return false
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            lateinit var launcher: ActivityResultLauncher<String>
            launcher = activity.activityResultRegistry.register(
                "record_audio_${System.nanoTime()}",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                launcher.unregister()
                if (cont.isActive) cont.resume(granted)
            }
            cont.invokeOnCancellation { runCatching { launcher.unregister() } }
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
