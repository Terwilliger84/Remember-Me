package de.kontaktchronik.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.kontaktchronik.app.R
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.model.BirthdayCongratulationTracker
import de.kontaktchronik.app.model.ContactResolver
import de.kontaktchronik.app.ui.AppThemeRegistry
import de.kontaktchronik.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WidgetKind {
    MINI,
    STRIP,
    COMPACT_LIST,
    LARGE_LIST
}

data class WidgetRowItem(
    val key: String,
    val personId: Long,
    val icon: String,
    val name: String,
    val shortMeta: String,
    val compactDateOnly: String,
    val popupReason: String,
    val badge: String,
    val isTodayOrDue: Boolean,
    val isBirthdayToday: Boolean = false,
    val isCongratulatedToday: Boolean = false
)

data class WidgetPalette(
    val bg: Int,
    val border: Int,
    val rowBg: Int,
    val accent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val badgeBg: Int,
    val badgeText: Int,
    val todayBadgeBg: Int,
    val todayBadgeText: Int,
    val bdayTodayFrame: Int,
    val bdayTodayRowBg: Int,
    val bdayTodayText: Int,
    val bdayTodaySubText: Int,
    val bdayTodayBadgeBg: Int,
    val bdayTodayBadgeText: Int,
    val bdayDoneFrame: Int,
    val bdayDoneRowBg: Int,
    val bdayDoneText: Int,
    val bdayDoneBadgeBg: Int,
    val bdayDoneBadgeText: Int
)

object RememberMeWidgetHelper {
    @Volatile
    var cachedRows: List<WidgetRowItem>? = null

    fun currentSavedTheme(context: Context): String {
        val raw = context.getSharedPreferences("remember_me_prefs", Context.MODE_PRIVATE)
            .getString("theme_mode", "warm")
        return AppThemeRegistry.byId(raw).id
    }

    fun paletteForTheme(themeMode: String?): WidgetPalette {
        val spec = AppThemeRegistry.byId(themeMode)
        return if (spec.isLight) {
            WidgetPalette(
                bg = spec.bgSlate.toArgb(),
                border = spec.borderSlate.toArgb(),
                rowBg = spec.cardSlate.toArgb(),
                accent = spec.accent.toArgb(),
                textPrimary = spec.textPrimary.toArgb(),
                textSecondary = spec.textSecondary.toArgb(),
                badgeBg = spec.btnBg.toArgb(),
                badgeText = spec.primaryBtnStart.toArgb(),
                todayBadgeBg = spec.primaryBtnStart.toArgb(),
                todayBadgeText = Color.White.toArgb(),
                bdayTodayFrame = Color(0xFFF59E0B).toArgb(),
                bdayTodayRowBg = Color(0xFFFFFBEB).toArgb(),
                bdayTodayText = Color(0xFF92400E).toArgb(),
                bdayTodaySubText = Color(0xFFB45309).toArgb(),
                bdayTodayBadgeBg = Color(0xFFD97706).toArgb(),
                bdayTodayBadgeText = Color.White.toArgb(),
                bdayDoneFrame = Color(0xFF10B981).toArgb(),
                bdayDoneRowBg = Color(0xFFECFDF5).toArgb(),
                bdayDoneText = Color(0xFF065F46).toArgb(),
                bdayDoneBadgeBg = Color(0xFF15803D).toArgb(),
                bdayDoneBadgeText = Color.White.toArgb()
            )
        } else {
            WidgetPalette(
                bg = spec.bgSlate.toArgb(),
                border = spec.borderSlate.toArgb(),
                rowBg = spec.cardSlate.toArgb(),
                accent = spec.accent.toArgb(),
                textPrimary = spec.textPrimary.toArgb(),
                textSecondary = spec.textSecondary.toArgb(),
                badgeBg = spec.btnBg.toArgb(),
                badgeText = spec.accent.toArgb(),
                todayBadgeBg = spec.primaryBtnStart.toArgb(),
                todayBadgeText = Color.White.toArgb(),
                bdayTodayFrame = Color(0xFFFBBF24).toArgb(),
                bdayTodayRowBg = Color(0xFF3B2810).toArgb(),
                bdayTodayText = Color(0xFFFEF3C7).toArgb(),
                bdayTodaySubText = Color(0xFFFCD34D).toArgb(),
                bdayTodayBadgeBg = Color(0xFFD97706).toArgb(),
                bdayTodayBadgeText = Color.White.toArgb(),
                bdayDoneFrame = Color(0xFF34D399).toArgb(),
                bdayDoneRowBg = Color(0xFF12352A).toArgb(),
                bdayDoneText = Color(0xFFD1FAE5).toArgb(),
                bdayDoneBadgeBg = Color(0xFF059669).toArgb(),
                bdayDoneBadgeText = Color.White.toArgb()
            )
        }
    }

