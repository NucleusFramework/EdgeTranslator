package dev.nucleusframework.offlinetranslator.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun paragraphCount(text: String): Int {
    if (text.isBlank()) return 0
    return text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }.coerceAtLeast(1)
}

fun replaceTerm(text: String, from: String, to: String): String {
    if (from.isEmpty() || from == to) return text
    return text.replaceFirst(from, to)
}

fun formatNumber(value: Double, ui: UiLanguage): String {
    val v = ((value * 10).toInt() / 10.0)
    val s = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    return if (ui.commaDecimal) s.replace('.', ',') else s
}

fun formatLatency(ms: Long?, ui: UiLanguage): String {
    if (ms == null) return ""
    return formatNumber(ms / 1000.0, ui)
}

fun formatBytes(bytes: Long, ui: UiLanguage, gb: String = "GB", mb: String = "MB", kb: String = "kB", b: String = "B"): String {
    fun dec(n: Double) = formatNumber(n, ui)
    return when {
        bytes >= 1_000_000_000 -> "${dec(bytes / 1_000_000_000.0)} $gb"
        bytes >= 1_000_000 -> "${dec(bytes / 1_000_000.0)} $mb"
        bytes >= 1_000 -> "${dec(bytes / 1_000.0)} $kb"
        else -> "$bytes $b"
    }
}

fun formatPercent(fraction: Float, ui: UiLanguage): String {
    val p = (fraction * 100).toInt().coerceIn(0, 100)
    return if (ui.commaDecimal) "$p %" else "$p%"
}

fun formatEta(remainingBytes: Long, speedBps: Long): String {
    if (speedBps <= 0L) return "—"
    val sec = (remainingBytes / speedBps).coerceAtLeast(0)
    val m = sec / 60
    val s = sec % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

fun formatHistoryStamp(epochMs: Long, months: List<String>, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    val month = months.getOrElse(local.month.ordinal) { "?" }
    val hh = local.hour.toString().padStart(2, '0')
    val mm = local.minute.toString().padStart(2, '0')
    return "${local.day} $month $hh:$mm"
}

fun newId(now: Long, salt: Int = kotlin.random.Random.nextInt(100_000)): String = "$now-$salt"

fun filterHistory(items: List<HistoryItem>, query: String, filter: HistoryFilter, now: Long): List<HistoryItem> {
    val q = query.trim().lowercase()
    return items.asSequence()
        .filter { item ->
            when (filter) {
                HistoryFilter.All -> true
                HistoryFilter.Pinned -> item.pinned
                HistoryFilter.Last7Days -> now - item.createdAt <= 7L * 24 * 60 * 60 * 1000
            }
        }
        .filter { item ->
            q.isEmpty() ||
                item.sourceText.lowercase().contains(q) ||
                item.targetText.lowercase().contains(q) ||
                item.sourceLang.contains(q) ||
                item.targetLang.contains(q)
        }
        .sortedWith(compareByDescending<HistoryItem> { it.pinned }.thenByDescending { it.createdAt })
        .toList()
}

fun purgeOldHistory(items: List<HistoryItem>, now: Long, days: Int): List<HistoryItem> {
    val cut = now - days.toLong() * 24 * 60 * 60 * 1000
    return items.filter { it.pinned || it.createdAt >= cut }
}
