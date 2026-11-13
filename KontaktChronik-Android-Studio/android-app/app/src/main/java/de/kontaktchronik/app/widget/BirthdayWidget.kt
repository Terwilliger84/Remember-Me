package de.kontaktchronik.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.model.BirthdayCongratulationTracker
import de.kontaktchronik.app.model.ContactResolver
import de.kontaktchronik.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val bg: Color,
    val rowBg: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val todayBadgeBg: Color,
    val todayBadgeText: Color,
    val bdayTodayFrame: Color,
    val bdayTodayRowBg: Color,
    val bdayTodayText: Color,
    val bdayTodaySubText: Color,
    val bdayTodayBadgeBg: Color,
    val bdayTodayBadgeText: Color,
    val bdayDoneFrame: Color,
    val bdayDoneRowBg: Color,
    val bdayDoneText: Color,
    val bdayDoneBadgeBg: Color,
    val bdayDoneBadgeText: Color
)

object RememberMeWidgetHelper {

    fun paletteForTheme(themeMode: String?): WidgetPalette {
        return when (themeMode) {
            "cozy-dark" -> WidgetPalette(
                bg = Color(0xFF132030),
                rowBg = Color(0xFF1C2E42),
                accent = Color(0xFF63B3ED),
                textPrimary = Color(0xFFF0F6FC),
                textSecondary = Color(0xFF9BB0C9),
                badgeBg = Color(0xFF263E57),
                badgeText = Color(0xFFE2E8F0),
                todayBadgeBg = Color(0xFF3182CE),
                todayBadgeText = Color(0xFFFFFFFF),
                bdayTodayFrame = Color(0xFFFBBF24),
                bdayTodayRowBg = Color(0xFF3B2810),
                bdayTodayText = Color(0xFFFEF3C7),
                bdayTodaySubText = Color(0xFFFCD34D),
                bdayTodayBadgeBg = Color(0xFFD97706),
                bdayTodayBadgeText = Color(0xFFFFFFFF),
                bdayDoneFrame = Color(0xFF34D399),
                bdayDoneRowBg = Color(0xFF13362B),
                bdayDoneText = Color(0xFFD1FAE5),
                bdayDoneBadgeBg = Color(0xFF059669),
                bdayDoneBadgeText = Color(0xFFFFFFFF)
            )
            "xmas" -> WidgetPalette(
                bg = Color(0xFF14261C),
                rowBg = Color(0xFF1E3528),
                accent = Color(0xFFFC8181),
                textPrimary = Color(0xFFF7FAFC),
                textSecondary = Color(0xFFB2D8C2),
                badgeBg = Color(0xFF284736),
                badgeText = Color(0xFFE6FFFA),
                todayBadgeBg = Color(0xFFC53030),
                todayBadgeText = Color(0xFFFFFFFF),
                bdayTodayFrame = Color(0xFFFBBF24),
                bdayTodayRowBg = Color(0xFF3E2414),
                bdayTodayText = Color(0xFFFEF3C7),
                bdayTodaySubText = Color(0xFFFDE68A),
                bdayTodayBadgeBg = Color(0xFFD97706),
                bdayTodayBadgeText = Color(0xFFFFFFFF),
                bdayDoneFrame = Color(0xFF34D399),
                bdayDoneRowBg = Color(0xFF163D2D),
                bdayDoneText = Color(0xFFD1FAE5),
                bdayDoneBadgeBg = Color(0xFF059669),
                bdayDoneBadgeText = Color(0xFFFFFFFF)
            )
            "classic" -> WidgetPalette(
                bg = Color(0xFF0F172A),
                rowBg = Color(0xFF1E293B),
                accent = Color(0xFF38BDF8),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFF94A3B8),
                badgeBg = Color(0xFF334155),
                badgeText = Color(0xFFE2E8F0),
                todayBadgeBg = Color(0xFF0284C7),
                todayBadgeText = Color(0xFFFFFFFF),
                bdayTodayFrame = Color(0xFFFBBF24),
                bdayTodayRowBg = Color(0xFF3B2810),
                bdayTodayText = Color(0xFFFEF3C7),
                bdayTodaySubText = Color(0xFFFCD34D),
                bdayTodayBadgeBg = Color(0xFFD97706),
                bdayTodayBadgeText = Color(0xFFFFFFFF),
                bdayDoneFrame = Color(0xFF34D399),
                bdayDoneRowBg = Color(0xFF12352A),
                bdayDoneText = Color(0xFFD1FAE5),
                bdayDoneBadgeBg = Color(0xFF059669),
                bdayDoneBadgeText = Color(0xFFFFFFFF)
            )
            else -> WidgetPalette( // "warm" – Sanftes Blau (Hell)
                bg = Color(0xFFEBF3FC),
                rowBg = Color(0xFFFFFFFF),
                accent = Color(0xFF2B6CB0),
                textPrimary = Color(0xFF1A2B3C),
                textSecondary = Color(0xFF4A607A),
                badgeBg = Color(0xFFD6E6F7),
                badgeText = Color(0xFF1E4E8C),
                todayBadgeBg = Color(0xFF2B6CB0),
                todayBadgeText = Color(0xFFFFFFFF),
                bdayTodayFrame = Color(0xFFF59E0B),
                bdayTodayRowBg = Color(0xFFFFFBEB),
                bdayTodayText = Color(0xFF92400E),
                bdayTodaySubText = Color(0xFFB45309),
                bdayTodayBadgeBg = Color(0xFFD97706),
                bdayTodayBadgeText = Color(0xFFFFFFFF),
                bdayDoneFrame = Color(0xFF10B981),
                bdayDoneRowBg = Color(0xFFECFDF5),
                bdayDoneText = Color(0xFF065F46),
                bdayDoneBadgeBg = Color(0xFF15803D),
                bdayDoneBadgeText = Color(0xFFFFFFFF)
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
                val currentDow = today.dayOfWeek.value // 1=Mo..7=So
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

    suspend fun loadWidgetState(context: Context, maxItems: Int): Pair<WidgetPalette, List<WidgetRowItem>> {
        val prefs = context.getSharedPreferences("remember_me_prefs", Context.MODE_PRIVATE)
        val palette = paletteForTheme(prefs.getString("theme_mode", "warm"))
        val hiddenIds = prefs.getStringSet("hidden_person_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        val dao = AppDatabase.getInstance(context).dao()
        val persons = dao.getAllPersonsOnce().filter { !it.isHidden && !hiddenIds.contains(it.id) }
        val events = dao.getAllSpecialEventsOnce()
        val today = LocalDate.now()
        val congratsRecords = BirthdayCongratulationTracker.loadAllRecords(context)

        // Sortierschlüssel: (Tage bis Ereignis, Typ-Priorität, Name)
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

        return palette to sorted
    }

    fun buildOpenAppIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("rememberme://widget/open_home")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    fun buildPersonPopupIntent(context: Context, item: WidgetRowItem): Intent =
        Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("rememberme://widget/person/${item.key}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("POPUP_PERSON_ID", item.personId)
            putExtra("POPUP_REASON", item.popupReason)
        }
}

object RememberMeWidgetUpdater {
    fun updateAllAsync(context: Context) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            updateAll(appCtx)
        }
    }

    suspend fun updateAll(context: Context) {
        runCatching { MiniBirthdayWidget().updateAll(context) }
        runCatching { StripBirthdayWidget().updateAll(context) }
        runCatching { BirthdayGlanceWidget().updateAll(context) }
        runCatching { LargeBirthdayWidget().updateAll(context) }
    }
}

@Composable
private fun CompactBadgePill(
    item: WidgetRowItem,
    palette: WidgetPalette,
    ultraTight: Boolean = false
) {
    val bg = when {
        item.isBirthdayToday && item.isCongratulatedToday -> palette.bdayDoneBadgeBg
        item.isBirthdayToday -> palette.bdayTodayBadgeBg
        item.isTodayOrDue -> palette.todayBadgeBg
        else -> palette.badgeBg
    }
    val fg = when {
        item.isBirthdayToday && item.isCongratulatedToday -> palette.bdayDoneBadgeText
        item.isBirthdayToday -> palette.bdayTodayBadgeText
        item.isTodayOrDue -> palette.todayBadgeText
        else -> palette.badgeText
    }
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(bg))
            .cornerRadius(if (ultraTight) 5.dp else 6.dp)
            .padding(
                horizontal = if (ultraTight) 4.dp else 5.dp,
                vertical = if (ultraTight) 1.dp else 1.5.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.badge,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(fg),
                fontSize = if (ultraTight) 8.8.sp else 9.8.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun CompactSingleRowEntry(
    context: Context,
    item: WidgetRowItem,
    palette: WidgetPalette,
    inlineMetaMode: Int = 2, // 0 = kein Meta, 1 = nur Datum dd.MM., 2 = volles Kurz-Meta
    ultraTight: Boolean = false
) {
    val launchIntent = RememberMeWidgetHelper.buildPersonPopupIntent(context, item)
    val metaText = when (inlineMetaMode) {
        2 -> item.shortMeta
        1 -> item.compactDateOnly
        else -> ""
    }

    if (item.isBirthdayToday) {
        val frameColor = if (item.isCongratulatedToday) palette.bdayDoneFrame else palette.bdayTodayFrame
        val innerBg = if (item.isCongratulatedToday) palette.bdayDoneRowBg else palette.bdayTodayRowBg
        val titleColor = if (item.isCongratulatedToday) palette.bdayDoneText else palette.bdayTodayText
        val subColor = if (item.isCongratulatedToday) palette.bdayDoneText else palette.bdayTodaySubText

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(frameColor))
                .cornerRadius(if (ultraTight) 6.dp else 8.dp)
                .padding(if (ultraTight) 1.dp else 1.5.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(innerBg))
                    .cornerRadius(if (ultraTight) 5.dp else 7.dp)
                    .clickable(actionStartActivity(launchIntent))
                    .padding(
                        horizontal = if (ultraTight) 4.dp else 6.dp,
                        vertical = if (ultraTight) 1.dp else 2.5.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.icon} ${item.name}",
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(titleColor),
                        fontSize = if (ultraTight) 9.8.sp else 11.2.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (metaText.isNotBlank() && !ultraTight) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = metaText,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(subColor),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
                CompactBadgePill(item = item, palette = palette, ultraTight = ultraTight)
            }
        }
        return
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(palette.rowBg))
            .cornerRadius(if (ultraTight) 6.dp else 7.dp)
            .clickable(actionStartActivity(launchIntent))
            .padding(
                horizontal = if (ultraTight) 5.dp else 6.dp,
                vertical = if (ultraTight) 1.2.dp else 2.5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${item.icon} ${item.name}",
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(palette.textPrimary),
                fontSize = if (ultraTight) 9.8.sp else 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (metaText.isNotBlank()) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = metaText,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.textSecondary),
                    fontSize = if (ultraTight) 8.8.sp else 9.8.sp
                )
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        CompactBadgePill(item = item, palette = palette, ultraTight = ultraTight)
    }
}

