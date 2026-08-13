package dev.nucleusframework.offlinetranslator.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.systeminfo.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class MeterSnapshot(
    val cpu: Float = 0f,
    val ramUsed: Long = 0L,
    val ramTotal: Long = 0L,
    val ramApp: Long = 0L,
    val gpu: Float? = null,
    val hasGpu: Boolean = false,
)

@Composable
fun SystemMeters(modifier: Modifier = Modifier) {
    var snap by remember { mutableStateOf(MeterSnapshot()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { sample() } // prime CPU tick counters
        while (true) {
            delay(1_000)
            snap = withContext(Dispatchers.IO) { sample() }
        }
    }
    // Bar: how much of the machine's RAM is taken, so the empty part is what is
    // still available. Line below, no bar: what this app itself holds.
    val ramFrac = if (snap.ramTotal > 0) snap.ramUsed.toFloat() / snap.ramTotal else 0f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Meter("CPU", pct(snap.cpu), (snap.cpu / 100f).coerceIn(0f, 1f))
        Meter("RAM", ramLabel(snap.ramUsed, snap.ramTotal), ramFrac)
        if (snap.hasGpu) Meter("GPU", snap.gpu?.let { pct(it) } ?: "—", ((snap.gpu ?: 0f) / 100f).coerceIn(0f, 1f))
        ValueRow("App", appLabel(snap.ramApp))
    }
}

@Composable
private fun Meter(label: String, value: String, fraction: Float) {
    val c = MaterialTheme.colorScheme
    val bar = loadColor(fraction, c.primary, c.tertiary, c.error)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ValueRow(label, value)
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.outlineVariant)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).clip(RoundedCornerShape(1.dp)).background(bar))
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value, color = c.onSurface, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

private fun sample(): MeterSnapshot {
    val mem = SystemInfo.memoryInfo()
    val gpus = SystemInfo.gpus()
    return MeterSnapshot(
        cpu = SystemInfo.cpuInfo()?.globalCpuUsage ?: 0f,
        ramUsed = mem?.usedMemory ?: 0L,
        ramTotal = mem?.totalMemory ?: 0L,
        ramApp = SystemInfo.process(ProcessHandle.current().pid())?.memory ?: 0L,
        gpu = gpus.mapNotNull { it.gpuUsage }.maxOrNull(),
        hasGpu = gpus.isNotEmpty(),
    )
}

private fun pct(value: Float) = "${value.toInt().coerceIn(0, 100)} %"

/** Machine RAM taken over installed, so the remainder is what is available. */
private fun ramLabel(used: Long, total: Long): String = if (total <= 0L) "—" else "${gib(used)} / ${gib(total)} GB"

/** This app's resident memory, in MB with the unit spelled out. */
private fun appLabel(bytes: Long): String = if (bytes <= 0L) "—" else "${bytes / 1_048_576} MB"

private fun gib(bytes: Long): String {
    val g = bytes / 1_073_741_824.0
    val tenths = ((g * 10).toInt() / 10.0)
    return if (tenths == tenths.toLong().toDouble()) tenths.toLong().toString() else tenths.toString()
}

private fun loadColor(fraction: Float, low: Color, mid: Color, high: Color): Color = when {
    fraction < 0.7f -> low
    fraction < 0.9f -> mid
    else -> high
}