    private fun shortDateDdMm(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        val clean = isoDate.trim()
        val parts = clean.split("-")
        if (parts.size >= 3) {
            val m = parts[1].padStart(2, '0')
            val d = parts[2].take(2).padStart(2, '0')
            return "$d.$m."
        }
        return ""
    }

    private fun shortAgeNumber(birthdayIso: String?, today: LocalDate): String {
        val raw = ContactResolver.formatUpcomingAge(birthdayIso, today)
        if (raw.isBlank() || raw.equals("Alter unbekannt", ignoreCase = true) || raw.equals("Geburtstag", ignoreCase = true)) return ""
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotBlank()) "${digits}." else ""
    }

    private fun addWidgetInterval(base: LocalDate, count: Long, unit: String): LocalDate {
        val step = count.coerceAtLeast(1L)
        return when (unit.uppercase()) {
            "WEEKS" -> base.plusWeeks(step)
            "MONTHS" -> base.plusMonths(step)
            "YEARS" -> base.plusYears(step)
            else -> base.plusDays(step)
        }
    }

    private fun resolveSpecialEventOccurrences(
        rawSpec: String,
        today: LocalDate,
        horizonDays: Long = 90L,
        maxOccurrences: Int = 5
    ): List<LocalDate> {
        val spec = rawSpec.trim()
        return when {
            spec.startsWith("REPEAT:") -> {
                val parts = spec.split(":")
                if (parts.size >= 4) {
                    val count = parts[1].toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                    val unit = parts[2].uppercase()
                    var nextD = runCatching { LocalDate.parse(parts[3]) }.getOrNull() ?: return emptyList()
                    var guard = 0
                    while (nextD.isBefore(today) && guard < 1000) {
                        nextD = addWidgetInterval(nextD, count, unit)
                        guard++
                    }
                    val list = mutableListOf<LocalDate>()
                    var cur = nextD
                    for (idx in 0 until maxOccurrences) {
                        val diff = ChronoUnit.DAYS.between(today, cur)
                        if (idx > 0 && diff > horizonDays) break
                        list += cur
                        cur = addWidgetInterval(cur, count, unit)
                    }
                    list
                } else emptyList()
            }
            spec.startsWith("WEEKLY:") -> {
                val targetDow = spec.removePrefix("WEEKLY:").toIntOrNull() ?: return emptyList()
                if (targetDow !in 1..7) return emptyList()
                val currentDow = today.dayOfWeek.value
                val firstDelta = ((targetDow - currentDow) + 7) % 7
                val firstDate = today.plusDays(firstDelta.toLong())
                val list = mutableListOf<LocalDate>()
                for (idx in 0 until maxOccurrences) {
                    val cur = firstDate.plusWeeks(idx.toLong())
                    if (idx > 0 && ChronoUnit.DAYS.between(today, cur) > horizonDays) break
                    list += cur
                }
                list
            }
            else -> {
                val single = runCatching { LocalDate.parse(spec) }.getOrNull()
                if (single != null && !single.isBefore(today)) listOf(single) else emptyList()
            }
        }
    }

    suspend fun loadAllRows(context: Context, maxItems: Int = 15): List<WidgetRowItem> {
        val prefs = context.getSharedPreferences("remember_me_prefs", Context.MODE_PRIVATE)
        val hiddenIds = prefs.getStringSet("hidden_person_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        val dao = AppDatabase.getInstance(context).dao()
        val persons = dao.getAllPersonsOnce().filter { !it.isHidden && !hiddenIds.contains(it.id) }
        val events = dao.getAllSpecialEventsOnce()
        val today = LocalDate.now()
        val congratsRecords = BirthdayCongratulationTracker.loadAllRecords(context)

        val candidates = mutableListOf<Triple<Long, Int, WidgetRowItem>>()

        for (p in persons) {
            val bdayDays = ContactResolver.daysUntilNextBirthday(p.birthdayIso, today)
            if (bdayDays != null && bdayDays <= 366L) {
                val isBdayToday = bdayDays == 0L
                val isCongratulated = isBdayToday && congratsRecords[p.id]?.year == today.year
                val ddMm = shortDateDdMm(p.birthdayIso)
                val ageDot = shortAgeNumber(p.birthdayIso, today)
                val meta = when {
                    ddMm.isNotBlank() && ageDot.isNotBlank() -> "$ddMm ($ageDot)"
                    ddMm.isNotBlank() -> ddMm
                    else -> "Geburtstag"
                }
                val ageText = ContactResolver.formatUpcomingAge(p.birthdayIso, today)
                val reason = if (ageText.equals("Alter unbekannt", ignoreCase = true) || ageText.equals("Geburtstag", ignoreCase = true)) {
                    "🎂 Geburtstag"
                } else {
                    "🎂 Geburtstag ($ageText)"
                }
                val icon = when {
                    isBdayToday && isCongratulated -> "🎂"
                    isBdayToday -> "🎉🎂"
                    else -> "🎂"
                }
                val badge = when {
                    isBdayToday && isCongratulated -> "✅ HEUTE"
                    isBdayToday -> "🎂 HEUTE!"
                    bdayDays == 1L -> "Morgen"
                    else -> "${bdayDays}T"
                }
                candidates += Triple(
                    bdayDays,
                    0,
                    WidgetRowItem(
                        key = "bday_${p.id}",
                        personId = p.id,
                        icon = icon,
                        name = p.name,
                        shortMeta = meta,
                        compactDateOnly = ddMm,
                        popupReason = reason,
                        badge = badge,
                        isTodayOrDue = isBdayToday,
                        isBirthdayToday = isBdayToday,
                        isCongratulatedToday = isCongratulated
                    )
                )
            }

            val firstTimerDate = ContactResolver.nextTimerDate(p, today)
            if (firstTimerDate != null) {
                var curTimer: LocalDate = firstTimerDate
                val stepCount = p.timerInterval.coerceAtLeast(1).toLong()
                for (occIdx in 0 until 4) {
                    val diff = ChronoUnit.DAYS.between(today, curTimer)
                    if (occIdx > 0 && diff > 60L) break
                    if (diff <= 60L) {
                        val sortDays = diff.coerceAtLeast(0L)
                        val ddMm = "%02d.%02d.".format(curTimer.dayOfMonth, curTimer.monthValue)
                        val badge = when {
                            diff <= 0L -> "Fällig"
                            diff == 1L -> "Morgen"
                            else -> "${diff}T"
                        }
                        candidates += Triple(
                            sortDays,
                            2,
                            WidgetRowItem(
                                key = "timer_${p.id}_$occIdx",
                                personId = p.id,
                                icon = "⏳",
                                name = p.name,
                                shortMeta = "Timer ($ddMm)",
                                compactDateOnly = ddMm,
                                popupReason = "⏳ Melde-Timer",
                                badge = badge,
                                isTodayOrDue = diff <= 0L
                            )
                        )
                    }
                    val baseForNext = if (curTimer.isBefore(today)) today else curTimer
                    curTimer = addWidgetInterval(baseForNext, stepCount, p.timerUnit)
                }
            }
        }

        for (ev in events) {
            val person = persons.find { it.id == ev.personId } ?: continue
            val occurrences = resolveSpecialEventOccurrences(ev.dateIso, today, horizonDays = 90L, maxOccurrences = 5)
            occurrences.forEachIndexed { occIdx, evDate ->
                val diff = ChronoUnit.DAYS.between(today, evDate)
                if (diff in 0L..90L) {
                    val ddMm = "%02d.%02d.".format(evDate.dayOfMonth, evDate.monthValue)
                    val isRecurring = ev.dateIso.startsWith("WEEKLY:") || ev.dateIso.startsWith("REPEAT:")
                    val badge = when (diff) {
                        0L -> "Heute"
                        1L -> "Morgen"
                        else -> "${diff}T"
                    }
                    candidates += Triple(
                        diff,
                        1,
                        WidgetRowItem(
                            key = "ev_${ev.id}_$occIdx",
                            personId = person.id,
                            icon = if (isRecurring) "🔁" else "🔔",
                            name = person.name,
                            shortMeta = "${ev.title} ($ddMm)",
                            compactDateOnly = ddMm,
                            popupReason = "🔔 ${ev.title}",
                            badge = badge,
                            isTodayOrDue = diff == 0L
                        )
                    )
                }
            }
        }

        val sorted = candidates
            .sortedWith(
                compareBy<Triple<Long, Int, WidgetRowItem>> { it.first }
                    .thenBy { it.second }
                    .thenBy { it.third.name.lowercase() }
            )
            .take(maxItems)
            .map { it.third }

        cachedRows = sorted
        return sorted
    }

    fun buildOpenAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("rememberme://widget/open_home/$requestCode")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildPersonPopupPendingIntent(context: Context, item: WidgetRowItem, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("rememberme://widget/$appWidgetId/person/${item.key}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("POPUP_PERSON_ID", item.personId)
            putExtra("POPUP_REASON", item.popupReason)
        }
        val reqCode = (appWidgetId * 31) xor item.key.hashCode()
        return PendingIntent.getActivity(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildListTemplatePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(
            context,
            700_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutFlag
        )
    }
}

private object NativeWidgetRenderer {
    private val SLOT_FRAME_IDS = intArrayOf(
        R.id.slot_frame_0, R.id.slot_frame_1, R.id.slot_frame_2,
        R.id.slot_frame_3, R.id.slot_frame_4, R.id.slot_frame_5
    )
    private val SLOT_INNER_IDS = intArrayOf(
        R.id.slot_inner_0, R.id.slot_inner_1, R.id.slot_inner_2,
        R.id.slot_inner_3, R.id.slot_inner_4, R.id.slot_inner_5
    )
    private val SLOT_NAME_IDS = intArrayOf(
        R.id.slot_name_0, R.id.slot_name_1, R.id.slot_name_2,
        R.id.slot_name_3, R.id.slot_name_4, R.id.slot_name_5
    )
    private val SLOT_META_IDS = intArrayOf(
        R.id.slot_meta_0, R.id.slot_meta_1, R.id.slot_meta_2,
        R.id.slot_meta_3, R.id.slot_meta_4, R.id.slot_meta_5
    )
    private val SLOT_BADGE_IDS = intArrayOf(
        R.id.slot_badge_0, R.id.slot_badge_1, R.id.slot_badge_2,
        R.id.slot_badge_3, R.id.slot_badge_4, R.id.slot_badge_5
    )

    private val TILE_FRAME_IDS = intArrayOf(R.id.tile_frame_0, R.id.tile_frame_1, R.id.tile_frame_2)
    private val TILE_INNER_IDS = intArrayOf(R.id.tile_inner_0, R.id.tile_inner_1, R.id.tile_inner_2)
    private val TILE_META_IDS = intArrayOf(R.id.tile_meta_0, R.id.tile_meta_1, R.id.tile_meta_2)
    private val TILE_BADGE_IDS = intArrayOf(R.id.tile_badge_0, R.id.tile_badge_1, R.id.tile_badge_2)
    private val TILE_NAME_IDS = intArrayOf(R.id.tile_name_0, R.id.tile_name_1, R.id.tile_name_2)

    private fun RemoteViews.tintRounded(viewId: Int, argb: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(argb))
        } else {
            setInt(viewId, "setBackgroundColor", argb)
        }
    }

    private fun resolveRowColors(item: WidgetRowItem, palette: WidgetPalette): IntArray {
        val frameCol: Int
        val fillCol: Int
        val nameCol: Int
        val metaCol: Int
        val badgeBg: Int
        val badgeFg: Int

        when {
            item.isBirthdayToday && item.isCongratulatedToday -> {
                frameCol = palette.bdayDoneFrame
                fillCol = palette.bdayDoneRowBg
                nameCol = palette.bdayDoneText
                metaCol = palette.bdayDoneText
                badgeBg = palette.bdayDoneBadgeBg
                badgeFg = palette.bdayDoneBadgeText
            }
            item.isBirthdayToday -> {
                frameCol = palette.bdayTodayFrame
                fillCol = palette.bdayTodayRowBg
                nameCol = palette.bdayTodayText
                metaCol = palette.bdayTodaySubText
                badgeBg = palette.bdayTodayBadgeBg
                badgeFg = palette.bdayTodayBadgeText
            }
            else -> {
                frameCol = palette.border
                fillCol = palette.rowBg
                nameCol = palette.textPrimary
                metaCol = palette.textSecondary
                badgeBg = if (item.isTodayOrDue) palette.todayBadgeBg else palette.badgeBg
                badgeFg = if (item.isTodayOrDue) palette.todayBadgeText else palette.badgeText
            }
        }
        return intArrayOf(frameCol, fillCol, nameCol, metaCol, badgeBg, badgeFg)
    }

    fun renderWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        kind: WidgetKind,
        allRows: List<WidgetRowItem>,
        palette: WidgetPalette
    ): RemoteViews {
        val opts: Bundle? = runCatching { appWidgetManager.getAppWidgetOptions(appWidgetId) }.getOrNull()
        val (defW, defH) = when (kind) {
            WidgetKind.MINI -> 160 to 90
            WidgetKind.STRIP -> 320 to 70
            WidgetKind.COMPACT_LIST -> 240 to 140
            WidgetKind.LARGE_LIST -> 320 to 220
        }
        val widthDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defW)?.takeIf { it > 0 } ?: defW
        val heightDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defH)?.takeIf { it > 0 }
            ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defH)?.takeIf { it > 0 }
            ?: defH

        return when (kind) {
            WidgetKind.MINI -> buildFourRowsView(context, appWidgetId, widthDp, heightDp, allRows.take(8), palette)
            WidgetKind.STRIP -> {
                if (widthDp < 225) {
                    buildFourRowsView(context, appWidgetId, widthDp, heightDp, allRows.take(6), palette)
                } else {
                    buildStripView(context, appWidgetId, widthDp, allRows.take(6), palette)
                }
            }
            WidgetKind.COMPACT_LIST -> {
                val subset = allRows.take(10)
                if (heightDp < 118 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    buildFourRowsView(context, appWidgetId, widthDp, heightDp, subset, palette)
                } else {
                    buildScrollableListView(context, appWidgetId, widthDp, "🎂 Termine", subset, palette)
                }
            }
            WidgetKind.LARGE_LIST -> {
                val subset = allRows.take(15)
                if (heightDp < 118 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    buildFourRowsView(context, appWidgetId, widthDp, heightDp, subset, palette)
                } else {
                    buildScrollableListView(context, appWidgetId, widthDp, "🎂 Termine (${subset.size})", subset, palette)
                }
            }
        }
    }

    private fun buildFourRowsView(
        context: Context,
        appWidgetId: Int,
        widthDp: Int,
        heightDp: Int,
        rows: List<WidgetRowItem>,
        palette: WidgetPalette
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_four_rows)
        val visibleSlots = if (heightDp >= 175) 6 else 4
        val shownRows = rows.take(visibleSlots)
        val hasOpenBdayToday = shownRows.any { it.isBirthdayToday && !it.isCongratulatedToday }
        val hasAnyBdayToday = shownRows.any { it.isBirthdayToday }
        val showSlimHeader = heightDp >= 126
        val inlineMetaMode = when {
            widthDp >= 225 -> 2
            widthDp >= 165 && heightDp >= 90 -> 1
            else -> 0
        }

        val outerCol = if (hasOpenBdayToday) palette.bdayTodayFrame else palette.border
        rv.tintRounded(R.id.w_outer_frame, outerCol)
        rv.tintRounded(R.id.w_inner_box, palette.bg)

        val openAppPi = RememberMeWidgetHelper.buildOpenAppPendingIntent(context, 100_000 + appWidgetId)
        rv.setOnClickPendingIntent(R.id.w_inner_box, openAppPi)

        if (shownRows.isEmpty()) {
            rv.setViewVisibility(R.id.w_header_row, View.GONE)
            rv.setViewVisibility(R.id.w_rows_container, View.GONE)
            rv.setViewVisibility(R.id.w_empty_text, View.VISIBLE)
            rv.setTextColor(R.id.w_empty_text, palette.textSecondary)
            rv.setOnClickPendingIntent(R.id.w_empty_text, openAppPi)
            return rv
        }

        rv.setViewVisibility(R.id.w_empty_text, View.GONE)
        rv.setViewVisibility(R.id.w_rows_container, View.VISIBLE)

        if (showSlimHeader) {
            val hdrText = when {
                hasOpenBdayToday -> "🎉 HEUTE GEBURTSTAG!"
                hasAnyBdayToday -> "🎂 Heute erledigt ✓"
                else -> "🎂 Termine"
            }
            val hdrCol = when {
                hasOpenBdayToday -> palette.bdayTodayBadgeBg
                hasAnyBdayToday -> palette.bdayDoneBadgeBg
                else -> palette.accent
            }
            rv.setViewVisibility(R.id.w_header_row, View.VISIBLE)
            rv.setTextViewText(R.id.w_header_title, hdrText)
            rv.setTextColor(R.id.w_header_title, hdrCol)
            rv.setTextColor(R.id.w_header_open, palette.textSecondary)
            rv.setOnClickPendingIntent(R.id.w_header_row, openAppPi)
        } else {
            rv.setViewVisibility(R.id.w_header_row, View.GONE)
        }

        for (slotIdx in 0 until 6) {
            val frameId = SLOT_FRAME_IDS[slotIdx]
            if (slotIdx >= visibleSlots) {
                rv.setViewVisibility(frameId, View.GONE)
                continue
            }
            val item = shownRows.getOrNull(slotIdx)
            if (item == null) {
                rv.setViewVisibility(frameId, View.INVISIBLE)
                continue
            }

            rv.setViewVisibility(frameId, View.VISIBLE)
            val cols = resolveRowColors(item, palette)
            val innerId = SLOT_INNER_IDS[slotIdx]
            val nameId = SLOT_NAME_IDS[slotIdx]
            val metaId = SLOT_META_IDS[slotIdx]
            val badgeId = SLOT_BADGE_IDS[slotIdx]

            rv.tintRounded(frameId, cols[0])
            rv.tintRounded(innerId, cols[1])

            rv.setTextViewText(nameId, "${item.icon} ${item.name}")
            rv.setTextColor(nameId, cols[2])

            val metaText = when (inlineMetaMode) {
                2 -> item.shortMeta
                1 -> item.compactDateOnly
                else -> ""
            }
            if (metaText.isNotBlank()) {
                rv.setViewVisibility(metaId, View.VISIBLE)
                rv.setTextViewText(metaId, metaText)
                rv.setTextColor(metaId, cols[3])
            } else {
                rv.setViewVisibility(metaId, View.GONE)
            }

            rv.setTextViewText(badgeId, item.badge)
            rv.setTextColor(badgeId, cols[5])
            rv.tintRounded(badgeId, cols[4])

            val personPi = RememberMeWidgetHelper.buildPersonPopupPendingIntent(context, item, appWidgetId)
            rv.setOnClickPendingIntent(frameId, personPi)
            rv.setOnClickPendingIntent(innerId, personPi)
        }

        return rv
    }

    private fun buildStripView(
        context: Context,
        appWidgetId: Int,
        widthDp: Int,
        rows: List<WidgetRowItem>,
        palette: WidgetPalette
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_strip_row)
        val colCount = if (widthDp >= 310 && rows.size >= 3) 3 else minOf(2, rows.size)
        val shown = rows.take(colCount)
        val hasOpenBdayToday = shown.any { it.isBirthdayToday && !it.isCongratulatedToday }

        rv.tintRounded(R.id.w_outer_frame, if (hasOpenBdayToday) palette.bdayTodayFrame else palette.border)
        rv.tintRounded(R.id.w_inner_box, palette.bg)

        val openAppPi = RememberMeWidgetHelper.buildOpenAppPendingIntent(context, 200_000 + appWidgetId)
        rv.setOnClickPendingIntent(R.id.w_inner_box, openAppPi)

        if (shown.isEmpty()) {
            rv.setViewVisibility(R.id.w_tiles_row, View.GONE)
            rv.setViewVisibility(R.id.w_empty_text, View.VISIBLE)
            rv.setTextColor(R.id.w_empty_text, palette.textSecondary)
            rv.setOnClickPendingIntent(R.id.w_empty_text, openAppPi)
            return rv
        }

        rv.setViewVisibility(R.id.w_empty_text, View.GONE)
        rv.setViewVisibility(R.id.w_tiles_row, View.VISIBLE)

        for (idx in 0 until 3) {
            val frameId = TILE_FRAME_IDS[idx]
            val item = shown.getOrNull(idx)
            if (item == null) {
                rv.setViewVisibility(frameId, View.GONE)
                continue
            }
            rv.setViewVisibility(frameId, View.VISIBLE)
            val cols = resolveRowColors(item, palette)
            val innerId = TILE_INNER_IDS[idx]
            rv.tintRounded(frameId, cols[0])
            rv.tintRounded(innerId, cols[1])

            rv.setTextViewText(TILE_META_IDS[idx], "${item.icon} ${item.shortMeta}")
            rv.setTextColor(TILE_META_IDS[idx], cols[3])

            rv.setTextViewText(TILE_BADGE_IDS[idx], item.badge)
            rv.setTextColor(TILE_BADGE_IDS[idx], cols[5])
            rv.tintRounded(TILE_BADGE_IDS[idx], cols[4])

            rv.setTextViewText(TILE_NAME_IDS[idx], item.name)
            rv.setTextColor(TILE_NAME_IDS[idx], cols[2])

            val personPi = RememberMeWidgetHelper.buildPersonPopupPendingIntent(context, item, appWidgetId)
            rv.setOnClickPendingIntent(frameId, personPi)
            rv.setOnClickPendingIntent(innerId, personPi)
        }
        return rv
    }

    private fun buildScrollableListView(
        context: Context,
        appWidgetId: Int,
        widthDp: Int,
        headerTitle: String,
        rows: List<WidgetRowItem>,
        palette: WidgetPalette
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_scrollable_list)
        val hasOpenBdayToday = rows.any { it.isBirthdayToday && !it.isCongratulatedToday }
        val hasAnyBdayToday = rows.any { it.isBirthdayToday }
        val inlineMetaMode = when {
            widthDp >= 220 -> 2
            widthDp >= 165 -> 1
            else -> 0
        }

        rv.tintRounded(R.id.w_outer_frame, if (hasOpenBdayToday) palette.bdayTodayFrame else palette.border)
        rv.tintRounded(R.id.w_inner_box, palette.bg)

        val effectiveHeader = when {
            hasOpenBdayToday -> "🎉 HEUTE GEBURTSTAG!"
            hasAnyBdayToday -> "🎂 Heute gratuliert ✓"
            else -> headerTitle
        }
        val effectiveHeaderColor = when {
            hasOpenBdayToday -> palette.bdayTodayBadgeBg
            hasAnyBdayToday -> palette.bdayDoneBadgeBg
            else -> palette.accent
        }

        val openAppPi = RememberMeWidgetHelper.buildOpenAppPendingIntent(context, 300_000 + appWidgetId)
        rv.setTextViewText(R.id.w_header_title, effectiveHeader)
        rv.setTextColor(R.id.w_header_title, effectiveHeaderColor)
        rv.setTextColor(R.id.w_header_open, palette.textSecondary)
        rv.setOnClickPendingIntent(R.id.w_header_row, openAppPi)

        if (rows.isEmpty()) {
            rv.setViewVisibility(R.id.w_list_view, View.GONE)
            rv.setViewVisibility(R.id.w_empty_text, View.VISIBLE)
            rv.setTextColor(R.id.w_empty_text, palette.textSecondary)
            rv.setOnClickPendingIntent(R.id.w_empty_text, openAppPi)
            return rv
        }

        rv.setViewVisibility(R.id.w_empty_text, View.GONE)
        rv.setViewVisibility(R.id.w_list_view, View.VISIBLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val builder = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)

            rows.forEachIndexed { index, item ->
                val itemRv = RemoteViews(context.packageName, R.layout.widget_list_item)
                val cols = resolveRowColors(item, palette)
                itemRv.tintRounded(R.id.li_frame, cols[0])
                itemRv.tintRounded(R.id.li_inner, cols[1])

                itemRv.setTextViewText(R.id.li_name, "${item.icon} ${item.name}")
                itemRv.setTextColor(R.id.li_name, cols[2])

                val metaText = when (inlineMetaMode) {
                    2 -> item.shortMeta
                    1 -> item.compactDateOnly
                    else -> ""
                }
                if (metaText.isNotBlank()) {
                    itemRv.setViewVisibility(R.id.li_meta, View.VISIBLE)
                    itemRv.setTextViewText(R.id.li_meta, metaText)
                    itemRv.setTextColor(R.id.li_meta, cols[3])
                } else {
                    itemRv.setViewVisibility(R.id.li_meta, View.GONE)
                }

                itemRv.setTextViewText(R.id.li_badge, item.badge)
                itemRv.setTextColor(R.id.li_badge, cols[5])
                itemRv.tintRounded(R.id.li_badge, cols[4])

                val fillIn = Intent().apply {
                    data = Uri.parse("rememberme://widget/$appWidgetId/list/${item.key}")
                    putExtra("POPUP_PERSON_ID", item.personId)
                    putExtra("POPUP_REASON", item.popupReason)
                }
                itemRv.setOnClickFillInIntent(R.id.li_frame, fillIn)
                itemRv.setOnClickFillInIntent(R.id.li_inner, fillIn)
                builder.addItem(item.key.hashCode().toLong() + index, itemRv)
            }

            rv.setPendingIntentTemplate(
                R.id.w_list_view,
                RememberMeWidgetHelper.buildListTemplatePendingIntent(context, appWidgetId)
            )
            rv.setRemoteAdapter(R.id.w_list_view, builder.build())
        }

        return rv
    }
}