/**
 * Optimierter 4-Zeilen-Block für ein 2-Felder-Widget (2×1, 1×2 oder 2×2):
 * Passt exakt 4 einzeilige Einträge ohne Textumbruch ein und hebt heutige Geburtstage besonders hervor.
 */
@Composable
private fun FourRowTwoCellWidgetContent(
    context: Context,
    rows: List<WidgetRowItem>,
    palette: WidgetPalette
) {
    val size = LocalSize.current
    val openAppIntent = RememberMeWidgetHelper.buildOpenAppIntent(context)
    val visibleCount = if (size.height >= 175.dp) 6 else 4
    val shownRows = rows.take(visibleCount)
    val hasOpenBirthdayToday = shownRows.any { it.isBirthdayToday && !it.isCongratulatedToday }
    val hasAnyBirthdayToday = shownRows.any { it.isBirthdayToday }

    val ultraTight = size.height < 92.dp
    val showSlimHeader = size.height >= 126.dp
    val inlineMetaMode = when {
        size.width >= 225.dp -> 2
        size.width >= 165.dp && !ultraTight -> 1
        else -> 0
    }

    val outerModifier = if (hasOpenBirthdayToday) {
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.bdayTodayFrame))
            .cornerRadius(12.dp)
            .padding(if (ultraTight) 1.dp else 1.5.dp)
    } else {
        GlanceModifier.fillMaxSize()
    }

    Box(modifier = outerModifier) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.bg))
                .cornerRadius(if (hasOpenBirthdayToday) 11.dp else 12.dp)
                .padding(
                    horizontal = if (ultraTight) 4.dp else 6.dp,
                    vertical = if (ultraTight) 2.5.dp else 4.dp
                )
        ) {
            if (shownRows.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(openAppIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎂 Keine Termine",
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.textSecondary),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                if (showSlimHeader) {
                    val hdrText = when {
                        hasOpenBirthdayToday -> "🎉 HEUTE GEBURTSTAG!"
                        hasAnyBirthdayToday -> "🎂 Heute erledigt ✓"
                        else -> "🎂 Termine"
                    }
                    val hdrColor = when {
                        hasOpenBirthdayToday -> palette.bdayTodayBadgeBg
                        hasAnyBirthdayToday -> palette.bdayDoneBadgeBg
                        else -> palette.accent
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(openAppIntent))
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = hdrText,
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(hdrColor),
                                fontSize = 10.8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "↗",
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(palette.textSecondary),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.5.dp))
                }

                val rowGap = if (ultraTight) 1.5.dp else 2.5.dp
                shownRows.forEachIndexed { idx, item ->
                    if (idx > 0) Spacer(modifier = GlanceModifier.height(rowGap))
                    CompactSingleRowEntry(
                        context = context,
                        item = item,
                        palette = palette,
                        inlineMetaMode = inlineMetaMode,
                        ultraTight = ultraTight
                    )
                }

                if (shownRows.size < 4) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity(openAppIntent))
                    ) {}
                }
            }
        }
    }
}

