package dev.nucleusframework.offlinetranslator.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.engine.LlmAccelerator
import dev.nucleusframework.offlinetranslator.engine.LlmRuntime
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.em_dash
import offlinetranslator.sharedui.generated.resources.engine_auto
import offlinetranslator.sharedui.generated.resources.engine_cpu
import offlinetranslator.sharedui.generated.resources.engine_gpu
import org.jetbrains.compose.resources.stringResource

/** The backend chosen in Settings: Auto, GPU, or CPU. */
@Composable
fun backendLabel(backend: LlmBackend): String = when (backend) {
    LlmBackend.Auto -> stringResource(Res.string.engine_auto)
    LlmBackend.Gpu -> stringResource(Res.string.engine_gpu)
    LlmBackend.Cpu -> stringResource(Res.string.engine_cpu)
}

/** The backend the LLM is actually running on: GPU, CPU, or not loaded yet. */
@Composable
fun acceleratorLabel(): String {
    val accelerator by LlmRuntime.accelerator.collectAsState()
    return when (accelerator) {
        LlmAccelerator.Gpu -> stringResource(Res.string.engine_gpu)
        LlmAccelerator.Cpu -> stringResource(Res.string.engine_cpu)
        LlmAccelerator.None -> stringResource(Res.string.em_dash)
    }
}

@Composable
fun LlmAcceleratorBadge(modifier: Modifier = Modifier) {
    Text(
        acceleratorLabel(),
        modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )
}