object RememberMeWidgetUpdater {
    private val updateMutex = Mutex()

    private val PROVIDER_MAP: List<Pair<WidgetKind, Class<out AppWidgetProvider>>> = listOf(
        WidgetKind.MINI to MiniWidgetReceiver::class.java,
        WidgetKind.STRIP to StripWidgetReceiver::class.java,
        WidgetKind.COMPACT_LIST to BirthdayWidgetReceiver::class.java,
        WidgetKind.LARGE_LIST to LargeWidgetReceiver::class.java
    )

    private fun pushPaletteAndRowsSync(
        appCtx: Context,
        themeMode: String,
        rows: List<WidgetRowItem>,
        onlyKind: WidgetKind? = null,
        onlyIds: IntArray? = null
    ) {
        val manager = AppWidgetManager.getInstance(appCtx)
        val palette = RememberMeWidgetHelper.paletteForTheme(themeMode)

        for ((kind, cls) in PROVIDER_MAP) {
            if (onlyKind != null && kind != onlyKind) continue
            val ids = if (onlyKind != null && onlyIds != null && onlyIds.isNotEmpty()) {
                onlyIds
            } else {
                runCatching { manager.getAppWidgetIds(ComponentName(appCtx, cls)) }.getOrNull() ?: IntArray(0)
            }
            for (appWidgetId in ids) {
                runCatching {
                    val rv = NativeWidgetRenderer.renderWidget(appCtx, manager, appWidgetId, kind, rows, palette)
                    manager.updateAppWidget(appWidgetId, rv)
                    Log.d("RememberMeWidget", "Updated widgetId=$appWidgetId kind=$kind theme=$themeMode")
                }.onFailure { err ->
                    Log.e("RememberMeWidget", "Failed updating widgetId=$appWidgetId kind=$kind", err)
                }
            }
        }
    }