/**
 * 1. KOMPAKT-WIDGET (2×1 / 2 Felder groß): Zeigt 4 einzeilige Terminzeilen mit Heute-Geburtstags-Markierung.
 */
class MiniBirthdayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 8)
        provideContent {
            FourRowTwoCellWidgetContent(
                context = context,
                rows = rows,
                palette = palette
            )
        }
    }
}

/**
 * 2. KOMPAKT-LEISTE (4×1): Flache Leiste über die Breite; auf 2 Felder verkleinert automatisch 4 Zeilen.
 */
class StripBirthdayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 6)
        provideContent {
            val size = LocalSize.current
            if (size.width < 225.dp) {
                FourRowTwoCellWidgetContent(
                    context = context,
                    rows = rows,
                    palette = palette
                )
                return@provideContent
            }

            val openAppIntent = RememberMeWidgetHelper.buildOpenAppIntent(context)
            val colCount = if (size.width >= 310.dp && rows.size >= 3) 3 else minOf(2, rows.size)
            val shown = rows.take(colCount)
            val hasOpenBdayToday = shown.any { it.isBirthdayToday && !it.isCongratulatedToday }

            val outerMod = if (hasOpenBdayToday) {
                GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(palette.bdayTodayFrame))
                    .cornerRadius(12.dp)
                    .padding(1.5.dp)
            } else {
                GlanceModifier.fillMaxSize()
            }

            Box(modifier = outerMod) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(palette.bg))
                        .cornerRadius(if (hasOpenBdayToday) 11.dp else 12.dp)
                        .padding(5.dp)
                ) {
                    if (shown.isEmpty()) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .clickable(actionStartActivity(openAppIntent))
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎂 Keine anstehenden Termine",
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(palette.textSecondary),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    } else {
                        Row(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            shown.forEachIndexed { idx, item ->
                                if (idx > 0) Spacer(modifier = GlanceModifier.width(5.dp))
                                val itemIntent = RememberMeWidgetHelper.buildPersonPopupIntent(context, item)
                                val cardBg = when {
                                    item.isBirthdayToday && item.isCongratulatedToday -> palette.bdayDoneRowBg
                                    item.isBirthdayToday -> palette.bdayTodayRowBg
                                    else -> palette.rowBg
                                }
                                val nameCol = when {
                                    item.isBirthdayToday && item.isCongratulatedToday -> palette.bdayDoneText
                                    item.isBirthdayToday -> palette.bdayTodayText
                                    else -> palette.textPrimary
                                }
                                val subCol = when {
                                    item.isBirthdayToday && item.isCongratulatedToday -> palette.bdayDoneText
                                    item.isBirthdayToday -> palette.bdayTodaySubText
                                    else -> palette.textSecondary
                                }
                                Column(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .background(ColorProvider(cardBg))
                                        .cornerRadius(8.dp)
                                        .clickable(actionStartActivity(itemIntent))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.icon} ${item.shortMeta}",
                                            modifier = GlanceModifier.defaultWeight(),
                                            maxLines = 1,
                                            style = TextStyle(
                                                color = ColorProvider(subCol),
                                                fontSize = 9.5.sp,
                                                fontWeight = if (item.isBirthdayToday) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        CompactBadgePill(item = item, palette = palette, ultraTight = true)
                                    }
                                    Spacer(modifier = GlanceModifier.height(1.dp))
                                    Text(
                                        text = item.name,
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = ColorProvider(nameCol),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. KOMPAKT-LISTE (3×2 / 2×2): Einzeilige Karten, mind. 4 volle Zeilen auf 2 Feldern Höhe & scrollbar.
 */
class BirthdayGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 10)
        provideContent {
            CompactListWidgetLayout(
                context = context,
                headerTitle = "🎂 Termine",
                rows = rows,
                palette = palette,
                compactMode = true
            )
        }
    }
}

/**
 * 4. ÜBERSICHT GROSS (4×3): Scrollbare Liste für bis zu 15 Termine mit Heute-Geburtstags-Markierung.
 */
class LargeBirthdayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 15)
        provideContent {
            CompactListWidgetLayout(
                context = context,
                headerTitle = "🎂 Termine (${rows.size})",
                rows = rows,
                palette = palette,
                compactMode = false
            )
        }
    }
}

