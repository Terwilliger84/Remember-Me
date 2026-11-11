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
    val popupReason: String,
    val badge: String,
    val isTodayOrDue: Boolean
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
    val todayBadgeText: Color
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
                todayBadgeText = Color(0xFFFFFFFF)
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
                todayBadgeText = Color(0xFFFFFFFF)
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
                todayBadgeText = Color(0xFFFFFFFF)
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
                todayBadgeText = Color(0xFFFFFFFF)
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
        if (raw.isBlank() || raw.equals("Alter unbekannt", ignoreCase = true)) return ""
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotBlank()) "${digits}." else ""
    }

    private fun resolveSpecialEventDate(rawSpec: String, today: LocalDate): LocalDate? {
        val spec = rawSpec.trim()
        return when {
            spec.startsWith("REPEAT:") -> {
                val parts = spec.split(":")
                if (parts.size >= 4) {
                    val count = parts[1].toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                    val unit = parts[2]
                    var nextD = runCatching { LocalDate.parse(parts[3]) }.getOrNull() ?: return null
                    var guard = 0
                    while (nextD.isBefore(today) && guard < 500) {
                        nextD = when (unit) {
                            "WEEKS" -> nextD.plusWeeks(count)
                            "MONTHS" -> nextD.plusMonths(count)
                            "YEARS" -> nextD.plusYears(count)
                            else -> nextD.plusDays(count)
                        }
                        guard++
                    }
                    nextD
                } else null
            }
            spec.startsWith("WEEKLY:") -> {
                val targetDow = spec.removePrefix("WEEKLY:").toIntOrNull() ?: return null
                val currentDow = today.dayOfWeek.value % 7
                val delta = ((targetDow - currentDow) + 7) % 7
                today.plusDays(delta.toLong())
            }
            else -> runCatching { LocalDate.parse(spec) }.getOrNull()
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

        // Sortierschlüssel: (Tage bis Termin, Typ-Priorität, Name)
        val candidates = mutableListOf<Triple<Long, Int, WidgetRowItem>>()

        for (p in persons) {
            val bdayDays = ContactResolver.daysUntilNextBirthday(p.birthdayIso, today)
            if (bdayDays != null && bdayDays <= 90L) {
                val ddMm = shortDateDdMm(p.birthdayIso)
                val ageDot = shortAgeNumber(p.birthdayIso, today)
                val meta = when {
                    ddMm.isNotBlank() && ageDot.isNotBlank() -> "$ddMm ($ageDot)"
                    ddMm.isNotBlank() -> ddMm
                    else -> "Geburtstag"
                }
                val ageText = ContactResolver.formatUpcomingAge(p.birthdayIso, today)
                val reason = if (ageText.equals("Alter unbekannt", ignoreCase = true)) {
                    "🎂 Geburtstag"
                } else {
                    "🎂 Geburtstag ($ageText)"
                }
                val badge = when (bdayDays) {
                    0L -> "Heute!"
                    1L -> "Morgen"
                    else -> "${bdayDays}T"
                }
                candidates += Triple(
                    bdayDays,
                    0,
                    WidgetRowItem(
                        key = "bday_${p.id}",
                        personId = p.id,
                        icon = "🎂",
                        name = p.name,
                        shortMeta = meta,
                        popupReason = reason,
                        badge = badge,
                        isTodayOrDue = bdayDays == 0L
                    )
                )
            }

            val timerDate = ContactResolver.nextTimerDate(p, today)
            if (timerDate != null) {
                val diff = ChronoUnit.DAYS.between(today, timerDate)
                if (diff <= 14L) {
                    val sortDays = diff.coerceAtLeast(0L)
                    val badge = when {
                        diff <= 0L -> "Fällig"
                        diff == 1L -> "Morgen"
                        else -> "${diff}T"
                    }
                    candidates += Triple(
                        sortDays,
                        2,
                        WidgetRowItem(
                            key = "timer_${p.id}",
                            personId = p.id,
                            icon = "⏳",
                            name = p.name,
                            shortMeta = "Timer",
                            popupReason = "⏳ Melde-Timer",
                            badge = badge,
                            isTodayOrDue = diff <= 0L
                        )
                    )
                }
            }
        }

        for (ev in events) {
            val person = persons.find { it.id == ev.personId } ?: continue
            val evDate = resolveSpecialEventDate(ev.dateIso, today) ?: continue
            val diff = ChronoUnit.DAYS.between(today, evDate)
            if (diff in 0L..60L) {
                val ddMm = "%02d.%02d.".format(evDate.dayOfMonth, evDate.monthValue)
                val badge = when (diff) {
                    0L -> "Heute"
                    1L -> "Morgen"
                    else -> "${diff}T"
                }
                candidates += Triple(
                    diff,
                    1,
                    WidgetRowItem(
                        key = "ev_${ev.id}",
                        personId = person.id,
                        icon = "🔔",
                        name = person.name,
                        shortMeta = "${ev.title} ($ddMm)",
                        popupReason = "🔔 ${ev.title}",
                        badge = badge,
                        isTodayOrDue = diff == 0L
                    )
                )
            }
        }

        val sorted = candidates
            .sortedWith(compareBy<Triple<Long, Int, WidgetRowItem>> { it.first }.thenBy { it.second }.thenBy { it.third.name.lowercase() })
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
private fun CompactBadgePill(text: String, isHighlighted: Boolean, palette: WidgetPalette) {
    val bg = if (isHighlighted) palette.todayBadgeBg else palette.badgeBg
    val fg = if (isHighlighted) palette.todayBadgeText else palette.badgeText
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(bg))
            .cornerRadius(6.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(fg),
                fontSize = 10.sp,
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
    showMetaInline: Boolean = true
) {
    val launchIntent = RememberMeWidgetHelper.buildPersonPopupIntent(context, item)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(palette.rowBg))
            .cornerRadius(8.dp)
            .clickable(actionStartActivity(launchIntent))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "${item.icon} ${item.name}",
                style = TextStyle(
                    color = ColorProvider(palette.textPrimary),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            if (!showMetaInline && item.shortMeta.isNotBlank()) {
                Text(
                    text = item.shortMeta,
                    style = TextStyle(
                        color = ColorProvider(palette.textSecondary),
                        fontSize = 9.5.sp
                    )
                )
            }
        }
        if (showMetaInline && item.shortMeta.isNotBlank()) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = item.shortMeta,
                style = TextStyle(
                    color = ColorProvider(palette.textSecondary),
                    fontSize = 10.sp
                )
            )
        }
        Spacer(modifier = GlanceModifier.width(5.dp))
        CompactBadgePill(
            text = item.badge,
            isHighlighted = item.isTodayOrDue,
            palette = palette
        )
    }
}

