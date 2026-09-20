package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.window.tao.XdgPortalParent
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import dev.nucleusframework.core.runtime.Platform as NucleusPlatform

/**
 * Tao is not an AWT window. On Linux the XDG portal file chooser stays blank
 * or never maps unless it is parented to the live X11 XID / Wayland export.
 */
internal object FilePickerParent {
    @Volatile
    var window: NucleusWindow? = null
}

@Composable
fun InstallDesktopFilePicker(window: NucleusWindow) {
    DisposableEffect(window) {
        FilePickerParent.window = window
        onDispose {
            if (FilePickerParent.window === window) FilePickerParent.window = null
        }
    }
}

internal actual suspend fun filekitPickImage(): ByteArray? {
    val owned = FilePickerParent.window?.fileKitDialog()
        ?: return FileKit.openFilePicker(type = FileKitType.Image)?.readBytes()
    return owned.use { dialog ->
        FileKit.openFilePicker(
            type = FileKitType.Image,
            dialogSettings = dialog.settings,
        )?.readBytes()
    }
}

private class OwnedFileKitDialog(val settings: FileKitDialogSettings, private val closer: AutoCloseable?) : AutoCloseable {
    override fun close() {
        closer?.close()
    }
}

private fun NucleusWindow.fileKitDialog(): OwnedFileKitDialog {
    // Nucleus 2.6 retired the AWT backend: unsafe only exposes the Tao window.
    val tao = unsafe.taoWindow ?: return OwnedFileKitDialog(FileKitDialogSettings(), closer = null)
    when (val parent = tao.xdgPortalParent()) {
        is XdgPortalParent.X11 -> return OwnedFileKitDialog(
            FileKitDialogSettings(parent = FileKitDialogParent.x11(parent.xid)),
            closer = null,
        )

        is XdgPortalParent.Wayland -> return OwnedFileKitDialog(
            FileKitDialogSettings(parent = FileKitDialogParent.wayland(parent.handle)),
            closer = parent,
        )

        null -> Unit
    }
    // Windows: TaoWindow.nativeHandle is the HWND. macOS has no FileKit parent yet
    // (nsWindowHandle is an NSWindow*, FileKit has no macos() factory).
    tao.nativeHandle.takeIf { NucleusPlatform.Current == NucleusPlatform.Windows && it != 0L }?.let { hwnd ->
        return OwnedFileKitDialog(
            FileKitDialogSettings(parent = FileKitDialogParent.windows(hwnd)),
            closer = null,
        )
    }
    return OwnedFileKitDialog(FileKitDialogSettings(), closer = null)
}
