package dev.nucleusframework.offlinetranslator.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.domain.AppData
import dev.nucleusframework.offlinetranslator.domain.HistoryFilter
import dev.nucleusframework.offlinetranslator.domain.HistoryItem
import dev.nucleusframework.offlinetranslator.domain.filterHistory
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.ui.Chip
import dev.nucleusframework.offlinetranslator.ui.OutlinedPill
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.VerticalContentScrollbar
import dev.nucleusframework.offlinetranslator.ui.formatHistoryStampUi
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.cd_delete
import offlinetranslator.sharedui.generated.resources.cd_pin
import offlinetranslator.sharedui.generated.resources.cd_unpin
import offlinetranslator.sharedui.generated.resources.history_clear
import offlinetranslator.sharedui.generated.resources.history_col_date
import offlinetranslator.sharedui.generated.resources.history_col_pair
import offlinetranslator.sharedui.generated.resources.history_col_source
import offlinetranslator.sharedui.generated.resources.history_col_target
import offlinetranslator.sharedui.generated.resources.history_empty
import offlinetranslator.sharedui.generated.resources.history_filter_all
import offlinetranslator.sharedui.generated.resources.history_filter_last_7
import offlinetranslator.sharedui.generated.resources.history_filter_pinned
import offlinetranslator.sharedui.generated.resources.history_search
import offlinetranslator.sharedui.generated.resources.history_stored
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@TraceRecomposition(tag = "history", threshold = 3)
@Composable
fun HistoryScreen(data: AppData, query: String, filter: HistoryFilter, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val rows = filterHistory(data.history, query, filter, Platform.now())
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HistoryFilters(filter, onIntent)
            Spacer(Modifier.weight(1f))
            if (data.history.isNotEmpty()) {
                OutlinedPill(stringResource(Res.string.history_clear), onClick = { onIntent(AppIntent.ClearHistory) })
            }
            Row(
                Modifier.width(420.dp).height(40.dp).clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceContainer).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
                val placeholder = pluralStringResource(Res.plurals.history_search, data.history.size, data.history.size)
                BasicTextField(
                    value = query,
                    onValueChange = { onIntent(AppIntent.SetHistoryQuery(it)) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = c.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(c.surfaceContainer).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(Res.string.history_col_date), Modifier.width(120.dp))
                SectionLabel(stringResource(Res.string.history_col_pair), Modifier.width(120.dp))
                SectionLabel(stringResource(Res.string.history_col_source), Modifier.weight(1f))
                SectionLabel(stringResource(Res.string.history_col_target), Modifier.weight(1f))
                Spacer(Modifier.width(80.dp))
            }
            if (rows.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text(stringResource(Res.string.history_empty), color = c.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        rows.forEach { e ->
                            HistoryRow(e, onIntent)
                            HorizontalDivider(color = c.surfaceContainerHighest)
                        }
                    }
                    VerticalContentScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            pluralStringResource(Res.plurals.history_stored, data.history.size, data.history.size),
            color = c.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HistoryFilters(filter: HistoryFilter, onIntent: (AppIntent) -> Unit) {
    val filters = listOf(
        HistoryFilter.All to stringResource(Res.string.history_filter_all),
        HistoryFilter.Pinned to stringResource(Res.string.history_filter_pinned),
        HistoryFilter.Last7Days to stringResource(Res.string.history_filter_last_7),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        filters.forEach { (f, label) ->
            Chip(label, f == filter, onClick = { onIntent(AppIntent.SetHistoryFilter(f)) })
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryItem, onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (e.pinned) c.primaryContainer.copy(alpha = 0.45f) else c.surface)
            .clickable { onIntent(AppIntent.OpenHistory(e.id)) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatHistoryStampUi(e.createdAt), Modifier.width(120.dp), color = c.onSurfaceVariant, fontSize = 14.sp)
        Text("${e.sourceLang.uppercase()} → ${e.targetLang.uppercase()}", Modifier.width(120.dp), color = c.onSurface, fontSize = 14.sp)
        Text(
            e.sourceText,
            Modifier.weight(1f).padding(end = 16.dp),
            color = c.onSurface,
            fontSize = 14.sp,
            fontWeight = if (e.pinned) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            e.targetText,
            Modifier.weight(1f).padding(end = 16.dp),
            color = c.onSurfaceVariant,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.width(40.dp).clickable { onIntent(AppIntent.ToggleHistoryPin(e.id)) }, Alignment.Center) {
            Icon(
                if (e.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                if (e.pinned) stringResource(Res.string.cd_unpin) else stringResource(Res.string.cd_pin),
                Modifier.size(20.dp).then(if (e.pinned) Modifier else Modifier.rotate(45f)),
                tint = if (e.pinned) c.primary else c.onSurfaceVariant,
            )
        }
        Box(Modifier.width(40.dp).clickable { onIntent(AppIntent.DeleteHistory(e.id)) }, Alignment.Center) {
            Icon(Icons.Outlined.Delete, stringResource(Res.string.cd_delete), Modifier.size(20.dp), tint = c.onSurfaceVariant)
        }
    }
}