    fun notifyThemeChangedImmediate(context: Context, themeMode: String) {
        val appCtx = context.applicationContext
        val normalized = AppThemeRegistry.byId(themeMode).id
        // 1. Sofortiges synchrones Umfärben im selben Frame (< 2 ms) falls Zeilen im Speicher sind
        RememberMeWidgetHelper.cachedRows?.let { cached ->
            runCatching {
                pushPaletteAndRowsSync(appCtx, normalized, cached)
            }
        }
        // 2. Vollständiges Nachladen & Synchronisieren über Room-DB
        updateAllAsync(appCtx)
    }

    fun updateAllAsync(context: Context) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            updateAll(appCtx)
        }
    }

    suspend fun updateAll(context: Context) {
        val appCtx = context.applicationContext
        updateMutex.withLock {
            val rows = runCatching { RememberMeWidgetHelper.loadAllRows(appCtx, maxItems = 15) }
                .getOrElse { RememberMeWidgetHelper.cachedRows ?: emptyList() }
            val latestTheme = RememberMeWidgetHelper.currentSavedTheme(appCtx)
            pushPaletteAndRowsSync(appCtx, latestTheme, rows)
        }
    }

    suspend fun updateSpecificProvider(context: Context, kind: WidgetKind, appWidgetIds: IntArray?) {
        val appCtx = context.applicationContext
        updateMutex.withLock {
            val rows = runCatching { RememberMeWidgetHelper.loadAllRows(appCtx, maxItems = 15) }
                .getOrElse { RememberMeWidgetHelper.cachedRows ?: emptyList() }
            val latestTheme = RememberMeWidgetHelper.currentSavedTheme(appCtx)
            pushPaletteAndRowsSync(appCtx, latestTheme, rows, onlyKind = kind, onlyIds = appWidgetIds)
        }
    }
}

abstract class BaseRememberMeWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appCtx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RememberMeWidgetUpdater.updateSpecificProvider(appCtx, kind, appWidgetIds)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val appCtx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RememberMeWidgetUpdater.updateSpecificProvider(appCtx, kind, intArrayOf(appWidgetId))
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}

class MiniWidgetReceiver : BaseRememberMeWidgetProvider(WidgetKind.MINI)
class StripWidgetReceiver : BaseRememberMeWidgetProvider(WidgetKind.STRIP)
class BirthdayWidgetReceiver : BaseRememberMeWidgetProvider(WidgetKind.COMPACT_LIST)
class LargeWidgetReceiver : BaseRememberMeWidgetProvider(WidgetKind.LARGE_LIST)
