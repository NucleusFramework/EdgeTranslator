package dev.nucleusframework.offlinetranslator.platform

import android.content.pm.ApplicationInfo

internal actual val isDebugBuild: Boolean
    get() = try {
        (androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    } catch (_: IllegalStateException) {
        false
    }
