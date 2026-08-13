package dev.nucleusframework.offlinetranslator.platform

internal actual val isDebugBuild: Boolean
    get() = System.getProperty("compose.application.resources.dir") == null
