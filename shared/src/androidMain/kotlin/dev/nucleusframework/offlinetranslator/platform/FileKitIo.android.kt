package dev.nucleusframework.offlinetranslator.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes

internal actual suspend fun filekitPickImage(): ByteArray? =
    FileKit.openFilePicker(FileKitType.Image)?.readBytes()