@Composable
private fun CompactListWidgetLayout(
    context: Context,
    headerTitle: String,
    rows: List<WidgetRowItem>,
    palette: WidgetPalette,
    compactMode: Boolean
) {
    val size = LocalSize.current
    if (size.height < 118.dp) {
        FourRowTwoCellWidgetContent(
            context = context,
            rows = rows,
            palette = palette
        )
        return
    }

    val inlineMetaMode = when {
        size.width >= 220.dp -> 2
        size.width >= 165.dp -> 1
        else -> 0
    }
    val openAppIntent = RememberMeWidgetHelper.buildOpenAppIntent(context)
    val hasOpenBirthdayToday = rows.any { it.isBirthdayToday && !it.isCongratulatedToday }
    val hasAnyBirthdayToday = rows.any { it.isBirthdayToday }

    val effectiveHeader = when {
        hasOpenBirthdayToday -> "🎉 HEUTE GEBURTSTAG!"
        hasAnyBirthdayToday -> "🎂 Heute gratuliert ✓"
        else -> headerTitle
    }
    val effectiveHeaderColor = when {
        hasOpenBirthdayToday -> palette.bdayTodayBadgeBg
        hasAnyBirthdayToday -> palette.bdayDoneBadgeBg
        else -> palette.accent
    }

    val outerModifier = if (hasOpenBirthdayToday) {
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.bdayTodayFrame))
            .cornerRadius(13.dp)
            .padding(1.5.dp)
    } else {
        GlanceModifier.fillMaxSize()
    }

    Box(modifier = outerModifier) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.bg))
                .cornerRadius(if (hasOpenBirthdayToday) 12.dp else 13.dp)
                .padding(if (compactMode) 5.dp else 7.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openAppIntent))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = effectiveHeader,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(effectiveHeaderColor),
                        fontSize = if (compactMode) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Öffnen ↗",
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.textSecondary),
                        fontSize = 9.8.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            if (rows.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(openAppIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine anstehenden Termine",
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.textSecondary),
                            fontSize = 11.sp
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(rows, itemId = { it.key.hashCode().toLong() }) { item ->
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.5.dp)) {
                            CompactSingleRowEntry(
                                context = context,
                                item = item,
                                palette = palette,
                                inlineMetaMode = inlineMetaMode,
                                ultraTight = false
                            )
                        }
                    }
                }
            }
        }
    }
}

class MiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniBirthdayWidget()
}

class StripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StripBirthdayWidget()
}

class BirthdayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BirthdayGlanceWidget()
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeBirthdayWidget()
}
