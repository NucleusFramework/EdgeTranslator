package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.platform.filekitPickImage

/** Encoded image (JPEG/PNG) chosen by the user, `null` when the picker is cancelled. */
fun interface ImagePicker {
    suspend fun pick(): ByteArray?
}

object FileImagePicker : ImagePicker {
    override suspend fun pick(): ByteArray? = filekitPickImage()
}