/**
 * 1. MINI-WIDGET (2x1): Ultra-kompakt für den nächsten Termin (wächst automatisch mit, wenn höher gezogen).
 */
class MiniBirthdayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 4)
        provideContent {
            val size = LocalSize.current
            val openAppIntent = RememberMeWidgetHelper.buildOpenAppIntent(context)
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(palette.bg))
                    .cornerRadius(12.dp)
                    .padding(6.dp)
            ) {
                if (rows.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity(openAppIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎂 Keine Termine",
                            style = TextStyle(
                                color = ColorProvider(palette.textSecondary),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                } else if (size.height < 90.dp) {
                    val first = rows.first()
                    val firstIntent = RememberMeWidgetHelper.buildPersonPopupIntent(context, first)
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(palette.rowBg))
                            .cornerRadius(9.dp)
                            .clickable(actionStartActivity(firstIntent))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${first.icon} ${first.shortMeta}",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = ColorProvider(palette.accent),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            CompactBadgePill(
                                text = first.badge,
                                isHighlighted = first.isTodayOrDue,
                                palette = palette
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = first.name,
                            style = TextStyle(
                                color = ColorProvider(palette.textPrimary),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    val visibleCount = if (size.height >= 140.dp) 3 else 2
                    rows.take(visibleCount).forEachIndexed { idx, item ->
                        if (idx > 0) Spacer(modifier = GlanceModifier.height(3.dp))
                        CompactSingleRowEntry(
                            context = context,
                            item = item,
                            palette = palette,
                            showMetaInline = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. KOMPAKT-LEISTE (4x1): Flache 1-Zeilen-Leiste über die Bildschirmbreite (2 bis 3 Termine nebeneinander).
 */
class StripBirthdayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 6)
        provideContent {
            val size = LocalSize.current
            val openAppIntent = RememberMeWidgetHelper.buildOpenAppIntent(context)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(palette.bg))
                    .cornerRadius(12.dp)
                    .padding(6.dp)
            ) {
                if (rows.isEmpty()) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity(openAppIntent))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎂 Remember Me – Keine anstehenden Termine",
                            style = TextStyle(
                                color = ColorProvider(palette.textSecondary),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                } else {
                    val colCount = if (size.width >= 310.dp && rows.size >= 3) 3 else minOf(2, rows.size)
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rows.take(colCount).forEachIndexed { idx, item ->
                            if (idx > 0) Spacer(modifier = GlanceModifier.width(5.dp))
                            val itemIntent = RememberMeWidgetHelper.buildPersonPopupIntent(context, item)
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .background(ColorProvider(palette.rowBg))
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
                                        style = TextStyle(
                                            color = ColorProvider(palette.textSecondary),
                                            fontSize = 9.5.sp
                                        )
                                    )
                                    CompactBadgePill(
                                        text = item.badge,
                                        isHighlighted = item.isTodayOrDue,
                                        palette = palette
                                    )
                                }
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = item.name,
                                    style = TextStyle(
                                        color = ColorProvider(palette.textPrimary),
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

/**
 * 3. KOMPAKT-LISTE (3x2 / Standard): Deutlich kompakter als bisher, einzeilige Karten & scrollbar.
 */
class BirthdayGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 8)
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
 * 4. ÜBERSICHT GROSS (4x3): Große, dennoch sehr kompakte & scrollbare Liste für bis zu 15 Termine.
 */
class LargeBirthdayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (palette, rows) = RememberMeWidgetHelper.loadWidgetState(context, maxItems = 15)
        provideContent {
            CompactListWidgetLayout(
                context = context,
                headerTitle = "🎂 Geburtstage & Termine (${rows.size})",
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
    val showInlineMeta = size.width >= 210.dp
    val openAppIntent = RememberMeWidgetHelper.buildOpenAppIntent(context)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.bg))
            .cornerRadius(13.dp)
            .padding(if (compactMode) 6.dp else 8.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openAppIntent))
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerTitle,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(palette.accent),
                    fontSize = if (compactMode) 11.5.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Öffnen ↗",
                style = TextStyle(
                    color = ColorProvider(palette.textSecondary),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (rows.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity(openAppIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine anstehenden Einträge",
                    style = TextStyle(
                        color = ColorProvider(palette.textSecondary),
                        fontSize = 11.sp
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.key.hashCode().toLong() }) { item ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 3.dp)) {
                        CompactSingleRowEntry(
                            context = context,
                            item = item,
                            palette = palette,
                            showMetaInline = showInlineMeta
                        )
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
