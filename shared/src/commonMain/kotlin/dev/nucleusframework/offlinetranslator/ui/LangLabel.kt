package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.runtime.Composable
import dev.nucleusframework.offlinetranslator.domain.AUTO_LANG
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.formatBytes
import dev.nucleusframework.offlinetranslator.domain.formatHistoryStamp
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.lang_ar
import offlinetranslator.shared.generated.resources.lang_auto
import offlinetranslator.shared.generated.resources.lang_bn
import offlinetranslator.shared.generated.resources.lang_cs
import offlinetranslator.shared.generated.resources.lang_da
import offlinetranslator.shared.generated.resources.lang_de
import offlinetranslator.shared.generated.resources.lang_el
import offlinetranslator.shared.generated.resources.lang_en
import offlinetranslator.shared.generated.resources.lang_es
import offlinetranslator.shared.generated.resources.lang_fa
import offlinetranslator.shared.generated.resources.lang_fi
import offlinetranslator.shared.generated.resources.lang_fil
import offlinetranslator.shared.generated.resources.lang_fr
import offlinetranslator.shared.generated.resources.lang_he
import offlinetranslator.shared.generated.resources.lang_hi
import offlinetranslator.shared.generated.resources.lang_hr
import offlinetranslator.shared.generated.resources.lang_hu
import offlinetranslator.shared.generated.resources.lang_id
import offlinetranslator.shared.generated.resources.lang_it
import offlinetranslator.shared.generated.resources.lang_ja
import offlinetranslator.shared.generated.resources.lang_ko
import offlinetranslator.shared.generated.resources.lang_mi
import offlinetranslator.shared.generated.resources.lang_nl
import offlinetranslator.shared.generated.resources.lang_no
import offlinetranslator.shared.generated.resources.lang_pl
import offlinetranslator.shared.generated.resources.lang_pt
import offlinetranslator.shared.generated.resources.lang_ro
import offlinetranslator.shared.generated.resources.lang_ru
import offlinetranslator.shared.generated.resources.lang_sv
import offlinetranslator.shared.generated.resources.lang_sw
import offlinetranslator.shared.generated.resources.lang_te
import offlinetranslator.shared.generated.resources.lang_th
import offlinetranslator.shared.generated.resources.lang_tr
import offlinetranslator.shared.generated.resources.lang_uk
import offlinetranslator.shared.generated.resources.lang_vi
import offlinetranslator.shared.generated.resources.lang_zh
import offlinetranslator.shared.generated.resources.month_apr
import offlinetranslator.shared.generated.resources.month_aug
import offlinetranslator.shared.generated.resources.month_dec
import offlinetranslator.shared.generated.resources.month_feb
import offlinetranslator.shared.generated.resources.month_jan
import offlinetranslator.shared.generated.resources.month_jul
import offlinetranslator.shared.generated.resources.month_jun
import offlinetranslator.shared.generated.resources.month_mar
import offlinetranslator.shared.generated.resources.month_may
import offlinetranslator.shared.generated.resources.month_nov
import offlinetranslator.shared.generated.resources.month_oct
import offlinetranslator.shared.generated.resources.month_sep
import offlinetranslator.shared.generated.resources.unit_b
import offlinetranslator.shared.generated.resources.unit_gb
import offlinetranslator.shared.generated.resources.unit_kb
import offlinetranslator.shared.generated.resources.unit_mb
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun languageNameRes(code: String): StringResource = when (code) {
    AUTO_LANG -> Res.string.lang_auto
    "fr" -> Res.string.lang_fr
    "en" -> Res.string.lang_en
    "ar" -> Res.string.lang_ar
    "bn" -> Res.string.lang_bn
    "zh" -> Res.string.lang_zh
    "hr" -> Res.string.lang_hr
    "cs" -> Res.string.lang_cs
    "da" -> Res.string.lang_da
    "nl" -> Res.string.lang_nl
    "fil" -> Res.string.lang_fil
    "fi" -> Res.string.lang_fi
    "de" -> Res.string.lang_de
    "el" -> Res.string.lang_el
    "he" -> Res.string.lang_he
    "hi" -> Res.string.lang_hi
    "hu" -> Res.string.lang_hu
    "id" -> Res.string.lang_id
    "it" -> Res.string.lang_it
    "ja" -> Res.string.lang_ja
    "ko" -> Res.string.lang_ko
    "mi" -> Res.string.lang_mi
    "no" -> Res.string.lang_no
    "fa" -> Res.string.lang_fa
    "pl" -> Res.string.lang_pl
    "pt" -> Res.string.lang_pt
    "ro" -> Res.string.lang_ro
    "ru" -> Res.string.lang_ru
    "es" -> Res.string.lang_es
    "sw" -> Res.string.lang_sw
    "sv" -> Res.string.lang_sv
    "te" -> Res.string.lang_te
    "th" -> Res.string.lang_th
    "tr" -> Res.string.lang_tr
    "uk" -> Res.string.lang_uk
    "vi" -> Res.string.lang_vi
    else -> Res.string.lang_en
}

@Composable
fun languageLabel(code: String, style: LangNameStyle = LangNameStyle.System): String {
    if (style == LangNameStyle.Native && !Languages.isAuto(code)) {
        return Languages.get(code)?.native ?: code.uppercase()
    }
    return stringResource(languageNameRes(code))
}

@Composable
fun formatBytesUi(bytes: Long, ui: UiLanguage): String = formatBytes(
    bytes,
    ui,
    stringResource(Res.string.unit_gb),
    stringResource(Res.string.unit_mb),
    stringResource(Res.string.unit_kb),
    stringResource(Res.string.unit_b),
)

@Composable
fun formatHistoryStampUi(epochMs: Long): String = formatHistoryStamp(
    epochMs,
    listOf(
        stringResource(Res.string.month_jan),
        stringResource(Res.string.month_feb),
        stringResource(Res.string.month_mar),
        stringResource(Res.string.month_apr),
        stringResource(Res.string.month_may),
        stringResource(Res.string.month_jun),
        stringResource(Res.string.month_jul),
        stringResource(Res.string.month_aug),
        stringResource(Res.string.month_sep),
        stringResource(Res.string.month_oct),
        stringResource(Res.string.month_nov),
        stringResource(Res.string.month_dec),
    ),
)
