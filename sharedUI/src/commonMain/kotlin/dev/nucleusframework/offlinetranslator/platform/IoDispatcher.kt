package dev.nucleusframework.offlinetranslator.platform

import kotlinx.coroutines.CoroutineDispatcher

/** Blocking I/O dispatcher. JVM/Android: `Dispatchers.IO`. Do not use `Dispatchers.IO` in commonMain (KMP_001). */
internal expect val IoDispatcher: CoroutineDispatcher
