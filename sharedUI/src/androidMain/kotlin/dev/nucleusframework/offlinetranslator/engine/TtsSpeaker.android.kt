package dev.nucleusframework.offlinetranslator.engine

import io.ktor.client.HttpClient

// ponytail: piper-jni is desktop-only (no Android natives). Wire an AAR later.
actual fun createTtsSpeaker(http: HttpClient): TtsSpeaker = SilentTts
