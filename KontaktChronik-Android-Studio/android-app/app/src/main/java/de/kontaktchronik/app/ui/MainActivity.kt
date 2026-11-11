package de.kontaktchronik.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.data.ContactDeduplicator
import de.kontaktchronik.app.data.ContactsSyncRepository
import de.kontaktchronik.app.data.DeviceContactFullDetails
import de.kontaktchronik.app.messaging.DirectMessagingService
import de.kontaktchronik.app.model.*
import de.kontaktchronik.app.reminders.BirthdayNotificationHelper
import de.kontaktchronik.app.reminders.DailyReminderWorker
import de.kontaktchronik.app.reminders.QuickActionDispatcher
import de.kontaktchronik.app.widget.RememberMeWidgetUpdater
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// "warm" (Standard: Sanftes Blau), "cozy-dark" (Sanftes Nachtblau), "xmas" (Weihnachts-Modus), "classic"
private var appThemeMode by mutableStateOf("warm")
private val isLightMode: Boolean get() = appThemeMode == "warm"

private val BgSlate: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFFF2F7FC)
        "cozy-dark" -> Color(0xFF131F31)
        "xmas" -> Color(0xFF0F241D)
        else -> Color(0xFF0F172A)
    }

private val CardSlate: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFFFFFFFF)
        "cozy-dark" -> Color(0xFF1B2B42)
        "xmas" -> Color(0xFF163329)
        else -> Color(0xFF1E293B)
    }

private val BorderSlate: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFFCFE0F0)
        "cozy-dark" -> Color(0xFF2C4263)
        "xmas" -> Color(0xFF265444)
        else -> Color(0xFF334155)
    }

private val BtnBg: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFFE3EFFA)
        "cozy-dark" -> Color(0xFF243854)
        "xmas" -> Color(0xFF20483A)
        else -> Color(0xFF334155)
    }

private val RootNodeBg: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFFDFEFFC)
        "cozy-dark" -> Color(0xFF1E3A5F)
        "xmas" -> Color(0xFF3F1D24)
        else -> Color(0xFF1E3A5F)
    }

private val TextPrimary: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFF182B42)
        "cozy-dark" -> Color(0xFFF0F6FC)
        "xmas" -> Color(0xFFFEFCE8)
        else -> Color(0xFFF8FAFC)
    }

private val TextSecondary: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFF415A77)
        "cozy-dark" -> Color(0xFFB8CCE0)
        "xmas" -> Color(0xFFD1FAE5)
        else -> Color(0xFFCBD5E1)
    }

private val TextMuted: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFF728CA8)
        "cozy-dark" -> Color(0xFF7E97B3)
        "xmas" -> Color(0xFF86EFAC)
        else -> Color(0xFF94A3B8)
    }

private val AccentSteel: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFF3B82C4)
        "cozy-dark" -> Color(0xFF63A4FF)
        "xmas" -> Color(0xFFFBBF24)
        else -> Color(0xFF38BDF8)
    }

private val WarnAmber: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFF2563EB)
        "cozy-dark" -> Color(0xFF60A5FA)
        "xmas" -> Color(0xFFF87171)
        else -> Color(0xFFF59E0B)
    }

private val PartnerRose: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFFE11D48)
        "cozy-dark" -> Color(0xFFFB7185)
        "xmas" -> Color(0xFFFB7185)
        else -> Color(0xFFF43F5E)
    }

private val TreeGreen: Color
    get() = when (appThemeMode) {
        "warm" -> Color(0xFF15803D)
        "cozy-dark" -> Color(0xFF34D399)
        "xmas" -> Color(0xFF34D399)
        else -> Color(0xFF10B981)
    }

fun getThemeIcon(mode: String): String = when (mode) {
    "warm" -> "☀️"
    "cozy-dark" -> "🌙"
    "xmas" -> "🎄"
    else -> "💎"
}

fun nextThemeMode(mode: String): String = when (mode) {
    "warm" -> "cozy-dark"
    "cozy-dark" -> "xmas"
    "xmas" -> "classic"
    else -> "warm"
}

private val AVATAR_GRADIENTS = listOf(
    listOf(Color(0xFF4A90E2), Color(0xFF2563EB)),
    listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
    listOf(Color(0xFF60A5FA), Color(0xFF3B82F6)),
    listOf(Color(0xFF6366F1), Color(0xFF4338CA)),
    listOf(Color(0xFF2DD4BF), Color(0xFF0D9488)),
    listOf(Color(0xFF4C6EF5), Color(0xFF364FC7)),
    listOf(Color(0xFF0CA678), Color(0xFF087F5B))
)

fun getPersonInitials(name: String): String {
    val clean = stripNameRoleSuffix(name)
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return "${parts.first().first()}${parts.last().first()}".uppercase()
}

fun encodeUriToBase64Jpeg(context: android.content.Context, uri: android.net.Uri, maxDim: Int = 256): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream) ?: return ""
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val scale = (maxDim.toFloat() / maxOf(w, h).toFloat()).coerceAtMost(1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bmp
            }
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.orEmpty()
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun PersonAvatar(name: String, photoBase64: String = "", sizeDp: Int = 36) {
    val imageBitmap = remember(photoBase64, name) {
        val raw = photoBase64.trim()
        if (raw.isNotBlank()) {
            runCatching {
                val clean = raw.substringAfter("base64,", raw).trim()
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else {
            null
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .border(1.dp, BorderSlate, CircleShape)
        )
        return
    }
    // Sofortiges illustriertes Kontaktporträt (auch bevor die DB-Synchronisation durchgelaufen ist)
    val n = remember(name) { name.lowercase() }
    val gradColors = remember(name) {
        val hash = abs(name.fold(0) { acc, ch -> ch.code + ((acc shl 5) - acc) })
        AVATAR_GRADIENTS[hash % AVATAR_GRADIENTS.size]
    }
    Canvas(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .border(1.dp, BorderSlate, CircleShape)
    ) {
        val w = size.width
        val h = size.height
        drawRect(brush = Brush.linearGradient(gradColors))
        val skinColor = Color(0xFFF6D0B8)
        val hairColor = when {
            n.contains("helga") || n.contains("oma") -> Color(0xFFD9D9E0)
            n.contains("sabine") -> Color(0xFFB44C27)
            n.contains("leo") || n.contains("baby") -> Color(0xFFC68B59)
            else -> Color(0xFF4E3222)
        }
        val shirtColor = when {
            n.contains("helga") || n.contains("oma") -> Color(0xFF8E3A59)
            n.contains("anna") -> Color(0xFF2E7D5B)
            n.contains("thomas") -> Color(0xFF2B4C7E)
            else -> Color(0xFF334155)
        }
        // Schultern
        drawOval(
            color = shirtColor,
            topLeft = Offset(w * 0.16f, h * 0.72f),
            size = Size(w * 0.68f, h * 0.45f)
        )
        // Kopf
        drawOval(
            color = skinColor,
            topLeft = Offset(w * 0.29f, h * 0.24f),
            size = Size(w * 0.42f, h * 0.44f)
        )
        // Haare oben
        drawArc(
            color = hairColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.27f, h * 0.20f),
            size = Size(w * 0.46f, h * 0.34f)
        )
        // Augen
        drawCircle(color = Color(0xFF2C201B), radius = w * 0.025f, center = Offset(w * 0.41f, h * 0.46f))
        drawCircle(color = Color(0xFF2C201B), radius = w * 0.025f, center = Offset(w * 0.59f, h * 0.46f))
        // Lächeln
        drawArc(
            color = Color(0xFFA84A3A),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.41f, h * 0.49f),
            size = Size(w * 0.18f, h * 0.10f),
            style = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        )
    }
}

private val CenteredPillTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

@Composable
fun BirthdayCountdownPill(birthdayIso: String?, isPastMode: Boolean) {
    if (birthdayIso.isNullOrBlank()) return
    val today = LocalDate.now()
    if (isPastMode) {
        val neg = ContactResolver.negativeDaysSinceLastBirthday(birthdayIso, today) ?: return
        val label = when (neg) {
            0L -> "🎂 Heute!"
            else -> "vor ${abs(neg)}T"
        }
        Box(
            modifier = Modifier
                .background(BtnBg, RoundedCornerShape(999.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = CenteredPillTextStyle,
                maxLines = 1,
                softWrap = false
            )
        }
        return
    }
    val days = ContactResolver.daysUntilNextBirthday(birthdayIso, today) ?: return
    val isToday = days == 0L
    val isSoon = days in 1L..14L
    val label = when (days) {
        0L -> "🎂 Heute!"
        1L -> "🎂 Morgen!"
        else -> "🎂 in ${days}T"
    }
    val bgColor = when {
        isToday -> WarnAmber
        isSoon -> AccentSteel.copy(alpha = 0.16f)
        else -> BtnBg
    }
    val borderColor = when {
        isToday -> WarnAmber
        isSoon -> AccentSteel
        else -> BorderSlate
    }
    val textColor = when {
        isToday -> Color.White
        isSoon -> AccentSteel
        else -> TextSecondary
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = CenteredPillTextStyle,
            maxLines = 1,
            softWrap = false
        )
    }
}

val RELATION_PRESETS = listOf(
    "Kind von",
    "Ehe-Partner von",
    "Partner von",
    "Bruder von",
    "Schwester von",
    "Mutter von",
    "Vater von",
    "Elternteil von",
    "Oma/Opa von",
    "Enkelkind von",
    "Onkel/Tante von",
    "Cousin/Cousine von"
)

fun inverseRelationLabel(label: String): String {
    val l = label.trim().lowercase()
    return when {
        l.startsWith("kind") || l.startsWith("sohn") || l.startsWith("tochter") -> "Elternteil von"
        l.startsWith("mutter") || l.startsWith("vater") || l.startsWith("elternteil") -> "Kind von"
        l.startsWith("ehe-partner") || l.startsWith("ehepartner") -> "Ehe-Partner von"
        l.startsWith("partner") -> "Partner von"
        l.startsWith("bruder") || l.startsWith("schwester") || l.startsWith("geschwister") -> "Geschwister von"
        l.startsWith("oma") || l.startsWith("opa") || l.startsWith("großelter") -> "Enkelkind von"
        l.startsWith("enkel") -> "Oma/Opa von"
        l.startsWith("onkel") || l.startsWith("tante") -> "Neffe/Nichte von"
        l.startsWith("neffe") || l.startsWith("nichte") -> "Onkel/Tante von"
        l.startsWith("cousin") -> "Cousin/Cousine von"
        else -> "Verknüpft mit"
    }
}

/**
 * Bestimmt den Generationen-Abstand von `fromPerson` zu `toPerson`:
 * Wenn `fromPerson` "Kind von" `toPerson` ist -> `toPerson` liegt 1 Generation höher (-1).
 * Wenn `fromPerson` "Mutter/Vater/Elternteil von" `toPerson` ist -> `toPerson` liegt 1 Generation tiefer (+1).
 * Wenn `fromPerson` "Ehe-Partner/Bruder/Schwester von" `toPerson` ist -> gleiche Generation (0).
 */
fun relationGenerationStep(label: String): Int {
    val l = label.trim().lowercase()
    return when {
        l.startsWith("kind") || l.startsWith("sohn") || l.startsWith("tochter") -> -1
        l.startsWith("enkel") -> -2
        l.startsWith("neffe") || l.startsWith("nichte") -> -1
        l.startsWith("mutter") || l.startsWith("vater") || l.startsWith("elternteil") -> 1
        l.startsWith("oma") || l.startsWith("opa") || l.startsWith("großelter") -> 2
        l.startsWith("onkel") || l.startsWith("tante") -> 1
        else -> 0
    }
}

fun isPartnerRelation(label: String): Boolean {
    val l = label.trim().lowercase()
    return l.contains("partner") || l.contains("ehe") || l.contains("verheiratet")
}

fun isSiblingRelation(label: String): Boolean {
    val l = label.trim().lowercase()
    return l.contains("bruder") || l.contains("schwester") || l.contains("geschwister")
}

val WEEKDAY_NAMES_DE = mapOf(
    1 to "Montag",
    2 to "Dienstag",
    3 to "Mittwoch",
    4 to "Donnerstag",
    5 to "Freitag",
    6 to "Samstag",
    7 to "Sonntag"
)

fun isWeeklyDateSpec(dateIso: String?): Boolean =
    dateIso?.trim()?.startsWith("WEEKLY:") == true

fun isRepeatDateSpec(dateIso: String?): Boolean =
    dateIso?.trim()?.startsWith("REPEAT:") == true

fun addIntervalToIsoDate(baseDate: LocalDate, count: Int, unit: String): LocalDate {
    val n = count.coerceAtLeast(1).toLong()
    return when (unit.uppercase()) {
        "WEEKS" -> baseDate.plusWeeks(n)
        "MONTHS" -> baseDate.plusMonths(n)
        "YEARS" -> baseDate.plusYears(n)
        else -> baseDate.plusDays(n)
    }
}

fun formatRhythmTextDe(count: Int, unit: String): String {
    val n = count.coerceAtLeast(1)
    return when (unit.uppercase()) {
        "WEEKS" -> if (n == 1) "1 Woche" else "$n Wochen"
        "MONTHS" -> if (n == 1) "1 Monat" else "$n Monate"
        "YEARS" -> if (n == 1) "1 Jahr" else "$n Jahre"
        else -> if (n == 1) "1 Tag" else "$n Tage"
    }
}

data class ParsedRepeatSpec(val count: Int, val unit: String, val nextDate: LocalDate)

fun parseRepeatDateSpec(dateIso: String?, today: LocalDate = LocalDate.now()): ParsedRepeatSpec? {
    if (!isRepeatDateSpec(dateIso)) return null
    val parts = dateIso!!.trim().split(":")
    val count = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val unit = (parts.getOrNull(2) ?: "DAYS").uppercase()
    var nextDate = runCatching { LocalDate.parse(parts.getOrNull(3).orEmpty()) }
        .getOrElse { addIntervalToIsoDate(today, count, unit) }
    var guard = 0
    while (nextDate.isBefore(today) && guard < 1000) {
        nextDate = addIntervalToIsoDate(nextDate, count, unit)
        guard++
    }
    return ParsedRepeatSpec(count, unit, nextDate)
}

fun getWeeklyDayNumber(dateIso: String?): Int? {
    if (!isWeeklyDateSpec(dateIso)) return null
    val num = dateIso!!.trim().substringAfter("WEEKLY:").toIntOrNull()
    return if (num != null && num in 1..7) num else null
}

fun daysUntilWeeklyDay(weekday1To7: Int, today: LocalDate = LocalDate.now()): Long {
    val isoToday = today.dayOfWeek.value // 1=Mon..7=Sun
    return ((weekday1To7 - isoToday + 7) % 7).toLong()
}

fun daysUntilEventSpec(dateIso: String?, today: LocalDate = LocalDate.now()): Long? {
    if (dateIso.isNullOrBlank()) return null
    if (isRepeatDateSpec(dateIso)) {
        val rep = parseRepeatDateSpec(dateIso, today) ?: return null
        return ChronoUnit.DAYS.between(today, rep.nextDate)
    }
    if (isWeeklyDateSpec(dateIso)) {
        val wd = getWeeklyDayNumber(dateIso) ?: return null
        return daysUntilWeeklyDay(wd, today)
    }
    val parsed = runCatching { LocalDate.parse(dateIso.trim()) }.getOrNull() ?: return null
    return ChronoUnit.DAYS.between(today, parsed)
}

fun formatEventBadgeText(ev: SpecialEventEntity, today: LocalDate = LocalDate.now()): String {
    if (isRepeatDateSpec(ev.dateIso)) {
        val rep = parseRepeatDateSpec(ev.dateIso, today) ?: return ev.title
        val rhythmStr = formatRhythmTextDe(rep.count, rep.unit)
        val nextStr = formatIsoToDdMmYyyy(rep.nextDate.toString())
        val diff = ChronoUnit.DAYS.between(today, rep.nextDate)
        return if (diff == 0L) {
            "🔔 Heute: ${ev.title} (alle $rhythmStr)"
        } else {
            "🔁 Alle $rhythmStr: ${ev.title} ($nextStr)"
        }
    }
    if (isWeeklyDateSpec(ev.dateIso)) {
        val wd = getWeeklyDayNumber(ev.dateIso) ?: 1
        val dayName = WEEKDAY_NAMES_DE[wd] ?: "Wochentag"
        val diff = daysUntilWeeklyDay(wd, today)
        return when (diff) {
            0L -> "🔔 Heute ($dayName): ${ev.title}"
            1L -> "🔁 Jeden $dayName: ${ev.title} (morgen)"
            else -> "🔁 Jeden $dayName: ${ev.title} (in ${diff}T)"
        }
    }
    return "${ev.title} (${formatIsoToDdMmYyyy(ev.dateIso)})"
}

fun stripNameRoleSuffix(fullName: String): String =
    fullName.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()

fun extractFirstName(fullName: String): String {
    val clean = stripNameRoleSuffix(fullName)
    if (clean.contains(",")) {
        val after = clean.substringAfter(",").trim()
        if (after.isNotBlank()) {
            return after.split(Regex("\\s+")).firstOrNull()?.ifBlank { after } ?: after
        }
    }
    return clean.split(Regex("\\s+")).firstOrNull()?.ifBlank { clean } ?: clean
}

private val COMPANY_NAME_REGEX = Regex(
    "\\b(gmbh|mbh|kg|ohg|gbr|ag|ug|e\\.?v\\.?|co\\.|praxis|kanzlei|apotheke|autohaus|bäckerei|baeckerei|metzgerei|baugeschäft|baugeschaeft|hotel|restaurant|gasthof|schule|gemeinde|stadt|sparkasse|bank|versicherung|klinik|zentrum|verein)\\b",
    RegexOption.IGNORE_CASE
)

private val TRAILING_CONTACT_TAGS = setOf(
    "mobil", "handy", "privat", "arbeit", "büro", "buero", "festnetz", "tel", "tel.", "telefon", "zentrale", "home", "work"
)

fun isLikelyOrganizationName(cleanName: String): Boolean =
    COMPANY_NAME_REGEX.containsMatchIn(cleanName)

fun extractLastName(fullName: String): String {
    val clean = stripNameRoleSuffix(fullName)
    if (clean.isBlank()) return fullName.trim()
    if (clean.contains(",")) {
        val before = clean.substringBefore(",").trim()
        if (before.isNotBlank()) return before
    }
    if (isLikelyOrganizationName(clean)) {
        return clean
    }
    var parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    while (parts.size > 2 && TRAILING_CONTACT_TAGS.contains(parts.last().lowercase())) {
        parts = parts.dropLast(1)
    }
    return if (parts.size > 1) parts.last() else (parts.firstOrNull() ?: clean)
}

fun formatLastNameFirst(fullName: String): String {
    val trimmed = fullName.trim()
    if (trimmed.isBlank() || trimmed.contains(",")) return trimmed
    val roleMatch = Regex("(\\s*\\([^)]*\\))$").find(trimmed)
    val roleSuffix = roleMatch?.value.orEmpty()
    val clean = stripNameRoleSuffix(trimmed)
    if (isLikelyOrganizationName(clean)) return trimmed
    val rawParts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (rawParts.size <= 1) return trimmed
    val trailingTags = mutableListOf<String>()
    var coreParts = rawParts
    while (coreParts.size > 2 && TRAILING_CONTACT_TAGS.contains(coreParts.last().lowercase())) {
        trailingTags.add(0, coreParts.last())
        coreParts = coreParts.dropLast(1)
    }
    val last = coreParts.last()
    val first = coreParts.dropLast(1).joinToString(" ")
    val extraTagSuffix = if (trailingTags.isNotEmpty()) " (${trailingTags.joinToString(" ")})" else ""
    return "$last, $first$extraTagSuffix$roleSuffix"
}

fun normalizeLatinLetter(ch: Char): String {
    val upper = ch.uppercaseChar()
    when (upper) {
        'Ä' -> return "A"
        'Ö', 'Ø' -> return "O"
        'Ü' -> return "U"
        'ß' -> return "S"
        'Ł' -> return "L"
        'Đ' -> return "D"
    }
    if (upper in 'A'..'Z') return upper.toString()
    val decomposed = Normalizer.normalize(upper.toString(), Normalizer.Form.NFD)
    val baseChar = decomposed.firstOrNull { it in 'A'..'Z' }
    return baseChar?.toString() ?: "#"
}

fun normalizeGermanSortKey(raw: String): String {
    val pre = raw.trim().lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace("ł", "l")
        .replace("đ", "d")
        .replace("ø", "o")
    return Normalizer.normalize(pre, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}

val LastNamePersonComparator: Comparator<PersonEntity> =
    compareBy<PersonEntity> { normalizeGermanSortKey(extractLastName(it.name)) }
        .thenBy { normalizeGermanSortKey(extractFirstName(it.name)) }
        .thenBy { it.name.lowercase() }

fun List<PersonEntity>.sortedByLastName(): List<PersonEntity> =
    this.sortedWith(LastNamePersonComparator)

fun extractLastNameInitial(fullName: String): String {
    val last = extractLastName(fullName).trim().ifBlank { fullName.trim() }
    val ch = last.firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (ch.isLetter()) normalizeLatinLetter(ch) else "#"
}

fun extractTextInitial(text: String): String {
    val ch = text.trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (ch.isLetter()) normalizeLatinLetter(ch) else "#"
}

fun matchesPersonSearchAndLetter(person: PersonEntity, query: String, letter: String): Boolean {
    if (letter.isNotBlank() && letter != "Alle") {
        if (extractLastNameInitial(person.name) != letter) return false
    }
    val qRaw = query.trim().lowercase()
    if (qRaw.isBlank()) return true
    val fullLower = person.name.lowercase()
    val lastFirstLower = formatLastNameFirst(person.name).lowercase()
    val phoneLower = person.phone.lowercase()
    if (fullLower.contains(qRaw) || lastFirstLower.contains(qRaw) || phoneLower.contains(qRaw)) return true

    val haystackNorm = normalizeGermanSortKey("${person.name} ${formatLastNameFirst(person.name)} ${person.phone}")
    val tokens = qRaw.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    return tokens.all { token ->
        val tokenNorm = normalizeGermanSortKey(token)
        fullLower.contains(token) ||
            lastFirstLower.contains(token) ||
            phoneLower.contains(token) ||
            (tokenNorm.isNotBlank() && haystackNorm.contains(tokenNorm))
    }
}

val ALPHABET_ROW_1 = listOf("Alle", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M")
val ALPHABET_ROW_2 = listOf("N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#")

@Composable
fun AlphabetQuickBar(
    selectedLetter: String,
    onSelectLetter: (String) -> Unit,
    availableLetters: Set<String>? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSlate, RoundedCornerShape(6.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(ALPHABET_ROW_1, ALPHABET_ROW_2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { item ->
                    val isAll = item == "Alle"
                    val isActive = (isAll && (selectedLetter.isBlank() || selectedLetter == "Alle")) ||
                        (!isAll && selectedLetter == item)
                    val hasEntries = isAll || availableLetters == null || availableLetters.contains(item)
                    Box(
                        modifier = Modifier
                            .weight(if (isAll) 1.55f else 1f)
                            .background(
                                if (isActive) AccentSteel else CardSlate,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isActive) AccentSteel else BorderSlate,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                if (isAll || selectedLetter == item) {
                                    onSelectLetter("Alle")
                                } else {
                                    onSelectLetter(item)
                                }
                            }
                            .padding(vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item,
                            color = when {
                                isActive -> BgSlate
                                hasEntries -> TextPrimary
                                else -> TextMuted.copy(alpha = 0.4f)
                            },
                            fontSize = if (isAll) 8.5.sp else 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = CenteredPillTextStyle,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactSearchDropdown(
    persons: List<PersonEntity>,
    selectedPersonId: Long? = null,
    onSelectPerson: (PersonEntity) -> Unit,
    placeholderText: String = "🔎 Kontakt suchen / Dropdown (A–Z)...",
    confirmButtonLabel: String = "✓ Bestätigen",
    onConfirmAction: (() -> Unit)? = null,
    allowNone: Boolean = false,
    noneLabel: String = "-- Keine Verknüpfung --",
    onSelectNone: (() -> Unit)? = null,
    externalSearchQuery: String? = null,
    onExternalSearchQueryChange: ((String) -> Unit)? = null,
    externalSelectedLetter: String? = null,
    onExternalSelectedLetterChange: ((String) -> Unit)? = null,
    showInlineAlphabet: Boolean = false,
    checkedPersonIds: Set<Long>? = null,
    onToggleCheckedPerson: ((PersonEntity) -> Unit)? = null,
    hiddenPersons: List<PersonEntity> = emptyList(),
    onShowHidden: (() -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(false) }
    var internalQuery by remember { mutableStateOf("") }
    var internalLetter by remember { mutableStateOf("Alle") }

    val query = externalSearchQuery ?: internalQuery
    val setQuery: (String) -> Unit = onExternalSearchQueryChange ?: { internalQuery = it }
    val letter = externalSelectedLetter ?: internalLetter
    val setLetter: (String) -> Unit = onExternalSelectedLetterChange ?: { internalLetter = it }

    val availableLetters = remember(persons) {
        persons.map { extractLastNameInitial(it.name) }.toSet()
    }
    val sortedFiltered = remember(persons, query, letter) {
        persons
            .filter { matchesPersonSearchAndLetter(it, query, letter) }
            .sortedWith(LastNamePersonComparator)
    }
    val matchingHiddenCount = remember(hiddenPersons, query, letter) {
        if (hiddenPersons.isEmpty() || (query.isBlank() && (letter.isBlank() || letter == "Alle"))) 0
        else hiddenPersons.count { matchesPersonSearchAndLetter(it, query, letter) }
    }
    val selectedPerson = remember(persons, selectedPersonId) {
        persons.find { it.id == selectedPersonId }
    }
    val displayTitle = when {
        selectedPerson != null -> formatLastNameFirst(selectedPerson.name)
        allowNone && selectedPersonId == -1L -> noneLabel
        query.isNotBlank() || (letter.isNotBlank() && letter != "Alle") -> {
            val filterInfo = buildList {
                if (letter.isNotBlank() && letter != "Alle") add("[$letter]")
                if (query.isNotBlank()) add("„$query“")
            }.joinToString(" ")
            "🔎 Filter $filterInfo (${sortedFiltered.size} Treffer)"
        }
        else -> "$placeholderText (${persons.size})"
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (internalExpanded || query.isNotBlank() || (letter.isNotBlank() && letter != "Alle")) AccentSteel else BorderSlate,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { internalExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTitle,
                    color = if (selectedPerson != null || query.isNotBlank() || (letter.isNotBlank() && letter != "Alle")) TextPrimary else TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = if (selectedPerson != null) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (query.isNotBlank() || (letter.isNotBlank() && letter != "Alle")) {
                        Text(
                            text = "✕",
                            color = WarnAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    setQuery("")
                                    setLetter("Alle")
                                }
                                .padding(horizontal = 4.dp)
                        )
                    }
                    Text(
                        text = "▼ (${sortedFiltered.size})",
                        color = AccentSteel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (internalExpanded) {
                Dialog(
                    onDismissRequest = { internalExpanded = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { internalExpanded = false }
                            .padding(horizontal = 14.dp, vertical = 28.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardSlate, RoundedCornerShape(12.dp))
                                .border(1.5.dp, AccentSteel, RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { }
                        ) {
                            // Oben fixierte Leiste: Bestätigen + Suche + ABCD-Schnellwahl
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedPerson != null) {
                                            "✓ ${formatLastNameFirst(selectedPerson.name)}"
                                        } else {
                                            "📋 ${sortedFiltered.size} Kontakte (Nachname A–Z)"
                                        },
                                        color = AccentSteel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CompactButton("✕") {
                                            internalExpanded = false
                                        }
                                        CompactButton(confirmButtonLabel) {
                                            onConfirmAction?.invoke()
                                            internalExpanded = false
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { setQuery(it) },
                                    label = { Text("🔎 Nachname / Vorname suchen...", fontSize = 11.sp) },
                                    trailingIcon = {
                                        if (query.isNotBlank()) {
                                            Text(
                                                "✕",
                                                color = WarnAmber,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { setQuery("") }
                                                    .padding(6.dp)
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                AlphabetQuickBar(
                                    selectedLetter = letter,
                                    onSelectLetter = { setLetter(it) },
                                    availableLetters = availableLetters
                                )
                            }

                            HorizontalDivider(color = BorderSlate)

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            ) {
                                if (allowNone) {
                                    item(key = "none_option") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectNone?.invoke()
                                                    internalExpanded = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                noneLabel,
                                                color = if (selectedPersonId == -1L) AccentSteel else TextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = if (selectedPersonId == -1L) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f))
                                    }
                                }
                                if (matchingHiddenCount > 0 && onShowHidden != null) {
                                    item(key = "hidden_hint_option") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgSlate)
                                                .clickable {
                                                    internalExpanded = false
                                                    onShowHidden()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "👁 $matchingHiddenCount ausgeblendet für diesen Filter",
                                                color = WarnAmber,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "Ausgeblendete zeigen →",
                                                color = AccentSteel,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        HorizontalDivider(color = WarnAmber.copy(alpha = 0.5f))
                                    }
                                }
                                if (sortedFiltered.isEmpty()) {
                                    item(key = "empty_option") {
                                        Box(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                "Keine Kontakte für diesen Filter gefunden.",
                                                color = TextMuted,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }
                                } else {
                                    items(sortedFiltered, key = { it.id }) { p ->
                                        val isSelected = p.id == selectedPersonId
                                        val isChecked = checkedPersonIds?.contains(p.id) == true
                                        val formattedName = formatLastNameFirst(p.name)
                                        val bdayShort = formatIsoToDdMmYyyy(p.birthdayIso)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isSelected || isChecked) BtnBg else CardSlate)
                                                .clickable {
                                                    if (onToggleCheckedPerson != null) {
                                                        onToggleCheckedPerson(p)
                                                    } else {
                                                        onSelectPerson(p)
                                                        internalExpanded = false
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when {
                                                    checkedPersonIds != null -> "${if (isChecked) "☑️" else "⬜"} $formattedName"
                                                    isSelected -> "✓ $formattedName"
                                                    else -> formattedName
                                                },
                                                color = if (isSelected || isChecked) AccentSteel else TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isChecked) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (bdayShort.isNotBlank()) {
                                                Text(
                                                    text = bdayShort,
                                                    color = TextMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.45f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showInlineAlphabet) {
            AlphabetQuickBar(
                selectedLetter = letter,
                onSelectLetter = { setLetter(it) },
                availableLetters = availableLetters
            )
        }
    }
}

fun formatIsoToDdMmYyyy(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val clean = iso.trim()
    if (isRepeatDateSpec(clean)) {
        val rep = parseRepeatDateSpec(clean) ?: return clean
        val dParts = rep.nextDate.toString().split("-")
        val nextFormatted = if (dParts.size == 3) {
            "${dParts[2].padStart(2, '0')}.${dParts[1].padStart(2, '0')}.${dParts[0]}"
        } else rep.nextDate.toString()
        return "🔁 Alle ${formatRhythmTextDe(rep.count, rep.unit)} • Nächster: $nextFormatted"
    }
    if (isWeeklyDateSpec(clean)) {
        val wd = getWeeklyDayNumber(clean) ?: 1
        return "🔁 Jeden ${WEEKDAY_NAMES_DE[wd] ?: "Wochentag"}"
    }
    if (clean.startsWith("--")) {
        val parts = clean.removePrefix("--").split("-")
        if (parts.size == 2) return "${parts[1].padStart(2, '0')}.${parts[0].padStart(2, '0')}."
    }
    val parts = clean.split("-")
    return if (parts.size == 3) {
        "${parts[2].padStart(2, '0')}.${parts[1].padStart(2, '0')}.${parts[0]}"
    } else clean
}

fun normalizeDateInputToIso(input: String): String {
    val clean = input.trim()
    if (isWeeklyDateSpec(clean) || isRepeatDateSpec(clean)) return clean
    if (clean.contains(".")) {
        val parts = clean.trimEnd('.').split(".")
        if (parts.size == 3) {
            return "${parts[2].padStart(4, '0')}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
        }
        if (parts.size == 2) {
            return "--${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
        }
    }
    return clean
}

fun formatCompactBirthdayLine(birthdayIso: String?, today: LocalDate = LocalDate.now()): String? {
    if (birthdayIso.isNullOrBlank()) return null
    val dateStr = formatIsoToDdMmYyyy(birthdayIso)
    val ageStr = ContactResolver.formatUpcomingAge(birthdayIso, today)
    val days = ContactResolver.daysUntilNextBirthday(birthdayIso, today)
    val whenStr = when (days) {
        null -> ""
        0L -> "heute"
        1L -> "in 1 Tag"
        else -> "in $days Tagen"
    }
    return listOf(dateStr, "$ageStr $whenStr".trim()).filter { it.isNotBlank() }.joinToString("  •  ")
}

fun formatPassedBirthdayLine(birthdayIso: String?, today: LocalDate = LocalDate.now()): String? {
    if (birthdayIso.isNullOrBlank()) return null
    val dateStr = formatIsoToDdMmYyyy(birthdayIso)
    val passedAgeStr = ContactResolver.formatPassedAge(birthdayIso, today)
    val negDays = ContactResolver.negativeDaysSinceLastBirthday(birthdayIso, today) ?: return dateStr
    val negLabel = when (negDays) {
        0L -> "0 Tage (heute)"
        -1L -> "-1 Tag"
        else -> "$negDays Tage"
    }
    return "$dateStr  •  $passedAgeStr ($negLabel)"
}

data class OverviewTimelineItem(
    val uniqueKey: String,
    val person: PersonEntity,
    val daysUntil: Long,
    val typePriority: Int, // 0 = Geburtstag, 1 = Erinnerung/Termin, 2 = Melde-Timer, 3 = nur Verknüpfung
    val pillText: String,
    val isUrgent: Boolean,
    val isSoon: Boolean,
    val isRecurring: Boolean,
    val primaryLine: String,
    val secondaryLine: String = "",
    val popupReason: String
)

@Composable
fun OverviewCountdownPill(pillText: String, isUrgent: Boolean, isSoon: Boolean, isRecurring: Boolean = false) {
    if (pillText.isBlank()) return
    val bgColor = when {
        isUrgent -> WarnAmber
        isSoon -> AccentSteel.copy(alpha = 0.16f)
        isRecurring -> TreeGreen.copy(alpha = 0.14f)
        else -> BtnBg
    }
    val borderColor = when {
        isUrgent -> WarnAmber
        isSoon -> AccentSteel
        isRecurring -> TreeGreen
        else -> BorderSlate
    }
    val textColor = when {
        isUrgent -> Color.White
        isSoon -> AccentSteel
        isRecurring -> TreeGreen
        else -> TextSecondary
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = pillText,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = CenteredPillTextStyle,
            maxLines = 1,
            softWrap = false
        )
    }
}

fun buildOverviewTimelineItems(
    filteredPersons: List<PersonEntity>,
    allPersons: List<PersonEntity>,
    specialEvents: List<SpecialEventEntity>,
    relations: List<RelationEntity>,
    showPastDates: Boolean,
    sortMode: String, // "upcoming" | "lastname"
    today: LocalDate = LocalDate.now()
): List<OverviewTimelineItem> {
    val entries = mutableListOf<OverviewTimelineItem>()

    if (showPastDates) {
        filteredPersons.forEach { person ->
            if (!person.birthdayIso.isNullOrBlank()) {
                val negDays = ContactResolver.negativeDaysSinceLastBirthday(person.birthdayIso, today)
                if (negDays != null) {
                    val pill = if (negDays == 0L) "🎂 Heute!" else "🎂 vor ${abs(negDays)}T"
                    entries += OverviewTimelineItem(
                        uniqueKey = "past_bday_${person.id}",
                        person = person,
                        daysUntil = abs(negDays),
                        typePriority = 0,
                        pillText = pill,
                        isUrgent = negDays == 0L,
                        isSoon = abs(negDays) <= 14L,
                        isRecurring = false,
                        primaryLine = "🎂 ${formatPassedBirthdayLine(person.birthdayIso, today).orEmpty()}",
                        popupReason = "Nachträglich zum Geburtstag"
                    )
                }
            }
            specialEvents.filter { it.personId == person.id }.forEach { ev ->
                if (!isWeeklyDateSpec(ev.dateIso) && !isRepeatDateSpec(ev.dateIso)) {
                    val d = runCatching { LocalDate.parse(ev.dateIso.trim()) }.getOrNull()
                    if (d != null && !d.isAfter(today)) {
                        val ago = ChronoUnit.DAYS.between(d, today)
                        val pill = if (ago == 0L) "🔔 Heute!" else "🔔 vor ${ago}T"
                        entries += OverviewTimelineItem(
                            uniqueKey = "past_ev_${ev.id}",
                            person = person,
                            daysUntil = ago,
                            typePriority = 1,
                            pillText = pill,
                            isUrgent = ago == 0L,
                            isSoon = ago <= 14L,
                            isRecurring = false,
                            primaryLine = "🔔 ${ev.title}  •  ${formatIsoToDdMmYyyy(ev.dateIso)}",
                            secondaryLine = ev.note,
                            popupReason = "Erinnerung: ${ev.title}"
                        )
                    }
                }
            }
        }
        return if (sortMode == "lastname") {
            entries.sortedWith(
                compareBy<OverviewTimelineItem, PersonEntity>(LastNamePersonComparator) { it.person }
                    .thenBy { it.daysUntil }
            )
        } else {
            entries.sortedWith(
                compareBy<OverviewTimelineItem> { it.daysUntil }
                    .thenBy { it.typePriority }
                    .thenBy(LastNamePersonComparator) { it.person }
            )
        }
    }

    // Anstehende Ereignisse inkl. mehrfacher Einträge bei wiederkehrenden Terminen / Timern & mehreren Ereignissen pro Kontakt
    filteredPersons.forEach { person ->
        var hasAnyDatedEntry = false

        val personRelations = relations
            .filter { it.fromPersonId == person.id }
            .mapNotNull { rel -> allPersons.find { it.id == rel.toPersonId }?.let { rel to it } }
        val incomingOnlyRelations = relations
            .filter { inc ->
                inc.toPersonId == person.id &&
                    personRelations.none { it.second.id == inc.fromPersonId }
            }
            .mapNotNull { inc ->
                allPersons.find { it.id == inc.fromPersonId }?.let { inverseRelationLabel(inc.relationLabel) to it }
            }
        val relationSummary = buildList {
            personRelations.forEach { (rel, target) ->
                add("${rel.relationLabel} ${formatLastNameFirst(target.name)}")
            }
            incomingOnlyRelations.forEach { (invLabel, target) ->
                add("$invLabel ${formatLastNameFirst(target.name)}")
            }
        }.joinToString(" • ")

        // 1. Geburtstag
        if (!person.birthdayIso.isNullOrBlank()) {
            val bdayDays = ContactResolver.daysUntilNextBirthday(person.birthdayIso, today)
            if (bdayDays != null) {
                hasAnyDatedEntry = true
                val pill = when (bdayDays) {
                    0L -> "🎂 Heute!"
                    1L -> "🎂 Morgen!"
                    else -> "🎂 in ${bdayDays}T"
                }
                val ageInfo = ContactResolver.formatUpcomingAge(person.birthdayIso, today)
                entries += OverviewTimelineItem(
                    uniqueKey = "bday_${person.id}",
                    person = person,
                    daysUntil = bdayDays,
                    typePriority = 0,
                    pillText = pill,
                    isUrgent = bdayDays == 0L,
                    isSoon = bdayDays in 1L..14L,
                    isRecurring = false,
                    primaryLine = "🎂 ${formatCompactBirthdayLine(person.birthdayIso, today).orEmpty()}",
                    secondaryLine = if (relationSummary.isNotBlank() && person.hasNoOwnPhone) "🔗 $relationSummary" else "",
                    popupReason = "🎂 Geburtstag ($ageInfo)"
                )
            }
        }

        // 2. Erinnerungen & wiederkehrende Termine (WEEKLY:, REPEAT: und Einzeltermine)
        specialEvents.filter { it.personId == person.id }.forEach { ev ->
            when {
                isRepeatDateSpec(ev.dateIso) -> {
                    val rep = parseRepeatDateSpec(ev.dateIso, today)
                    if (rep != null) {
                        val rhythmStr = formatRhythmTextDe(rep.count, rep.unit)
                        var occDate = rep.nextDate
                        for (occIdx in 0 until 6) {
                            val diff = ChronoUnit.DAYS.between(today, occDate)
                            if (occIdx > 0 && diff > 180L) break
                            hasAnyDatedEntry = true
                            val dateStr = formatIsoToDdMmYyyy(occDate.toString())
                            val pill = when (diff) {
                                0L -> "🔔 Heute!"
                                1L -> "🔔 Morgen!"
                                else -> "🔁 in ${diff}T"
                            }
                            entries += OverviewTimelineItem(
                                uniqueKey = "ev_${ev.id}_$occIdx",
                                person = person,
                                daysUntil = diff,
                                typePriority = 1,
                                pillText = pill,
                                isUrgent = diff == 0L,
                                isSoon = diff in 1L..7L,
                                isRecurring = true,
                                primaryLine = "🔁 ${ev.title}  •  $dateStr (alle $rhythmStr)",
                                secondaryLine = ev.note,
                                popupReason = "Erinnerung: ${ev.title}"
                            )
                            occDate = addIntervalToIsoDate(occDate, rep.count, rep.unit)
                        }
                    }
                }
                isWeeklyDateSpec(ev.dateIso) -> {
                    val wd = getWeeklyDayNumber(ev.dateIso) ?: 1
                    val firstDiff = daysUntilWeeklyDay(wd, today)
                    val dayName = WEEKDAY_NAMES_DE[wd] ?: "Wochentag"
                    for (occIdx in 0 until 6) {
                        val diff = firstDiff + occIdx * 7L
                        if (occIdx > 0 && diff > 60L) break
                        hasAnyDatedEntry = true
                        val occDate = today.plusDays(diff)
                        val dateStr = formatIsoToDdMmYyyy(occDate.toString())
                        val pill = when (diff) {
                            0L -> "🔔 Heute!"
                            1L -> "🔔 Morgen!"
                            else -> "🔁 in ${diff}T"
                        }
                        entries += OverviewTimelineItem(
                            uniqueKey = "ev_${ev.id}_$occIdx",
                            person = person,
                            daysUntil = diff,
                            typePriority = 1,
                            pillText = pill,
                            isUrgent = diff == 0L,
                            isSoon = diff in 1L..7L,
                            isRecurring = true,
                            primaryLine = "🔁 ${ev.title}  •  $dayName, $dateStr",
                            secondaryLine = ev.note,
                            popupReason = "Erinnerung: ${ev.title}"
                        )
                    }
                }
                else -> {
                    val parsed = runCatching { LocalDate.parse(ev.dateIso.trim()) }.getOrNull()
                    if (parsed != null) {
                        val diff = ChronoUnit.DAYS.between(today, parsed)
                        if (diff >= 0L) {
                            hasAnyDatedEntry = true
                            val dateStr = formatIsoToDdMmYyyy(parsed.toString())
                            val pill = when (diff) {
                                0L -> "🔔 Heute!"
                                1L -> "🔔 Morgen!"
                                else -> "🔔 in ${diff}T"
                            }
                            entries += OverviewTimelineItem(
                                uniqueKey = "ev_${ev.id}_0",
                                person = person,
                                daysUntil = diff,
                                typePriority = 1,
                                pillText = pill,
                                isUrgent = diff == 0L,
                                isSoon = diff in 1L..14L,
                                isRecurring = false,
                                primaryLine = "🔔 ${ev.title}  •  $dateStr",
                                secondaryLine = ev.note,
                                popupReason = "Erinnerung: ${ev.title}"
                            )
                        }
                    }
                }
            }
        }

        // 3. Wiederkehrender Melde-Timer (zeigt fälligen Termin + nächste Wiederholungen)
        if (person.timerEnabled) {
            val firstTimer = ContactResolver.nextTimerDate(person, today)
            if (firstTimer != null) {
                val rhythmStr = formatRhythmTextDe(person.timerInterval, person.timerUnit)
                var curTimer: LocalDate = firstTimer
                for (occIdx in 0 until 5) {
                    val rawDiff = ChronoUnit.DAYS.between(today, curTimer)
                    if (occIdx > 0 && rawDiff > 90L) break
                    hasAnyDatedEntry = true
                    val sortDays = rawDiff.coerceAtLeast(0L)
                    val dateStr = formatIsoToDdMmYyyy(curTimer.toString())
                    val pill = when {
                        rawDiff < 0L -> "⏳ Überfällig!"
                        rawDiff == 0L -> "⏳ Heute!"
                        rawDiff == 1L -> "⏳ Morgen"
                        else -> "⏳ in ${rawDiff}T"
                    }
                    entries += OverviewTimelineItem(
                        uniqueKey = "timer_${person.id}_$occIdx",
                        person = person,
                        daysUntil = sortDays,
                        typePriority = 2,
                        pillText = pill,
                        isUrgent = rawDiff <= 0L,
                        isSoon = rawDiff in 1L..7L,
                        isRecurring = true,
                        primaryLine = "⏳ Melde-Timer (alle $rhythmStr)  •  $dateStr",
                        popupReason = "Melde-Timer"
                    )
                    val baseForNext = if (curTimer.isBefore(today)) today else curTimer
                    curTimer = addIntervalToIsoDate(baseForNext, person.timerInterval, person.timerUnit)
                }
            }
        }

        // 4. Verknüpfte Kontakte ohne eigenes Datum/Ereignis (am Ende der Liste einreihen)
        if (!hasAnyDatedEntry && relationSummary.isNotBlank()) {
            entries += OverviewTimelineItem(
                uniqueKey = "relonly_${person.id}",
                person = person,
                daysUntil = Long.MAX_VALUE,
                typePriority = 3,
                pillText = "",
                isUrgent = false,
                isSoon = false,
                isRecurring = false,
                primaryLine = "🔗 $relationSummary",
                popupReason = "Nachricht"
            )
        }
    }

    return if (sortMode == "lastname") {
        entries.sortedWith(
            compareBy<OverviewTimelineItem, PersonEntity>(LastNamePersonComparator) { it.person }
                .thenBy { it.daysUntil }
                .thenBy { it.typePriority }
        )
    } else {
        entries.sortedWith(
            compareBy<OverviewTimelineItem> { it.daysUntil }
                .thenBy { it.typePriority }
                .thenBy(LastNamePersonComparator) { it.person }
        )
    }
}

data class WichtelParticipant(
    val id: Long,
    val personId: Long? = null,
    val name: String,
    val phone: String = "",
    val wishText: String = "",
    val noGos: String = "",
    val address: String = "",
    val wishlistUrl: String = "",
    val extraWishUrls: List<String> = emptyList(),
    val drawnTargetId: Long? = null,
    val lastYearTargetId: Long? = null,
    val notifiedVia: String = ""
)

data class WichtelGroup(
    val id: Long,
    val groupName: String,
    val adminName: String,
    val motto: String = "🎁 Klassisches Wichteln",
    val budget: String = "25 €",
    val revealDateIso: String = "${LocalDate.now().year}-12-24",
    val reminderDaysBefore: Int = 7,
    val excludeCouples: Boolean = true,
    val excludeLastYear: Boolean = true,
    val forceRevealed: Boolean = false,
    val isDrawn: Boolean = false,
    val participants: List<WichtelParticipant> = emptyList()
)

fun parseWichtelGroupsJson(raw: String?): List<WichtelGroup> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val pArr = obj.optJSONArray("participants") ?: JSONArray()
            val parts = (0 until pArr.length()).map { j ->
                val pObj = pArr.getJSONObject(j)
                val extraArr = pObj.optJSONArray("extraWishUrls")
                val extras = if (extraArr != null) {
                    (0 until extraArr.length()).mapNotNull { k -> extraArr.optString(k).takeIf { it.isNotBlank() } }
                } else emptyList()
                WichtelParticipant(
                    id = pObj.optLong("id", System.nanoTime()),
                    personId = pObj.optLong("personId", -1L).takeIf { it > 0 },
                    name = pObj.optString("name", ""),
                    phone = pObj.optString("phone", ""),
                    wishText = pObj.optString("wishText", ""),
                    noGos = pObj.optString("noGos", ""),
                    address = pObj.optString("address", ""),
                    wishlistUrl = pObj.optString("wishlistUrl", ""),
                    extraWishUrls = extras,
                    drawnTargetId = pObj.optLong("drawnTargetId", -1L).takeIf { it > 0 },
                    lastYearTargetId = pObj.optLong("lastYearTargetId", -1L).takeIf { it > 0 },
                    notifiedVia = pObj.optString("notifiedVia", "")
                )
            }
            WichtelGroup(
                id = obj.optLong("id", System.nanoTime()),
                groupName = obj.optString("groupName", "Wichtelrunde"),
                adminName = obj.optString("adminName", "Admin"),
                motto = obj.optString("motto", "🎁 Klassisches Wichteln"),
                budget = obj.optString("budget", "25 €"),
                revealDateIso = obj.optString("revealDateIso", "${LocalDate.now().year}-12-24"),
                reminderDaysBefore = obj.optInt("reminderDaysBefore", 7),
                excludeCouples = obj.optBoolean("excludeCouples", true),
                excludeLastYear = obj.optBoolean("excludeLastYear", true),
                forceRevealed = obj.optBoolean("forceRevealed", false),
                isDrawn = obj.optBoolean("isDrawn", false),
                participants = parts
            )
        }
    }.getOrElse { emptyList() }
}

fun serializeWichtelGroupsJson(groups: List<WichtelGroup>): String {
    val arr = JSONArray()
    groups.forEach { g ->
        arr.put(
            JSONObject().apply {
                put("id", g.id)
                put("groupName", g.groupName)
                put("adminName", g.adminName)
                put("motto", g.motto)
                put("budget", g.budget)
                put("revealDateIso", g.revealDateIso)
                put("reminderDaysBefore", g.reminderDaysBefore)
                put("excludeCouples", g.excludeCouples)
                put("excludeLastYear", g.excludeLastYear)
                put("forceRevealed", g.forceRevealed)
                put("isDrawn", g.isDrawn)
                val pArr = JSONArray()
                g.participants.forEach { p ->
                    pArr.put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("personId", p.personId ?: -1L)
                            put("name", p.name)
                            put("phone", p.phone)
                            put("wishText", p.wishText)
                            put("noGos", p.noGos)
                            put("address", p.address)
                            put("wishlistUrl", p.wishlistUrl)
                            put("extraWishUrls", JSONArray(p.extraWishUrls))
                            put("drawnTargetId", p.drawnTargetId ?: -1L)
                            put("lastYearTargetId", p.lastYearTargetId ?: -1L)
                            put("notifiedVia", p.notifiedVia)
                        }
                    )
                }
                put("participants", pArr)
            }
        )
    }
    return arr.toString()
}

data class WichtelHistoryPair(
    val giverName: String,
    val giverPersonId: Long? = null,
    val giverPhone: String = "",
    val receiverName: String,
    val receiverPersonId: Long? = null,
    val wishText: String = "",
    val noGos: String = "",
    val address: String = "",
    val wishlistUrl: String = ""
)

data class WichtelHistoryEntry(
    val id: Long,
    val year: Int,
    val groupName: String,
    val adminName: String,
    val motto: String = "🎁 Klassisches Wichteln",
    val budget: String = "25 €",
    val completedDateIso: String = "",
    val pairings: List<WichtelHistoryPair> = emptyList()
)

fun parseWichtelHistoryJson(raw: String?): List<WichtelHistoryEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val pairArr = obj.optJSONArray("pairings") ?: JSONArray()
            val pairs = (0 until pairArr.length()).map { j ->
                val pObj = pairArr.getJSONObject(j)
                WichtelHistoryPair(
                    giverName = pObj.optString("giverName", ""),
                    giverPersonId = pObj.optLong("giverPersonId", -1L).takeIf { it > 0 },
                    giverPhone = pObj.optString("giverPhone", ""),
                    receiverName = pObj.optString("receiverName", ""),
                    receiverPersonId = pObj.optLong("receiverPersonId", -1L).takeIf { it > 0 },
                    wishText = pObj.optString("wishText", ""),
                    noGos = pObj.optString("noGos", ""),
                    address = pObj.optString("address", ""),
                    wishlistUrl = pObj.optString("wishlistUrl", "")
                )
            }
            WichtelHistoryEntry(
                id = obj.optLong("id", System.nanoTime() + i),
                year = obj.optInt("year", LocalDate.now().year - 1),
                groupName = obj.optString("groupName", "Wichtelrunde"),
                adminName = obj.optString("adminName", "Admin"),
                motto = obj.optString("motto", "🎁 Klassisches Wichteln"),
                budget = obj.optString("budget", "25 €"),
                completedDateIso = obj.optString("completedDateIso", ""),
                pairings = pairs
            )
        }
    }.getOrElse { emptyList() }
}

fun serializeWichtelHistoryJson(history: List<WichtelHistoryEntry>): String {
    val arr = JSONArray()
    history.forEach { h ->
        arr.put(
            JSONObject().apply {
                put("id", h.id)
                put("year", h.year)
                put("groupName", h.groupName)
                put("adminName", h.adminName)
                put("motto", h.motto)
                put("budget", h.budget)
                put("completedDateIso", h.completedDateIso)
                val pArr = JSONArray()
                h.pairings.forEach { p ->
                    pArr.put(
                        JSONObject().apply {
                            put("giverName", p.giverName)
                            put("giverPersonId", p.giverPersonId ?: -1L)
                            put("giverPhone", p.giverPhone)
                            put("receiverName", p.receiverName)
                            put("receiverPersonId", p.receiverPersonId ?: -1L)
                            put("wishText", p.wishText)
                            put("noGos", p.noGos)
                            put("address", p.address)
                            put("wishlistUrl", p.wishlistUrl)
                        }
                    )
                }
                put("pairings", pArr)
            }
        )
    }
    return arr.toString()
}

val DEFAULT_BIRTHDAY_GREETINGS = listOf(
    "Alles Liebe und Gute zum Geburtstag, {Name}! 🎂🎉 Hab einen wunderschönen Tag!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🥳 Ich wünsche dir Gesundheit, Glück und ganz viel Freude!",
    "Happy Birthday, {Name}! 🎈 Lass dich heute ordentlich feiern und reich beschenken!",
    "Liebe/r {Name}, zu deinem Geburtstag wünsche ich dir von Herzen nur das Allerbeste! 🍀🎂",
    "Hoch sollst du leben, {Name}! 🥂 Alles Gute für dein neues Lebensjahr!",
    "Zum Geburtstag viel Glück, {Name}! 🎶🎂 Genieße deinen Ehrentag in vollen Zügen!",
    "Alles Gute zum Geburtstag, {Name}! ☀️ Mögen all deine Wünsche im neuen Lebensjahr in Erfüllung gehen!",
    "Hallo {Name}, ich wünsche dir einen fantastischen Geburtstag voller schöner Momente! 🎉🎁",
    "Herzlichen Glückwunsch, {Name}! 🎂 Bleib so wunderbar, wie du bist, und hab einen tollen Tag!",
    "Ein Prosit auf dein neues Lebensjahr, {Name}! 🥂✨ Alles Liebe zum Geburtstag!",
    "Liebe/r {Name}, ich schicke dir sonnige Geburtstagsgrüße und wünsche dir 365 glückliche Tage! ☀️🍀",
    "Alles, alles Gute zum Geburtstag, {Name}! 🎊 Lass heute mal fünfe gerade sein und feier schön!",
    "Happy Birthday, {Name}! 🎂 Viel Gesundheit, Erfolg und unzählige schöne Augenblicke für dich!",
    "Zu deinem Ehrentag wünsche ich dir beste Gesundheit, viel Lachen und tolle Menschen um dich herum, {Name}! 🥳",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🎁 Möge dein neues Lebensjahr noch schöner werden als das letzte!",
    "Hallo {Name}! 🎈 Heute ist dein Tag – lass dich feiern, verwöhnen und hochleben!",
    "Für dein neues Lebensjahr wünsche ich dir eine Extraportion Glück, Gesundheit und Lebensfreude, {Name}! 🍀🎂",
    "Alles Liebe zum Geburtstag, {Name}! 🌸 Schön, dass es dich gibt!",
    "Herzlichen Glückwunsch, {Name}! 🎉 Ich hoffe, du hast heute einen richtig schönen Tag mit deinen Liebsten!",
    "Happy Birthday, {Name}! 🍰 Iss heute ein großes Stück Kuchen für mich mit!",
    "Liebe/r {Name}, alles erdenklich Gute zum Geburtstag! 🥂 Auf viele weitere glückliche und gesunde Jahre!",
    "Ich wünsche dir zum Geburtstag viel Sonnenschein im Herzen und immer einen Grund zum Lächeln, {Name}! ☀️😊",
    "Alles Gute zum Geburtstag, {Name}! 🎯 Möge dir im neuen Lebensjahr alles gelingen, was du dir vornimmst!",
    "Herzlichen Glückwunsch, {Name}! 🎂 Gesundheit, Glück und Zufriedenheit sollen dich das ganze Jahr begleiten!",
    "Ein neues Lebensjahr, ein neues Kapitel: Alles Liebe und Gute zum Geburtstag, {Name}! 📖✨",
    "Hallo {Name}, von Herzen alles Gute zum Geburtstag! 🥳 Lass die Korken knallen!",
    "Zu deinem Geburtstag wünsche ich dir Zeit für die schönen Dinge im Leben, {Name}! 🎂🌿",
    "Happy Birthday, {Name}! 🎉 Bleib gesund, munter und genauso herzlich wie bisher!",
    "Alles Liebe zum Geburtstag, {Name}! 🎁 Ich wünsche dir einen Tag voller Überraschungen und Freude!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🍀 Möge das Glück heute und an jedem Tag an deiner Seite sein!",
    "Auf dich, {Name}! 🥂 Hab einen grandiosen Geburtstag und ein wundervolles neues Lebensjahr!",
    "Liebe/r {Name}, zu deinem Wiegenfest wünsche ich dir von Herzen alles Gute und beste Gesundheit! 🎂💐",
    "Alles Gute zum Geburtstag, {Name}! 🌟 Strahle heute mit der Sonne um die Wette!",
    "Happy Birthday, {Name}! 🎈 Genieße jede Sekunde deines heutigen Ehrentages!",
    "Herzlichen Glückwunsch, {Name}! 🎊 Ich wünsche dir wunderbare Stunden im Kreis deiner Familie und Freunde!",
    "Hallo {Name}, alles Liebe zum Geburtstag! 🎂 Möge dein neues Lebensjahr voller unvergesslicher Momente sein!",
    "Für dich zum Geburtstag nur die besten Wünsche: Gesundheit, Frohsinn und ganz viel Glück, {Name}! 🍀🥳",
    "Alles Gute zum Geburtstag, {Name}! 🎁 Heute dreht sich alles nur um dich – genieße es!",
    "Liebe/r {Name}, herzlichen Glückwunsch! 🥂 Bleib so lebensfroh und positiv wie du bist!",
    "Happy Birthday, {Name}! 🎂 Ein Jahr älter, ein Jahr weiser und noch genauso sympathisch!",
    "Zu deinem Geburtstag schicke ich dir die herzlichsten Glückwünsche, {Name}! 🎉🌸",
    "Alles Liebe und Gute, {Name}! 🎈 Ich wünsche dir für das kommende Lebensjahr Rückenwind bei all deinen Plänen!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🍰 Lass es dir heute richtig gutgehen!",
    "Hallo {Name}! 🥳 Wieder ein Jahr geschafft – feier deinen Geburtstag heute gebührend!",
    "Ich wünsche dir zu deinem Geburtstag von Herzen Gesundheit, Ruhe, Freude und viele glückliche Tage, {Name}! 🍀🎂",
    "Happy Birthday, {Name}! ✨ Möge das neue Lebensjahr dir jeden Tag ein Lächeln ins Gesicht zaubern!",
    "Alles Gute zum Geburtstag, {Name}! 🎊 Lass dich heute von deinen Liebsten auf Händen tragen!",
    "Liebe/r {Name}, die allerbesten Glückwünsche zu deinem Geburtstag! 🥂🎁 Hab einen traumhaften Tag!",
    "Herzlichen Glückwunsch, {Name}! 🎂 Viel Kraft, Gesundheit und tolle Erlebnisse im neuen Lebensjahr!",
    "Zum Geburtstag wünsche ich dir so viel Glück, wie der Regen Tropfen hat, {Name}! 🍀☀️",
    "Hallo {Name}, alles Liebe zum Geburtstag! 🎈 Mach dir heute einen unvergesslich schönen Tag!",
    "Happy Birthday, {Name}! 🥳 Auf dass deine kleinen und großen Träume im neuen Lebensjahr wahr werden!",
    "Alles Gute zum Geburtstag, {Name}! 🎂 Gesundheit ist das größte Geschenk – davon wünsche ich dir reichlich!",
    "Herzlichen Glückwunsch, {Name}! 🎉 Genieße deinen Tag mit gutem Essen, lieben Menschen und bester Laune!",
    "Liebe/r {Name}, zum Geburtstag wünsche ich dir 12 Monate Gesundheit, 52 Wochen Glück und 365 Tage Freude! 🍀🥂",
    "Ein herzliches Happy Birthday an dich, {Name}! 🎂🎁 Lass dich heute schön feiern!",
    "Alles Liebe zum Geburtstag, {Name}! ☀️ Behalte deinen Humor und deine Lebensfreude!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🎊 Auf ein spannendes, gesundes und glückliches neues Lebensjahr!",
    "Hallo {Name}, ich denke heute an dich und wünsche dir einen wundervollen Geburtstag! 🌸🎂",
    "Happy Birthday, {Name}! 🎈 Möge dein Kaffeebecher immer voll und dein Stresspegel immer niedrig sein!",
    "Alles Gute zum Geburtstag, {Name}! 🥂 Feier heute schön und hab eine grandiose Zeit!",
    "Liebe/r {Name}, zu deinem Geburtstag wünsche ich dir von Herzen Zufriedenheit, Glück und beste Gesundheit! 🍀🎁",
    "Herzlichen Glückwunsch, {Name}! 🥳 Lass heute die Arbeit ruhen und das Leben hochleben!",
    "Zum Geburtstag alles Liebe, {Name}! 🎂 Ich wünsche dir viele kleine Glücksmomente an jedem einzelnen Tag!",
    "Happy Birthday, {Name}! 🎉 Bleib gesund, fröhlich und immer auf der Sonnenseite des Lebens!",
    "Alles Gute zum Geburtstag, {Name}! 🌟 Ich hoffe, dein Tag wird genauso toll wie du!",
    "Hallo {Name}! 🍰 Zu deinem Geburtstag wünsche ich dir alles Glück dieser Erde!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🎈 Möge dir das neue Lebensjahr viele schöne Überraschungen bringen!",
    "Liebe/r {Name}, alles Liebe und Gute zu deinem Ehrentag! 🥂🍀 Lass es dir heute rundum gutgehen!",
    "Happy Birthday, {Name}! 🎂 Wieder ein Jahr mehr voller schöner Erinnerungen – auf viele weitere!",
    "Alles Gute zum Geburtstag, {Name}! 🎁 Ich wünsche dir Gesundheit, Mut und immer gute Laune!",
    "Herzlichen Glückwunsch, {Name}! 🥳 Hab einen sonnigen, entspannten und fröhlichen Geburtstag!",
    "Zu deinem Geburtstag wünsche ich dir von Herzen alles erdenklich Gute, {Name}! 🌸🎊",
    "Hallo {Name}, lass dich heute feiern! 🎉 Alles Liebe, viel Glück und Gesundheit zum Geburtstag!",
    "Happy Birthday, {Name}! 🥂 Möge dein neues Lebensjahr unter einem besonders guten Stern stehen! ⭐",
    "Alles Liebe zum Geburtstag, {Name}! 🎂 Genieße das Kuchenessen, das Auspacken und das Feiern!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🍀 Auf dass du gesund, glücklich und zufrieden bleibst!",
    "Liebe/r {Name}, ich wünsche dir einen zauberhaften Geburtstag und ein tolles neues Lebensjahr! ✨🎈",
    "Alles Gute zum Geburtstag, {Name}! 🥳 Nimm dir heute Zeit für alles, was dir Freude macht!",
    "Happy Birthday, {Name}! 🎁 Ich schicke dir eine große Portion Glück und beste Wünsche für dein neues Lebensjahr!",
    "Herzlichen Glückwunsch, {Name}! 🎂 Möge jeder Tag deines neuen Lebensjahres ein kleines Fest sein!",
    "Hallo {Name}, zum Geburtstag wünsche ich dir feste Gesundheit, gute Nerven und ganz viel Sonnenschein! ☀️🍀",
    "Alles Liebe und Gute zum Geburtstag, {Name}! 🥂 Lass dich heute ordentlich hochleben!",
    "Zu deinem Geburtstag nur das Beste für dich, {Name}: Glück, Liebe, Gesundheit und Erfolg! 🎉🎂",
    "Happy Birthday, {Name}! 🎈 Bleib im Herzen jung und genieße jeden Moment!",
    "Herzlichen Glückwunsch zum Geburtstag, {Name}! 🍰 Ich hoffe, du wirst heute nach Strich und Faden verwöhnt!",
    "Liebe/r {Name}, alles Gute zum Geburtstag! 🍀 Möge das kommende Jahr dir viele schöne Reisen und Erlebnisse schenken!",
    "Alles Liebe zum Geburtstag, {Name}! 🥳 Feier schön im Kreis deiner Lieblingsmenschen!",
    "Happy Birthday, {Name}! 🎊 Ein Hoch auf dich und dein neues Lebensjahr!",
    "Herzlichen Glückwunsch, {Name}! 🎂 Ich wünsche dir Gesundheit, Gelassenheit und jeden Tag ein Lächeln!",
    "Hallo {Name}! 🎁 Zu deinem Ehrentag wünsche ich dir von Herzen alles Liebe und Gute!",
    "Alles Gute zum Geburtstag, {Name}! ☀️ Mögen dir Glück und Gesundheit niemals ausgehen!",
    "Liebe/r {Name}, herzlichen Glückwunsch zum Geburtstag! 🥂 Genieße deinen besonderen Tag in vollen Zügen!",
    "Happy Birthday, {Name}! 🎉 Ich wünsche dir ein wunderbares neues Lebensjahr voller Freude und Harmonie!",
    "Zum Geburtstag die allerherzlichsten Glückwünsche, {Name}! 🎂🍀 Lass es heute krachen!",
    "Alles Liebe zum Geburtstag, {Name}! 🌸 Möge dir das neue Lebensjahr ganz viel Grund zum Feiern geben!",
    "Herzlichen Glückwunsch, {Name}! 🎈 Gesundheit, Glück und viele schöne gemeinsame Stunden wünsche ich dir!",
    "Hallo {Name}, alles Gute zum Geburtstag! 🥳 Bleib gesund, glücklich und genau so, wie du bist!",
    "Happy Birthday, {Name}! 🍰✨ Ich wünsche dir einen unvergesslichen Tag und ein glückliches neues Lebensjahr!",
    "Von Herzen alles Liebe und Gute zum Geburtstag, {Name}! 🎂🥂🍀 Lass dich heute wunderbar feiern!"
)

fun parseBirthdayGreetingsJson(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return DEFAULT_BIRTHDAY_GREETINGS
    return runCatching {
        val arr = JSONArray(raw)
        val list = (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx).trim().takeIf { it.isNotBlank() }
        }
        list.ifEmpty { DEFAULT_BIRTHDAY_GREETINGS }
    }.getOrElse { DEFAULT_BIRTHDAY_GREETINGS }
}

fun serializeBirthdayGreetingsJson(list: List<String>): String {
    val arr = JSONArray()
    list.forEach { arr.put(it) }
    return arr.toString()
}

data class DuplicateSuggestionGroup(
    val key: String,
    val reasonBadge: String,
    val priority: Int,
    val members: List<PersonEntity>
)

data class MergedContactPreview(
    val primaryPerson: PersonEntity,
    val finalName: String,
    val birthdayIso: String?,
    val primaryPhone: String,
    val primaryEmail: String,
    val hasNoOwnPhone: Boolean,
    val timerEnabled: Boolean,
    val timerInterval: Int,
    val timerUnit: String,
    val lastContactedIso: String?,
    val photoBase64: String,
    val notes: String,
    val deduplicatedExtraInfos: List<Pair<String, String>>,
    val removedDuplicatesCount: Int
)

fun parseExtraInfosPref(raw: String?): List<Pair<String, String>> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("\t", limit = 2)
            val label = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "Info" }
            val value = parts.getOrNull(1)?.trim().orEmpty()
            if (value.isBlank()) null else (label to value)
        }
}

fun serializeExtraInfosPref(extraInfos: List<Pair<String, String>>): String {
    return extraInfos
        .filter { it.second.isNotBlank() }
        .joinToString("\n") { (label, value) ->
            "${label.trim().ifBlank { "Info" }.replace("\t", " ")}\t${value.trim().replace("\t", " ")}"
        }
}

fun canonicalPersonNameForMerge(fullName: String): String {
    var clean = stripNameRoleSuffix(fullName).trim()
    clean = clean.replace(
        Regex("\\s*\\((mobil|handy|privat|arbeit|festnetz|büro|buero|home|work|tel|telefon|alt|neu)\\)\\s*$", RegexOption.IGNORE_CASE),
        ""
    ).trim()
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
    while (parts.size > 2 && TRAILING_CONTACT_TAGS.contains(parts.last().lowercase())) {
        parts.removeAt(parts.lastIndex)
    }
    val reassembled = if (clean.contains(",")) clean else parts.joinToString(" ")
    return ContactDeduplicator.canonicalTextKey(formatLastNameFirst(reassembled))
}

fun findDuplicateSuggestionGroups(persons: List<PersonEntity>): List<DuplicateSuggestionGroup> {
    if (persons.size < 2) return emptyList()
    val result = mutableListOf<DuplicateSuggestionGroup>()
    val seenMemberSets = mutableSetOf<Set<Long>>()

    // 1. Gleicher kanonischer Name (z. B. "Hans Müller", "Müller, Hans", "Hans Müller (Mobil)")
    persons
        .groupBy { canonicalPersonNameForMerge(it.name) }
        .forEach { (nameKey, group) ->
            if (nameKey.isNotBlank() && group.size >= 2) {
                val idSet = group.map { it.id }.toSet()
                if (seenMemberSets.add(idSet)) {
                    result.add(
                        DuplicateSuggestionGroup(
                            key = "name:$nameKey",
                            reasonBadge = "👤 Gleicher Name",
                            priority = 1,
                            members = group.sortedWith(LastNamePersonComparator)
                        )
                    )
                }
            }
        }

    // 2. Gleiche E-Mail-Adresse
    persons
        .mapNotNull { p ->
            val ek = ContactDeduplicator.canonicalEmailKey(p.email)
            if (ek.isBlank()) null else (ek to p)
        }
        .groupBy({ it.first }, { it.second })
        .forEach { (emailKey, group) ->
            val distinctGroup = group.distinctBy { it.id }
            if (distinctGroup.size >= 2) {
                val idSet = distinctGroup.map { it.id }.toSet()
                if (seenMemberSets.add(idSet)) {
                    result.add(
                        DuplicateSuggestionGroup(
                            key = "mail:$emailKey",
                            reasonBadge = "✉️ Gleiche E-Mail",
                            priority = 2,
                            members = distinctGroup.sortedWith(LastNamePersonComparator)
                        )
                    )
                }
            }
        }

    // 3. Gleiche Telefonnummer (trotz Leerzeichen / +49 / Bindestrich)
    persons
        .mapNotNull { p ->
            val pk = ContactDeduplicator.canonicalPhoneKey(p.phone)
            if (pk.isBlank()) null else (pk to p)
        }
        .groupBy({ it.first }, { it.second })
        .forEach { (phoneKey, group) ->
            val distinctGroup = group.distinctBy { it.id }
            if (distinctGroup.size >= 2) {
                val idSet = distinctGroup.map { it.id }.toSet()
                if (seenMemberSets.add(idSet)) {
                    result.add(
                        DuplicateSuggestionGroup(
                            key = "phone:$phoneKey",
                            reasonBadge = "📞 Gleiche Nummer",
                            priority = 3,
                            members = distinctGroup.sortedWith(LastNamePersonComparator)
                        )
                    )
                }
            }
        }

    return result.sortedWith(
        compareBy<DuplicateSuggestionGroup> { it.priority }
            .thenBy { normalizeGermanSortKey(extractLastName(it.members.first().name)) }
            .thenBy { normalizeGermanSortKey(extractFirstName(it.members.first().name)) }
    )
}

fun buildMergedContactPreview(
    selectedPersons: List<PersonEntity>,
    primaryId: Long,
    customName: String,
    localExtraInfosByPersonId: Map<Long, List<Pair<String, String>>>,
    deviceDetailsByContactId: Map<String, DeviceContactFullDetails>
): MergedContactPreview? {
    if (selectedPersons.isEmpty()) return null
    val primary = selectedPersons.find { it.id == primaryId } ?: selectedPersons.first()
    val ordered = listOf(primary) + selectedPersons.filter { it.id != primary.id }

    var removedDuplicatesCount = 0

    val seenPhoneKeys = mutableSetOf<String>()
    val mergedPhones = mutableListOf<Pair<String, String>>()

    val seenEmailKeys = mutableSetOf<String>()
    val mergedEmails = mutableListOf<Pair<String, String>>()

    val seenAddressAndInfoKeys = mutableSetOf<String>()
    val mergedOtherExtras = mutableListOf<Pair<String, String>>()

    val perPersonCleanNotes = mutableListOf<String>()

    ordered.forEach { p ->
        val dev = p.importedFromDeviceContactId?.let { deviceDetailsByContactId[it] } ?: DeviceContactFullDetails()
        val localExtras = localExtraInfosByPersonId[p.id].orEmpty()

        // Innerhalb desselben Kontakts zuerst Room + Device + ExtraInfos bündeln, damit ein identisches Feld aus Room & Device nicht doppelt gezählt wird
        val ownPhoneKeys = mutableSetOf<String>()
        val ownPhones = mutableListOf<Pair<String, String>>()
        fun collectOwnPhone(lbl: String, rawVal: String, countInternalDup: Boolean) {
            val clean = ContactDeduplicator.cleanWhitespace(rawVal)
            if (clean.isBlank()) return
            val pk = ContactDeduplicator.canonicalPhoneKey(clean)
            if (pk.isBlank()) return
            if (ownPhoneKeys.add(pk)) {
                ownPhones.add(lbl to clean)
            } else if (countInternalDup) {
                removedDuplicatesCount++
            }
        }
        collectOwnPhone("Telefon", p.phone, false)
        dev.phones.forEach { (lbl, num) -> collectOwnPhone(lbl, num, false) }
        localExtras.filter { ContactDeduplicator.isPhoneLike(it.second) }
            .forEach { (lbl, num) -> collectOwnPhone(lbl, num, true) }

        ownPhones.forEach { (lbl, num) ->
            val pk = ContactDeduplicator.canonicalPhoneKey(num)
            if (seenPhoneKeys.add(pk)) {
                mergedPhones.add(lbl to num)
            } else {
                removedDuplicatesCount++
            }
        }

        // E-Mails
        val ownEmailKeys = mutableSetOf<String>()
        val ownEmails = mutableListOf<Pair<String, String>>()
        fun collectOwnEmail(lbl: String, rawVal: String, countInternalDup: Boolean) {
            val clean = rawVal.trim()
            val ek = ContactDeduplicator.canonicalEmailKey(clean)
            if (ek.isBlank()) return
            if (ownEmailKeys.add(ek)) {
                ownEmails.add(lbl to clean)
            } else if (countInternalDup) {
                removedDuplicatesCount++
            }
        }
        collectOwnEmail("E-Mail", p.email, false)
        dev.emails.forEach { (lbl, mail) -> collectOwnEmail(lbl, mail, false) }
        localExtras.filter { ContactDeduplicator.isEmailLike(it.second) }
            .forEach { (lbl, mail) -> collectOwnEmail(lbl, mail, true) }

        ownEmails.forEach { (lbl, mail) ->
            val ek = ContactDeduplicator.canonicalEmailKey(mail)
            if (seenEmailKeys.add(ek)) {
                mergedEmails.add(lbl to mail)
            } else {
                removedDuplicatesCount++
            }
        }

        // Adressen & sonstige Zusatzinfos
        val ownTextKeys = mutableSetOf<String>()
        val ownInfos = mutableListOf<Pair<String, String>>()
        dev.addresses.forEach { (lbl, addr) ->
            val clean = ContactDeduplicator.cleanWhitespace(addr)
            val tk = ContactDeduplicator.canonicalTextKey(clean)
            if (tk.isNotBlank() && ownTextKeys.add(tk)) {
                ownInfos.add(lbl.ifBlank { "Adresse" } to clean)
            }
        }
        localExtras.filter { !ContactDeduplicator.isPhoneLike(it.second) && !ContactDeduplicator.isEmailLike(it.second) }
            .forEach { (lbl, value) ->
                val clean = ContactDeduplicator.cleanWhitespace(value)
                val tk = ContactDeduplicator.canonicalTextKey(clean)
                if (tk.isNotBlank()) {
                    if (ownTextKeys.add(tk)) {
                        ownInfos.add(lbl.ifBlank { "Info" } to clean)
                    } else {
                        removedDuplicatesCount++
                    }
                }
            }

        ownInfos.forEach { (lbl, value) ->
            val tk = ContactDeduplicator.canonicalTextKey(value)
            if (seenAddressAndInfoKeys.add(tk)) {
                mergedOtherExtras.add(lbl to value)
            } else {
                removedDuplicatesCount++
            }
        }

        // Notizen dieses Kontakts
        val ownNoteMerged = ContactDeduplicator.deduplicateNotes(listOf(p.notes) + dev.notes)
        if (ownNoteMerged.isNotBlank()) {
            perPersonCleanNotes.add(ownNoteMerged)
        }
    }

    // Geburtstage intelligent zusammenführen
    val rawBirthdays = ordered.mapNotNull { it.birthdayIso?.trim()?.takeIf(String::isNotBlank) }
    var mergedBirthdayIso: String? = rawBirthdays.firstOrNull()
    if (mergedBirthdayIso != null && mergedBirthdayIso.startsWith("--")) {
        val monthDay = mergedBirthdayIso.removePrefix("--")
        val withYear = rawBirthdays.firstOrNull { !it.startsWith("--") && it.endsWith("-$monthDay") }
        if (withYear != null) {
            mergedBirthdayIso = withYear
        }
    }
    rawBirthdays.drop(1).forEach { otherBday ->
        val mainMd = mergedBirthdayIso?.removePrefix("--")?.takeLast(5).orEmpty()
        val otherMd = otherBday.removePrefix("--").takeLast(5)
        if (otherBday == mergedBirthdayIso || (mainMd.isNotBlank() && mainMd == otherMd)) {
            removedDuplicatesCount++
        } else {
            val formattedOther = formatIsoToDdMmYyyy(otherBday)
            val tk = ContactDeduplicator.canonicalTextKey(formattedOther)
            if (tk.isNotBlank() && seenAddressAndInfoKeys.add(tk)) {
                mergedOtherExtras.add("Weiteres Datum" to formattedOther)
            }
        }
    }

    // Notizen über alle ausgewählten Kontakte hinweg deduplizieren
    val seenNoteKeys = mutableSetOf<String>()
    seenNoteKeys.addAll(seenAddressAndInfoKeys)
    val finalNoteLines = mutableListOf<String>()
    perPersonCleanNotes.forEach { block ->
        block.lines().forEach { rawLine ->
            val line = ContactDeduplicator.cleanWhitespace(rawLine)
            if (line.isBlank()) return@forEach
            if (ContactDeduplicator.isEmailLike(line) && seenEmailKeys.contains(ContactDeduplicator.canonicalEmailKey(line))) {
                removedDuplicatesCount++
                return@forEach
            }
            if (ContactDeduplicator.isPhoneLike(line) && seenPhoneKeys.contains(ContactDeduplicator.canonicalPhoneKey(line))) {
                removedDuplicatesCount++
                return@forEach
            }
            val tk = ContactDeduplicator.canonicalTextKey(line)
            if (tk.isNotBlank()) {
                if (seenNoteKeys.add(tk)) {
                    finalNoteLines.add(line)
                } else {
                    removedDuplicatesCount++
                }
            }
        }
    }
    val finalNotes = finalNoteLines.joinToString("\n")

    val primaryPhone = mergedPhones.firstOrNull()?.second.orEmpty()
    val primaryEmail = mergedEmails.firstOrNull()?.second.orEmpty()

    val finalExtraInfos = mutableListOf<Pair<String, String>>()
    mergedPhones.drop(1).forEachIndexed { idx, (lbl, num) ->
        val niceLabel = if (lbl.isBlank() || lbl.equals("Telefon", ignoreCase = true) || lbl.equals("2. Telefon", ignoreCase = true)) {
            "${idx + 2}. Telefon"
        } else {
            lbl
        }
        finalExtraInfos.add(niceLabel to num)
    }
    mergedEmails.drop(1).forEachIndexed { idx, (lbl, mail) ->
        val niceLabel = if (lbl.isBlank() || lbl.equals("E-Mail", ignoreCase = true)) {
            "${idx + 2}. E-Mail"
        } else {
            lbl
        }
        finalExtraInfos.add(niceLabel to mail)
    }
    finalExtraInfos.addAll(mergedOtherExtras)

    // Echtes Kontaktfoto bevorzugen, falls vorhanden
    val primaryRealPhoto = primary.importedFromDeviceContactId?.let { deviceDetailsByContactId[it]?.realPhotoBase64 }.orEmpty()
    val anyRealPhoto = ordered.firstNotNullOfOrNull { p ->
        p.importedFromDeviceContactId?.let { deviceDetailsByContactId[it]?.realPhotoBase64 }?.takeIf { it.isNotBlank() }
    }.orEmpty()
    val finalPhoto = when {
        primaryRealPhoto.isNotBlank() -> primaryRealPhoto
        anyRealPhoto.isNotBlank() -> anyRealPhoto
        primary.photoBase64.isNotBlank() -> primary.photoBase64
        else -> ordered.firstNotNullOfOrNull { it.photoBase64.takeIf(String::isNotBlank) }.orEmpty()
    }

    val timerSource = if (primary.timerEnabled) primary else (ordered.firstOrNull { it.timerEnabled } ?: primary)
    val mergedTimerEnabled = ordered.any { it.timerEnabled }
    val latestContactIso = ordered.mapNotNull { it.lastContactedIso?.takeIf(String::isNotBlank) }.maxOrNull()

    return MergedContactPreview(
        primaryPerson = primary,
        finalName = customName.trim().ifBlank { primary.name },
        birthdayIso = mergedBirthdayIso,
        primaryPhone = primaryPhone,
        primaryEmail = primaryEmail,
        hasNoOwnPhone = primaryPhone.isBlank() && ordered.all { it.hasNoOwnPhone },
        timerEnabled = mergedTimerEnabled,
        timerInterval = timerSource.timerInterval,
        timerUnit = timerSource.timerUnit,
        lastContactedIso = latestContactIso,
        photoBase64 = finalPhoto,
        notes = finalNotes,
        deduplicatedExtraInfos = finalExtraInfos,
        removedDuplicatesCount = removedDuplicatesCount
    )
}

class MainActivity : ComponentActivity() {
    private val pendingPopupRequest = mutableStateOf<Triple<Long, String, Long>?>(null)

    private fun consumePopupIntent(incoming: Intent?) {
        val pid = incoming?.getLongExtra("POPUP_PERSON_ID", -1L) ?: -1L
        if (pid > 0L) {
            val reason = incoming?.getStringExtra("POPUP_REASON") ?: "Erinnerung"
            incoming?.removeExtra("POPUP_PERSON_ID")
            incoming?.removeExtra("POPUP_REASON")
            pendingPopupRequest.value = Triple(pid, reason, System.currentTimeMillis())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyReminderWorker.schedule(this)
        if (BirthdayNotificationHelper.isBirthdayPopupEnabled(this)) {
            BirthdayNotificationHelper.ensureNotificationChannel(this)
            BirthdayNotificationHelper.scheduleDailyBirthdayAlarm(this)
        }
        consumePopupIntent(intent)

        setContent {
            val scheme = if (isLightMode) {
                lightColorScheme(
                    background = BgSlate,
                    surface = CardSlate,
                    primary = AccentSteel,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            } else {
                darkColorScheme(
                    background = BgSlate,
                    surface = CardSlate,
                    primary = AccentSteel,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            }
            MaterialTheme(colorScheme = scheme) {
                KontaktChronikApp(
                    popupRequest = pendingPopupRequest.value,
                    onConsumePopupRequest = { pendingPopupRequest.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePopupIntent(intent)
    }
}

@Composable
fun KontaktChronikApp(
    popupRequest: Triple<Long, String, Long>?,
    onConsumePopupRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context).dao() }
    val syncRepo = remember { ContactsSyncRepository(db) }
    val appPrefs = remember { context.getSharedPreferences("remember_me_prefs", android.content.Context.MODE_PRIVATE) }

    val persons by db.observeAllPersons().collectAsState(initial = emptyList())
    val relations by db.observeAllRelations().collectAsState(initial = emptyList())
    val specialEvents by db.observeAllSpecialEvents().collectAsState(initial = emptyList())

    var activeTab by remember { mutableStateOf("Übersicht") }
    var treeEnabled by remember { mutableStateOf(appPrefs.getBoolean("tree_enabled", true)) }
    var wichtelEnabled by remember { mutableStateOf(appPrefs.getBoolean("wichtel_enabled", true)) }
    var birthdayPopupEnabled by remember { mutableStateOf(BirthdayNotificationHelper.isBirthdayPopupEnabled(context)) }
    var birthdayPopupHour by remember { mutableStateOf(BirthdayNotificationHelper.getNotificationTime(context).first) }
    var birthdayPopupMinute by remember { mutableStateOf(BirthdayNotificationHelper.getNotificationTime(context).second) }
    var hasNotificationPerm by remember { mutableStateOf(BirthdayNotificationHelper.hasNotificationPermission(context)) }
    var wichtelGroups by remember { mutableStateOf(parseWichtelGroupsJson(appPrefs.getString("wichtel_groups_json", null))) }
    var wichtelHistory by remember { mutableStateOf(parseWichtelHistoryJson(appPrefs.getString("wichtel_history_json", null))) }
    var birthdayGreetings by remember { mutableStateOf(parseBirthdayGreetingsJson(appPrefs.getString("birthday_greetings_json", null))) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showGreetingsEditorDialog by remember { mutableStateOf(false) }

    fun saveBirthdayGreetings(updated: List<String>) {
        val finalList = updated.ifEmpty { DEFAULT_BIRTHDAY_GREETINGS }
        birthdayGreetings = finalList
        appPrefs.edit().putString("birthday_greetings_json", serializeBirthdayGreetingsJson(finalList)).apply()
    }

    fun pickRandomBirthdayGreeting(firstName: String, isPastMode: Boolean = false): String {
        val cleanFirst = firstName.trim().ifBlank { "dir" }
        if (isPastMode) {
            return "Alles Gute nachträglich zum Geburtstag, $cleanFirst! 🎂🎉 Ich hoffe, du hattest einen wunderschönen Tag!"
        }
        val pool = birthdayGreetings.ifEmpty { DEFAULT_BIRTHDAY_GREETINGS }
        val template = pool.random()
        return template.replace(Regex("\\{Name\\}", RegexOption.IGNORE_CASE), cleanFirst)
    }

    fun saveWichtelGroups(updated: List<WichtelGroup>) {
        wichtelGroups = updated
        appPrefs.edit().putString("wichtel_groups_json", serializeWichtelGroupsJson(updated)).apply()
    }

    fun saveWichtelHistory(updated: List<WichtelHistoryEntry>) {
        wichtelHistory = updated
        appPrefs.edit().putString("wichtel_history_json", serializeWichtelHistoryJson(updated)).apply()
    }
    var showPastDates by remember { mutableStateOf(false) }
    var overviewSortMode by remember { mutableStateOf("upcoming") } // "upcoming" (Nächstes Ereignis zuerst) | "lastname"
    var contactSortMode by remember { mutableStateOf("lastname") } // "lastname" | "birthday"
    var allContactsFilter by remember { mutableStateOf("all") } // "all" | "hidden"
    var tabSearchQuery by remember { mutableStateOf("") }
    var tabSelectedLetter by remember { mutableStateOf("Alle") }
    var hiddenPersonIds by remember {
        val rawSet = appPrefs.getStringSet("hidden_person_ids", emptySet()).orEmpty()
        mutableStateOf(rawSet.mapNotNull { it.toLongOrNull() }.toSet())
    }
    val visiblePersons = remember(persons, hiddenPersonIds) {
        persons.filter { !it.isHidden && !hiddenPersonIds.contains(it.id) }
    }
    var selectedTreeRootId by remember { mutableStateOf<Long?>(null) }
    var popupPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var initialPhotoSyncDone by remember { mutableStateOf(false) }
    var popupReason by remember { mutableStateOf("Erinnerung") }
    var quickMessageText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showTimerEntryDialog by remember { mutableStateOf(false) }
    var editingTimerPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var detailPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var showMergeContactsDialog by remember { mutableStateOf(false) }
    var mergeInitialPersonIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var mergedAwayDeviceCids by remember {
        mutableStateOf(appPrefs.getStringSet("merged_away_device_cids", emptySet()).orEmpty())
    }
    val duplicateSuggestionGroups = remember(visiblePersons) {
        findDuplicateSuggestionGroups(visiblePersons)
    }
    var syncStatus by remember { mutableStateOf("") }
    var pendingConfirmMessage by remember { mutableStateOf<String?>(null) }
    var pendingConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted || BirthdayNotificationHelper.hasNotificationPermission(context)
        if (hasNotificationPerm && birthdayPopupEnabled) {
            BirthdayNotificationHelper.ensureNotificationChannel(context)
            BirthdayNotificationHelper.scheduleDailyBirthdayAlarm(context)
            BirthdayNotificationHelper.checkAndNotifyTodaysBirthdaysAsync(context)
            syncStatus = "Geburtstags-Popups aktiviert"
        } else if (!hasNotificationPerm) {
            syncStatus = "Benachrichtigungs-Berechtigung noch nicht erteilt"
        }
    }

    fun openAppNotificationSettings() {
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(settingsIntent) }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(fallback) }
        }
    }

    suspend fun syncContactsRespectingMerged(forcePhotoRefresh: Boolean = false): Int {
        val currentIgnored = appPrefs.getStringSet("merged_away_device_cids", emptySet()).orEmpty()
        val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            syncRepo.syncDeviceContactsWithBirthdays(
                contentResolver = context.contentResolver,
                forcePhotoRefresh = forcePhotoRefresh,
                ignoredDeviceContactIds = currentIgnored
            )
        }
        if (birthdayPopupEnabled && BirthdayNotificationHelper.hasNotificationPermission(context)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                BirthdayNotificationHelper.checkAndNotifyTodaysBirthdays(context)
            }
        }
        return count
    }

    fun setPersonHidden(personId: Long, hidden: Boolean) {
        if (hidden && detailPerson?.id == personId) {
            detailPerson = null
        }
        val next = if (hidden) hiddenPersonIds + personId else hiddenPersonIds - personId
        hiddenPersonIds = next
        appPrefs.edit().putStringSet("hidden_person_ids", next.map { it.toString() }.toSet()).apply()
        val p = persons.find { it.id == personId }
        if (p != null && p.isHidden != hidden) {
            scope.launch { db.updatePerson(p.copy(isHidden = hidden)) }
        }
    }

    fun applyThemeMode(mode: String) {
        appThemeMode = mode
        appPrefs.edit().putString("theme_mode", mode).apply()
        RememberMeWidgetUpdater.updateAllAsync(context)
    }

    LaunchedEffect(persons, specialEvents, hiddenPersonIds, appThemeMode) {
        RememberMeWidgetUpdater.updateAllAsync(context)
    }

    LaunchedEffect(Unit) {
        val savedTheme = appPrefs.getString("theme_mode", null)
        appThemeMode = if (savedTheme != null) {
            savedTheme
        } else {
            "warm"
        }
        hasNotificationPerm = BirthdayNotificationHelper.hasNotificationPermission(context)
        if (birthdayPopupEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                BirthdayNotificationHelper.scheduleDailyBirthdayAlarm(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BirthdayNotificationHelper.checkAndNotifyTodaysBirthdays(context)
                }
            }
        }
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasRead) {
            initialPhotoSyncDone = true
            syncContactsRespectingMerged(forcePhotoRefresh = false)
        }
    }

    LaunchedEffect(persons, popupRequest) {
        // Entferne automatisch evtl. vorhandene Dummy-/Beispieldaten aus früheren Versionen
        val dummyNames = setOf(
            "helga weber (oma)",
            "anna weber",
            "thomas weber",
            "markus weber",
            "leo weber (baby)",
            "sabine wagner",
            "max berger",
            "praxis dr. klein"
        )
        val dummyPhones = setOf(
            "+491701112233",
            "+491712345678",
            "+491719988776",
            "+491605544332",
            "+491609876543",
            "+4915122334455",
            "+4930123456"
        )
        val dummyPersons = persons.filter { p ->
            p.importedFromDeviceContactId.isNullOrBlank() &&
                (dummyNames.contains(p.name.trim().lowercase()) || dummyPhones.contains(p.phone.trim()))
        }
        if (dummyPersons.isNotEmpty()) {
            val dummyIds = dummyPersons.map { it.id }.toSet()
            relations.filter { dummyIds.contains(it.fromPersonId) || dummyIds.contains(it.toPersonId) }
                .forEach { db.deleteRelation(it) }
            specialEvents.filter { dummyIds.contains(it.personId) }
                .forEach { db.deleteSpecialEvent(it) }
            dummyPersons.forEach { db.deletePerson(it) }
        }
        if (wichtelGroups.any { it.id == 1001L }) {
            saveWichtelGroups(wichtelGroups.filterNot { it.id == 1001L })
        }
        if (wichtelHistory.any { it.id == 3001L }) {
            saveWichtelHistory(wichtelHistory.filterNot { it.id == 3001L })
        }

        if (popupRequest != null && persons.isNotEmpty()) {
            val (targetId, reqReason, _) = popupRequest
            onConsumePopupRequest()
            val found = persons.find { it.id == targetId }
            if (found != null && !found.isHidden && !hiddenPersonIds.contains(found.id)) {
                popupReason = reqReason
                val firstName = stripNameRoleSuffix(found.name).substringBefore(" ")
                if (reqReason.contains("Geburtstag", ignoreCase = true) || quickMessageText.isBlank()) {
                    quickMessageText = pickRandomBirthdayGreeting(firstName, false)
                }
                popupPerson = found
            }
        }
        if (selectedTreeRootId == null && visiblePersons.isNotEmpty()) {
            selectedTreeRootId = visiblePersons.first().id
        }
        val remainingPersons = persons.filterNot { dummyPersons.contains(it) }
        if (!initialPhotoSyncDone && remainingPersons.isNotEmpty() && remainingPersons.any { it.photoBase64.isBlank() }) {
            initialPhotoSyncDone = true
            val hasRead = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasRead) {
                syncContactsRespectingMerged()
            } else {
                syncRepo.ensureDefaultPhotosForAllPersons()
            }
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.READ_CONTACTS] == true) {
            scope.launch {
                val count = syncContactsRespectingMerged()
                syncStatus = "$count Kontakte synchronisiert"
            }
        } else {
            syncStatus = "Kontakt-Berechtigung verweigert"
        }
    }

    suspend fun addBidirectionalRelation(fromId: Long, toId: Long, label: String) {
        if (fromId <= 0 || toId <= 0 || fromId == toId) return
        val cleanLabel = label.trim()
        val existsForward = relations.any {
            it.fromPersonId == fromId && it.toPersonId == toId && it.relationLabel.equals(cleanLabel, ignoreCase = true)
        }
        if (!existsForward) {
            db.insertRelation(
                RelationEntity(
                    fromPersonId = fromId,
                    toPersonId = toId,
                    relationLabel = cleanLabel,
                    isFallbackContact = true
                )
            )
        }
        val revLabel = inverseRelationLabel(cleanLabel)
        val existsReverse = relations.any {
            it.fromPersonId == toId && it.toPersonId == fromId
        }
        if (!existsReverse) {
            db.insertRelation(
                RelationEntity(
                    fromPersonId = toId,
                    toPersonId = fromId,
                    relationLabel = revLabel,
                    isFallbackContact = cleanLabel.lowercase().startsWith("mutter") ||
                        cleanLabel.lowercase().startsWith("vater") ||
                        cleanLabel.lowercase().startsWith("elternteil") ||
                        isPartnerRelation(cleanLabel)
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSlate)
            .padding(12.dp)
    ) {
        // Kompakte Kopfzeile mit Schnell-Design-Umschalter & Optionen oben rechts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Remember Me v1.0.36", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactButton(getThemeIcon(appThemeMode)) {
                    applyThemeMode(nextThemeMode(appThemeMode))
                }
                CompactButton("Sync") {
                    val hasRead = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasWrite = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasRead && hasWrite) {
                        scope.launch {
                            val count = syncContactsRespectingMerged(forcePhotoRefresh = true)
                            syncStatus = "$count neue Kontakte synchronisiert"
                        }
                    } else {
                        contactPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS
                            )
                        )
                    }
                }
                CompactButton("Neu") { showCreateDialog = true }
                CompactButton("⚙") { showOptionsDialog = true }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Kompakte Icon-Navigation (Übersicht, Kontakte, Stammbaum, Timer, Erinnerung, Wichteln)
        val navTabItems = buildList {
            add(Triple("Übersicht", "🎂", "Übersicht"))
            add(Triple("Alle Kontakte", "👥", "Kontakte"))
            if (treeEnabled) add(Triple("Stammbaum", "🌳", "Stammbaum"))
            add(Triple("Timer", "⏳", "Timer"))
            add(Triple("Erinnerung", "🔔", "Erinnerung"))
            if (wichtelEnabled) add(Triple("🎄 Wichteln", "🎄", "Wichteln"))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            navTabItems.forEach { (tabId, iconStr, shortLabel) ->
                val active = activeTab == tabId
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (active) BtnBg else CardSlate, RoundedCornerShape(10.dp))
                        .border(if (active) 1.5.dp else 1.dp, if (active) AccentSteel else BorderSlate, RoundedCornerShape(10.dp))
                        .clickable { activeTab = tabId }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    Text(
                        iconStr,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        style = CenteredPillTextStyle
                    )
                    Text(
                        shortLabel,
                        color = if (active) TextPrimary else TextMuted,
                        fontSize = 9.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        style = CenteredPillTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (persons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Noch keine Kontakte vorhanden", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tippe auf '📲 Handykontakte synchronisieren' oder oben auf 'Sync', um deine Telefonkontakte samt Geburtstagen und Kontaktfotos zu übernehmen, oder auf '+ Kontakt anlegen'.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactButton("📲 Handykontakte synchronisieren") {
                            val hasRead = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasWrite = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasRead && hasWrite) {
                                scope.launch {
                                    val count = syncContactsRespectingMerged()
                                    syncStatus = "$count Kontakte synchronisiert"
                                }
                            } else {
                                contactPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                    )
                                )
                            }
                        }
                        CompactButton("+ Kontakt anlegen") {
                            showCreateDialog = true
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (activeTab) {
            "Alle Kontakte" -> {
                val hiddenList = remember(persons, hiddenPersonIds) {
                    persons.filter { it.isHidden || hiddenPersonIds.contains(it.id) }
                }
                val hiddenCount = hiddenList.size
                val baseList = remember(hiddenList, visiblePersons, allContactsFilter) {
                    if (allContactsFilter == "hidden") hiddenList else visiblePersons
                }
                val filteredBase = remember(baseList, tabSearchQuery, tabSelectedLetter) {
                    baseList.filter { matchesPersonSearchAndLetter(it, tabSearchQuery, tabSelectedLetter) }
                }
                val isFilteringAllContacts = tabSearchQuery.isNotBlank() || (tabSelectedLetter.isNotBlank() && tabSelectedLetter != "Alle")
                val matchingHiddenInContactsCount = remember(hiddenList, allContactsFilter, tabSearchQuery, tabSelectedLetter, isFilteringAllContacts) {
                    if (allContactsFilter == "hidden" || !isFilteringAllContacts) 0
                    else hiddenList.count { matchesPersonSearchAndLetter(it, tabSearchQuery, tabSelectedLetter) }
                }
                val sortedAll = remember(filteredBase, contactSortMode) {
                    when (contactSortMode) {
                        "birthday" -> filteredBase.sortedWith(
                            compareBy<PersonEntity> {
                                ContactResolver.daysUntilNextBirthday(it.birthdayIso) ?: Long.MAX_VALUE
                            }.then(LastNamePersonComparator)
                        )
                        else -> filteredBase.sortedWith(LastNamePersonComparator)
                    }
                }
                val sortBtnLabel = if (contactSortMode == "birthday") "⇅ Geburtstag" else "⇅ Nachname"

                Column(modifier = Modifier.fillMaxSize()) {
                    ContactSearchDropdown(
                        persons = baseList,
                        onSelectPerson = { detailPerson = it },
                        placeholderText = "🔎 Kontakt suchen / Dropdown-Liste",
                        confirmButtonLabel = "✓ Bestätigen",
                        externalSearchQuery = tabSearchQuery,
                        onExternalSearchQueryChange = { tabSearchQuery = it },
                        externalSelectedLetter = tabSelectedLetter,
                        onExternalSelectedLetterChange = { tabSelectedLetter = it },
                        showInlineAlphabet = true,
                        hiddenPersons = if (allContactsFilter == "all") hiddenList else emptyList(),
                        onShowHidden = if (allContactsFilter == "all") ({ allContactsFilter = "hidden" }) else null
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(sortBtnLabel) {
                            contactSortMode = if (contactSortMode == "lastname") "birthday" else "lastname"
                        }
                        CompactButton(
                            if (duplicateSuggestionGroups.isNotEmpty()) "🔗 Zusammenführen (${duplicateSuggestionGroups.size})"
                            else "🔗 Zusammenführen"
                        ) {
                            mergeInitialPersonIds = emptyList()
                            showMergeContactsDialog = true
                        }
                        CompactButton(if (allContactsFilter == "all") "✓ Alle (${visiblePersons.size})" else "Alle (${visiblePersons.size})") {
                            allContactsFilter = "all"
                        }
                        CompactButton(
                            if (allContactsFilter == "hidden") "✓ Ausgeblendet ($hiddenCount)"
                            else "Ausgeblendet ($hiddenCount)"
                        ) {
                            allContactsFilter = "hidden"
                        }
                    }

                    if (matchingHiddenInContactsCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(CardSlate, RoundedCornerShape(8.dp))
                                .border(1.dp, WarnAmber, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👁 $matchingHiddenInContactsCount ausgeblendet für diesen Filter",
                                color = WarnAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            CompactButton("Ausgeblendete zeigen") {
                                allContactsFilter = "hidden"
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sortedAll, key = { it.id }) { person ->
                            val isHidden = person.isHidden || hiddenPersonIds.contains(person.id)
                            val bdayLine = formatCompactBirthdayLine(person.birthdayIso)
                            val subText = bdayLine ?: person.phone.takeIf { it.isNotBlank() }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (isHidden) WarnAmber else BorderSlate, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { detailPerson = person }
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PersonAvatar(name = person.name, photoBase64 = person.photoBase64)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatLastNameFirst(person.name),
                                                color = if (isHidden) TextMuted else TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            BirthdayCountdownPill(birthdayIso = person.birthdayIso, isPastMode = false)
                                            if (isHidden) {
                                                Text(
                                                    text = "Ausgeblendet",
                                                    color = WarnAmber,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        if (!subText.isNullOrBlank()) {
                                            Text(
                                                text = subText,
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                CompactButton(if (isHidden) "Einblenden" else "Ausblenden") {
                                    setPersonHidden(person.id, !isHidden)
                                }
                            }
                        }
                    }
                }
            }

            "Stammbaum" -> {
                FamilyTreeScreen(
                    persons = visiblePersons,
                    relations = relations,
                    selectedRootId = selectedTreeRootId?.takeIf { id -> visiblePersons.any { it.id == id } } ?: visiblePersons.sortedByLastName().firstOrNull()?.id,
                    onSelectRoot = { selectedTreeRootId = it },
                    onOpenDetails = { detailPerson = it },
                    onOpenMessage = { p ->
                        val relPairs = relations
                            .filter { it.fromPersonId == p.id }
                            .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                        val resolved = ContactResolver.resolveTarget(p, relPairs)
                        popupReason = "Nachricht"
                        quickMessageText = pickRandomBirthdayGreeting(extractFirstName(resolved.targetPersonName), false)
                        popupPerson = p
                    }
                )
            }

            "Timer" -> {
                val allTimerPersons = remember(visiblePersons) {
                    visiblePersons.filter { it.timerEnabled }.sortedWith(LastNamePersonComparator)
                }
                val timerPersons = remember(allTimerPersons, tabSearchQuery, tabSelectedLetter) {
                    allTimerPersons.filter { matchesPersonSearchAndLetter(it, tabSearchQuery, tabSelectedLetter) }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏳ Melde-Timer (${allTimerPersons.size})",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        CompactButton("＋ Timer-Eintrag") {
                            editingTimerPerson = null
                            showTimerEntryDialog = true
                        }
                    }
                    ContactSearchDropdown(
                        persons = allTimerPersons,
                        onSelectPerson = { p ->
                            editingTimerPerson = p
                            showTimerEntryDialog = true
                        },
                        placeholderText = "🔎 Timer-Kontakt per Dropdown / ABCD suchen",
                        confirmButtonLabel = "✓ Bestätigen",
                        externalSearchQuery = tabSearchQuery,
                        onExternalSearchQueryChange = { tabSearchQuery = it },
                        externalSelectedLetter = tabSelectedLetter,
                        onExternalSelectedLetterChange = { tabSelectedLetter = it },
                        showInlineAlphabet = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (timerPersons.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardSlate, RoundedCornerShape(10.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (allTimerPersons.isEmpty()) {
                                            "Noch keine aktiven Melde-Timer vorhanden. Tippe auf „＋ Timer-Eintrag“, um manuell einen Timer für einen Kontakt zu erstellen."
                                        } else {
                                            "Keine Timer-Einträge für diesen ABCD-/Suchfilter gefunden."
                                        },
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    CompactButton("＋ Manuell Timer-Eintrag erstellen") {
                                        editingTimerPerson = null
                                        showTimerEntryDialog = true
                                    }
                                }
                            }
                        }
                        items(timerPersons, key = { it.id }) { p ->
                            val nextTimer = ContactResolver.nextTimerDate(p)
                            val diff = nextTimer?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) } ?: 0L
                            val nextTimerStr = formatIsoToDdMmYyyy(nextTimer?.toString())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(name = p.name, photoBase64 = p.photoBase64)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            editingTimerPerson = p
                                            showTimerEntryDialog = true
                                        }
                                ) {
                                    Text(formatLastNameFirst(p.name), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    val unitDe = when (p.timerUnit.uppercase()) {
                                        "DAYS" -> "Tage"
                                        "WEEKS" -> "Wochen"
                                        "MONTHS" -> "Monate"
                                        "YEARS" -> "Jahre"
                                        else -> p.timerUnit
                                    }
                                    Text(
                                        "Alle ${p.timerInterval} $unitDe • Nächste: $nextTimerStr",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        if (diff <= 0L) "Jetzt fällig ($nextTimerStr)" else "Nächste Erinnerung in ${diff}T ($nextTimerStr)",
                                        color = if (diff <= 0L) WarnAmber else AccentSteel,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CompactButton("✏️") {
                                        editingTimerPerson = p
                                        showTimerEntryDialog = true
                                    }
                                    CompactButton("Nachricht") {
                                        val relPairs = relations
                                            .filter { it.fromPersonId == p.id }
                                            .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                        val resolved = ContactResolver.resolveTarget(p, relPairs)
                                        popupReason = "Melde-Timer"
                                        quickMessageText = pickRandomBirthdayGreeting(extractFirstName(resolved.targetPersonName), false)
                                        popupPerson = p
                                    }
                                    Text(
                                        "✕",
                                        color = WarnAmber,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                pendingConfirmMessage = "Melde-Timer für „${formatLastNameFirst(p.name)}“ deaktivieren?"
                                                pendingConfirmAction = {
                                                    scope.launch {
                                                        db.updatePerson(p.copy(timerEnabled = false))
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "Erinnerung" -> {
                val filteredRemPersons = remember(visiblePersons, tabSearchQuery, tabSelectedLetter) {
                    visiblePersons
                        .filter { matchesPersonSearchAndLetter(it, tabSearchQuery, tabSelectedLetter) }
                        .sortedWith(LastNamePersonComparator)
                }
                val visibleEvents = remember(specialEvents, filteredRemPersons) {
                    val visIds = filteredRemPersons.map { it.id }.toSet()
                    specialEvents.filter { visIds.contains(it.personId) }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    ContactSearchDropdown(
                        persons = visiblePersons,
                        onSelectPerson = { detailPerson = it },
                        placeholderText = "🔎 Erinnerung / Kontakt per Dropdown suchen",
                        confirmButtonLabel = "✓ Bestätigen",
                        externalSearchQuery = tabSearchQuery,
                        onExternalSearchQueryChange = { tabSearchQuery = it },
                        externalSelectedLetter = tabSelectedLetter,
                        onExternalSelectedLetterChange = { tabSelectedLetter = it },
                        showInlineAlphabet = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (visibleEvents.isEmpty() && filteredRemPersons.none { !it.birthdayIso.isNullOrBlank() }) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardSlate, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        "Keine passenden Erinnerungen gefunden. Tippe in der Übersicht auf einen Namen, um eine Erinnerung hinzuzufügen.",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        items(visibleEvents, key = { "ev_${it.id}" }) { ev ->
                            val p = visiblePersons.find { it.id == ev.personId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(name = p?.name ?: "?", photoBase64 = p?.photoBase64.orEmpty())
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { if (p != null) detailPerson = p }
                                ) {
                                    Text(
                                        "${ev.title} – ${if (p != null) formatLastNameFirst(p.name) else "Kontakt"}",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("Datum: ${formatIsoToDdMmYyyy(ev.dateIso)}", color = AccentSteel, fontSize = 12.sp)
                                    if (ev.note.isNotBlank()) {
                                        Text(ev.note, color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                if (p != null) {
                                    CompactButton("Nachricht") {
                                        val relPairs = relations
                                            .filter { it.fromPersonId == p.id }
                                            .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                        val resolved = ContactResolver.resolveTarget(p, relPairs)
                                        popupReason = "Erinnerung: ${ev.title}"
                                        quickMessageText = pickRandomBirthdayGreeting(extractFirstName(resolved.targetPersonName), false)
                                        popupPerson = p
                                    }
                                }
                            }
                        }
                        items(filteredRemPersons.filter { !it.birthdayIso.isNullOrBlank() }, key = { "bday_${it.id}" }) { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(name = p.name, photoBase64 = p.photoBase64)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { detailPerson = p }
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "🎂 ${formatLastNameFirst(p.name)}",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        BirthdayCountdownPill(birthdayIso = p.birthdayIso, isPastMode = false)
                                    }
                                    Text(
                                        formatCompactBirthdayLine(p.birthdayIso).orEmpty(),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                CompactButton("Nachricht") {
                                    val relPairs = relations
                                        .filter { it.fromPersonId == p.id }
                                        .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                    val resolved = ContactResolver.resolveTarget(p, relPairs)
                                    popupReason = "Geburtstag"
                                    quickMessageText = pickRandomBirthdayGreeting(extractFirstName(resolved.targetPersonName), false)
                                    popupPerson = p
                                }
                            }
                        }
                    }
                }
            }

            "🎄 Wichteln" -> {
                WichtelScreen(
                    groups = wichtelGroups,
                    history = wichtelHistory,
                    persons = persons,
                    relations = relations,
                    onSaveGroups = { saveWichtelGroups(it) },
                    onSaveHistory = { saveWichtelHistory(it) },
                    onConfirmDelete = { msg, act ->
                        pendingConfirmMessage = msg
                        pendingConfirmAction = act
                    }
                )
            }

            else -> {
                val linkedPersonIds = remember(relations) {
                    buildSet {
                        relations.forEach { rel ->
                            add(rel.fromPersonId)
                            add(rel.toPersonId)
                        }
                    }
                }
                val eventPersonIds = remember(specialEvents) {
                    specialEvents.map { it.personId }.toSet()
                }
                val overviewVisiblePersons = remember(visiblePersons, linkedPersonIds, eventPersonIds) {
                    visiblePersons.filter { p ->
                        !p.birthdayIso.isNullOrBlank() ||
                            p.timerEnabled ||
                            eventPersonIds.contains(p.id) ||
                            linkedPersonIds.contains(p.id)
                    }
                }
                val hiddenOverviewList = remember(persons, hiddenPersonIds, linkedPersonIds, eventPersonIds) {
                    persons.filter { p ->
                        (p.isHidden || hiddenPersonIds.contains(p.id)) &&
                            (!p.birthdayIso.isNullOrBlank() ||
                                p.timerEnabled ||
                                eventPersonIds.contains(p.id) ||
                                linkedPersonIds.contains(p.id))
                    }
                }
                val filteredOverview = remember(overviewVisiblePersons, tabSearchQuery, tabSelectedLetter) {
                    overviewVisiblePersons.filter { matchesPersonSearchAndLetter(it, tabSearchQuery, tabSelectedLetter) }
                }
                val isFilteringByLetterOrQuery = tabSearchQuery.isNotBlank() || (tabSelectedLetter.isNotBlank() && tabSelectedLetter != "Alle")
                val matchingHiddenOverviewCount = remember(hiddenOverviewList, tabSearchQuery, tabSelectedLetter, isFilteringByLetterOrQuery) {
                    if (!isFilteringByLetterOrQuery) 0
                    else hiddenOverviewList.count { matchesPersonSearchAndLetter(it, tabSearchQuery, tabSelectedLetter) }
                }
                val timelineItems = remember(filteredOverview, persons, specialEvents, relations, showPastDates, overviewSortMode) {
                    buildOverviewTimelineItems(
                        filteredPersons = filteredOverview,
                        allPersons = persons,
                        specialEvents = specialEvents,
                        relations = relations,
                        showPastDates = showPastDates,
                        sortMode = overviewSortMode
                    )
                }
                val nextUpcomingDays = remember(timelineItems, showPastDates) {
                    if (showPastDates) null
                    else timelineItems.firstOrNull { it.daysUntil != Long.MAX_VALUE }?.daysUntil
                }

                val sortBtnLabel = if (overviewSortMode == "upcoming") "📅 Nächstes Ereignis" else "⇅ Nachname"

                Column(modifier = Modifier.fillMaxSize()) {
                    ContactSearchDropdown(
                        persons = overviewVisiblePersons,
                        onSelectPerson = { detailPerson = it },
                        placeholderText = "🔎 Kontakt suchen / Dropdown-Liste",
                        confirmButtonLabel = "✓ Bestätigen",
                        externalSearchQuery = tabSearchQuery,
                        onExternalSearchQueryChange = { tabSearchQuery = it },
                        externalSelectedLetter = tabSelectedLetter,
                        onExternalSelectedLetterChange = { tabSelectedLetter = it },
                        showInlineAlphabet = true,
                        hiddenPersons = hiddenOverviewList,
                        onShowHidden = {
                            allContactsFilter = "hidden"
                            activeTab = "Alle Kontakte"
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(sortBtnLabel) {
                            overviewSortMode = if (overviewSortMode == "upcoming") "lastname" else "upcoming"
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (showPastDates) AccentSteel else BtnBg,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, if (showPastDates) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                .clickable { showPastDates = !showPastDates }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showPastDates) "Anstehende zeigen" else "Vergangene Termine",
                                color = if (showPastDates) BgSlate else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                style = CenteredPillTextStyle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (matchingHiddenOverviewCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(CardSlate, RoundedCornerShape(8.dp))
                                .border(1.dp, WarnAmber, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👁 $matchingHiddenOverviewCount ausgeblendet für diesen Filter",
                                color = WarnAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            CompactButton("Ausgeblendete zeigen") {
                                allContactsFilter = "hidden"
                                activeTab = "Alle Kontakte"
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (timelineItems.isEmpty()) {
                            item(key = "overview_empty") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardSlate, RoundedCornerShape(10.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (overviewVisiblePersons.isEmpty()) {
                                            "Noch keine Kontakte mit Ereignis, Datum oder Verknüpfung vorhanden."
                                        } else {
                                            "Keine anstehenden Ereignisse oder verknüpften Kontakte für diesen Filter gefunden."
                                        },
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    CompactButton("👥 Alle Kontakte öffnen") {
                                        allContactsFilter = "all"
                                        activeTab = "Alle Kontakte"
                                    }
                                }
                            }
                        }
                        items(timelineItems, key = { it.uniqueKey }) { entry ->
                            val person = entry.person
                            val personRelations = relations
                                .filter { it.fromPersonId == person.id }
                                .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
                            val resolved = ContactResolver.resolveTarget(person, personRelations)
                            val isNextHighlight = !showPastDates &&
                                overviewSortMode == "upcoming" &&
                                nextUpcomingDays != null &&
                                entry.daysUntil == nextUpcomingDays

                            val cardBorder = when {
                                entry.isUrgent -> WarnAmber
                                isNextHighlight -> AccentSteel
                                else -> BorderSlate
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(if (isNextHighlight || entry.isUrgent) 1.5.dp else 1.dp, cardBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { detailPerson = person }
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PersonAvatar(name = person.name, photoBase64 = person.photoBase64)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (isNextHighlight) {
                                            Text(
                                                text = if (entry.daysUntil == 0L) "🔥 Heute anstehend" else "⭐ Als Nächstes anstehend",
                                                color = if (entry.daysUntil == 0L) WarnAmber else AccentSteel,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatLastNameFirst(person.name),
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            OverviewCountdownPill(
                                                pillText = entry.pillText,
                                                isUrgent = entry.isUrgent,
                                                isSoon = entry.isSoon,
                                                isRecurring = entry.isRecurring
                                            )
                                        }
                                        if (entry.primaryLine.isNotBlank()) {
                                            Text(
                                                text = entry.primaryLine,
                                                color = when {
                                                    showPastDates || entry.isUrgent -> WarnAmber
                                                    entry.isRecurring -> TreeGreen
                                                    else -> TextSecondary
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = if (entry.isUrgent || isNextHighlight) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                        if (entry.secondaryLine.isNotBlank()) {
                                            Text(
                                                text = entry.secondaryLine,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                                CompactButton("Nachricht") {
                                    popupReason = entry.popupReason
                                    quickMessageText = pickRandomBirthdayGreeting(
                                        extractFirstName(resolved.targetPersonName),
                                        showPastDates
                                    )
                                    popupPerson = person
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingConfirmMessage != null) {
        AlertDialog(
            onDismissRequest = {
                pendingConfirmMessage = null
                pendingConfirmAction = null
            },
            containerColor = CardSlate,
            title = {
                Text("Sicherheitsabfrage", color = WarnAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(pendingConfirmMessage.orEmpty(), color = TextPrimary, fontSize = 13.sp)
            },
            dismissButton = {
                CompactButton("Abbrechen") {
                    pendingConfirmMessage = null
                    pendingConfirmAction = null
                }
            },
            confirmButton = {
                CompactButton("Ja, löschen") {
                    val action = pendingConfirmAction
                    pendingConfirmMessage = null
                    pendingConfirmAction = null
                    action?.invoke()
                }
            }
        )
    }

    // Dialog: Optionen (oben rechts über ⚙)
    if (showOptionsDialog) {
        var backupEmail by remember { mutableStateOf(appPrefs.getString("backup_email", "").orEmpty()) }
        var showRestoreInput by remember { mutableStateOf(false) }
        var restoreJsonText by remember { mutableStateOf("") }

        fun buildBackupJsonObject(): JSONObject {
            val dateStr = formatIsoToDdMmYyyy(LocalDate.now().toString())
            return JSONObject().apply {
                put("app", "Remember Me")
                put("version", "1.0.36")
                put("createdAt", dateStr)
                put("birthdayPopupEnabled", birthdayPopupEnabled)
                put("birthdayPopupHour", birthdayPopupHour)
                put("birthdayPopupMinute", birthdayPopupMinute)
                put("birthdayGreetings", JSONArray(serializeBirthdayGreetingsJson(birthdayGreetings)))
                put("wichtelGroups", JSONArray(serializeWichtelGroupsJson(wichtelGroups)))
                put("wichtelHistory", JSONArray(serializeWichtelHistoryJson(wichtelHistory)))
                val personsArr = JSONArray()
                persons.forEach { p ->
                    personsArr.put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("birthdayIso", p.birthdayIso ?: "")
                            put("hasNoOwnPhone", p.hasNoOwnPhone)
                            put("isHidden", p.isHidden || hiddenPersonIds.contains(p.id))
                            put("phone", p.phone)
                            put("email", p.email)
                            put("notes", p.notes)
                            put("photoBase64", p.photoBase64)
                            put("extraInfos", appPrefs.getString("extra_infos_${p.id}", "").orEmpty())
                            put("timerEnabled", p.timerEnabled)
                            put("timerInterval", p.timerInterval)
                            put("timerUnit", p.timerUnit)
                        }
                    )
                }
                put("persons", personsArr)
                val relArr = JSONArray()
                relations.forEach { r ->
                    relArr.put(
                        JSONObject().apply {
                            put("id", r.id)
                            put("fromPersonId", r.fromPersonId)
                            put("toPersonId", r.toPersonId)
                            put("relationLabel", r.relationLabel)
                        }
                    )
                }
                put("relations", relArr)
                val evArr = JSONArray()
                specialEvents.forEach { ev ->
                    evArr.put(
                        JSONObject().apply {
                            put("id", ev.id)
                            put("personId", ev.personId)
                            put("title", ev.title)
                            put("dateIso", ev.dateIso)
                        }
                    )
                }
                put("specialEvents", evArr)
            }
        }

        Dialog(onDismissRequest = { showOptionsDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙ Optionen & Design", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    CompactButton("✓ Fertig") { showOptionsDialog = false }
                }
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌳 Stammbaum", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton(if (treeEnabled) "✓ Aktiviert" else "Aktiviert") {
                            treeEnabled = true
                            appPrefs.edit().putBoolean("tree_enabled", true).apply()
                        }
                        CompactButton(if (!treeEnabled) "✓ Deaktiviert" else "Deaktiviert") {
                            treeEnabled = false
                            if (activeTab == "Stammbaum") activeTab = "Übersicht"
                            appPrefs.edit().putBoolean("tree_enabled", false).apply()
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎄 Wichtelrunde", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton(if (wichtelEnabled) "✓ Aktiviert" else "Aktiviert") {
                            wichtelEnabled = true
                            appPrefs.edit().putBoolean("wichtel_enabled", true).apply()
                        }
                        CompactButton(if (!wichtelEnabled) "✓ Deaktiviert" else "Deaktiviert") {
                            wichtelEnabled = false
                            if (activeTab == "🎄 Wichteln") activeTab = "Übersicht"
                            appPrefs.edit().putBoolean("wichtel_enabled", false).apply()
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔔 Geburtstags-Popup",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (birthdayPopupEnabled) {
                                    "Handy-Meldung am Geburtstag (%02d:%02d Uhr)".format(birthdayPopupHour, birthdayPopupMinute)
                                } else {
                                    "Keine Handy-Popup-Benachrichtigung"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactButton(if (birthdayPopupEnabled) "✓ Aktiviert" else "Aktiviert") {
                                birthdayPopupEnabled = true
                                BirthdayNotificationHelper.setBirthdayPopupEnabled(context, true)
                                hasNotificationPerm = BirthdayNotificationHelper.hasNotificationPermission(context)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPerm) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            CompactButton(if (!birthdayPopupEnabled) "✓ Deaktiviert" else "Deaktiviert") {
                                birthdayPopupEnabled = false
                                BirthdayNotificationHelper.setBirthdayPopupEnabled(context, false)
                            }
                        }
                    }

                    if (birthdayPopupEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactButton(
                                label = "⏰ Uhrzeit: %02d:%02d".format(birthdayPopupHour, birthdayPopupMinute)
                            ) {
                                TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        birthdayPopupHour = selectedHour
                                        birthdayPopupMinute = selectedMinute
                                        BirthdayNotificationHelper.setNotificationTime(context, selectedHour, selectedMinute)
                                        syncStatus = "Geburtstags-Popup täglich um %02d:%02d Uhr".format(selectedHour, selectedMinute)
                                    },
                                    birthdayPopupHour,
                                    birthdayPopupMinute,
                                    true
                                ).show()
                            }
                            CompactButton("🔔 Test-Popup") {
                                val permOk = BirthdayNotificationHelper.hasNotificationPermission(context)
                                hasNotificationPerm = permOk
                                if (!permOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val today = LocalDate.now()
                                    val sample = visiblePersons.firstOrNull {
                                        ContactResolver.daysUntilNextBirthday(it.birthdayIso, today) == 0L
                                    } ?: visiblePersons.firstOrNull { !it.birthdayIso.isNullOrBlank() }
                                        ?: visiblePersons.firstOrNull()
                                    BirthdayNotificationHelper.sendTestBirthdayPopup(context, sample)
                                    syncStatus = "Test-Geburtstags-Popup gesendet"
                                }
                            }
                            if (!hasNotificationPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                CompactButton("🔓 Freigeben") {
                                    openAppNotificationSettings()
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📱 Homescreen-Widgets (4 Größen)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Mini (2×1) • Leiste (4×1) • Kompakt (3×2) • Groß (4×3)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    CompactButton("↻ Aktualisieren") {
                        RememberMeWidgetUpdater.updateAllAsync(context)
                        syncStatus = "Alle 4 Homescreen-Widgets aktualisiert"
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "🎂 Geburtstagsgrüße (${birthdayGreetings.size})",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Zufällige Glückwunschtexte mit {Name}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    CompactButton("✏️ Bearbeiten") {
                        showOptionsDialog = false
                        showGreetingsEditorDialog = true
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (duplicateSuggestionGroups.isNotEmpty()) "🔗 Kontakte zusammenführen (${duplicateSuggestionGroups.size} Vorschläge)"
                            else "🔗 Kontakte zusammenführen",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Mehrere Kontakte ohne doppelte Nummern/Mails/Adressen vereinen",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    CompactButton("🔗 Öffnen") {
                        showOptionsDialog = false
                        mergeInitialPersonIds = emptyList()
                        showMergeContactsDialog = true
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Text("🎨 Design-Theme (Sanftes Blau / Festlich)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton(
                            label = if (appThemeMode == "warm") "✓ ☀️ Sanftes Blau" else "☀️ Sanftes Blau",
                            modifier = Modifier.weight(1f)
                        ) {
                            applyThemeMode("warm")
                        }
                        CompactButton(
                            label = if (appThemeMode == "cozy-dark") "✓ 🌙 Nachtblau" else "🌙 Nachtblau",
                            modifier = Modifier.weight(1f)
                        ) {
                            applyThemeMode("cozy-dark")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton(
                            label = if (appThemeMode == "xmas") "✓ 🎄 Weihnachten" else "🎄 Weihnachten",
                            modifier = Modifier.weight(1f)
                        ) {
                            applyThemeMode("xmas")
                        }
                        CompactButton(
                            label = if (appThemeMode == "classic") "✓ 💎 Klassisch" else "💎 Klassisch",
                            modifier = Modifier.weight(1f)
                        ) {
                            applyThemeMode("classic")
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Text("💾 Vollständiges Backup & Restore (.json)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = backupEmail,
                    onValueChange = {
                        backupEmail = it
                        appPrefs.edit().putString("backup_email", it).apply()
                    },
                    label = { Text("Empfänger-Mail (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactButton("📧 Backup senden") {
                        try {
                            val dateStr = formatIsoToDdMmYyyy(LocalDate.now().toString())
                            val rootJson = buildBackupJsonObject()
                            val fileName = "Remember-Me-Sicherung-$dateStr.json"
                            val backupFile = File(context.cacheDir, fileName)
                            backupFile.writeText(rootJson.toString(2))

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                backupFile
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                if (backupEmail.isNotBlank()) {
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(backupEmail.trim()))
                                }
                                putExtra(Intent.EXTRA_SUBJECT, "Remember Me – Sicherung vom $dateStr")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Anbei die Remember Me Sicherungsdatei ($fileName) vom $dateStr (${persons.size} Kontakte, ${relations.size} Beziehungen, ${wichtelGroups.size} Wichtelrunden, ${wichtelHistory.size} Historie)."
                                )
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Sicherung per Mail senden"))
                        } catch (e: Exception) {
                            syncStatus = "Fehler beim Erstellen der Sicherung"
                        }
                    }
                    CompactButton("📋 Kopieren") {
                        runCatching {
                            clipboardManager.setText(AnnotatedString(buildBackupJsonObject().toString(2)))
                            syncStatus = "Backup in Zwischenablage kopiert"
                        }
                    }
                    CompactButton("📂 Laden") {
                        showRestoreInput = !showRestoreInput
                    }
                }

                if (showRestoreInput) {
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        label = { Text("JSON-Backup hier einfügen") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    CompactButton("📥 Sicherung jetzt wiederherstellen") {
                        scope.launch {
                            runCatching {
                                val parsed = JSONObject(restoreJsonText.trim())
                                val stateObj = parsed.optJSONObject("state") ?: parsed
                                if (stateObj.has("birthdayPopupEnabled")) {
                                    val restoredPopup = stateObj.optBoolean("birthdayPopupEnabled", true)
                                    birthdayPopupEnabled = restoredPopup
                                    BirthdayNotificationHelper.setBirthdayPopupEnabled(context, restoredPopup)
                                }
                                if (stateObj.has("birthdayPopupHour") || stateObj.has("birthdayPopupMinute")) {
                                    val rH = stateObj.optInt("birthdayPopupHour", birthdayPopupHour).coerceIn(0, 23)
                                    val rM = stateObj.optInt("birthdayPopupMinute", birthdayPopupMinute).coerceIn(0, 59)
                                    birthdayPopupHour = rH
                                    birthdayPopupMinute = rM
                                    BirthdayNotificationHelper.setNotificationTime(context, rH, rM)
                                }
                                val bgArr = stateObj.optJSONArray("birthdayGreetings")
                                if (bgArr != null) {
                                    saveBirthdayGreetings(parseBirthdayGreetingsJson(bgArr.toString()))
                                }
                                val wgArr = stateObj.optJSONArray("wichtelGroups")
                                if (wgArr != null) {
                                    saveWichtelGroups(parseWichtelGroupsJson(wgArr.toString()))
                                }
                                val whArr = stateObj.optJSONArray("wichtelHistory")
                                if (whArr != null) {
                                    saveWichtelHistory(parseWichtelHistoryJson(whArr.toString()))
                                }
                                val pArr = stateObj.optJSONArray("persons")
                                if (pArr != null) {
                                    for (i in 0 until pArr.length()) {
                                        val po = pArr.getJSONObject(i)
                                        val isHid = po.optBoolean("isHidden", false)
                                        val newId = db.upsertPerson(
                                            PersonEntity(
                                                name = po.optString("name", "Kontakt"),
                                                birthdayIso = po.optString("birthdayIso", "").ifBlank { null },
                                                hasNoOwnPhone = po.optBoolean("hasNoOwnPhone", false),
                                                phone = po.optString("phone", ""),
                                                email = po.optString("email", ""),
                                                notes = po.optString("notes", ""),
                                                timerEnabled = po.optBoolean("timerEnabled", true),
                                                timerInterval = po.optInt("timerInterval", 14),
                                                timerUnit = po.optString("timerUnit", TimerUnit.DAYS.name),
                                                isHidden = isHid,
                                                photoBase64 = po.optString("photoBase64", "")
                                            )
                                        )
                                        if (isHid) setPersonHidden(newId, true)
                                    }
                                }
                                showRestoreInput = false
                                restoreJsonText = ""
                                showOptionsDialog = false
                            }
                        }
                    }
                }

                }

                HorizontalDivider(color = BorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Schließen") { showOptionsDialog = false }
                }
            }
        }
    }

    if (showGreetingsEditorDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedLetter by remember { mutableStateOf("") }
        var greetingDropdownExpanded by remember { mutableStateOf(false) }
        val filteredIndices = remember(birthdayGreetings, searchQuery, selectedLetter) {
            val q = searchQuery.trim().lowercase()
            birthdayGreetings.indices.filter { idx ->
                val text = birthdayGreetings[idx]
                val matchesQ = q.isBlank() ||
                    text.lowercase().contains(q) ||
                    (idx + 1).toString() == q
                val matchesL = selectedLetter.isBlank() ||
                    extractTextInitial(text).equals(selectedLetter, ignoreCase = true)
                matchesQ && matchesL
            }
        }

        Dialog(onDismissRequest = { showGreetingsEditorDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🎂 Geburtstagsgrüße (${birthdayGreetings.size})",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("🔄 100 Standard") {
                            pendingConfirmMessage = "Alle Geburtstagsgrüße auf die 100 Standard-Glückwunschtexte zurücksetzen?"
                            pendingConfirmAction = {
                                saveBirthdayGreetings(DEFAULT_BIRTHDAY_GREETINGS)
                            }
                        }
                        CompactButton("✓ Fertig") {
                            showGreetingsEditorDialog = false
                        }
                    }
                }
                Text(
                    "Beim Öffnen einer Nachricht wählt die App zufällig einen dieser Glückwunschtexte aus. {Name} wird automatisch durch den Vornamen ersetzt.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("🔎 Spruch suchen...", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Box {
                        CompactButton("▼ Liste (${filteredIndices.size})") {
                            greetingDropdownExpanded = true
                        }
                        if (greetingDropdownExpanded) {
                            Dialog(
                                onDismissRequest = { greetingDropdownExpanded = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { greetingDropdownExpanded = false }
                                        .padding(horizontal = 14.dp, vertical = 28.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 440.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CardSlate, RoundedCornerShape(12.dp))
                                            .border(1.5.dp, AccentSteel, RoundedCornerShape(12.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgSlate)
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "✓ ${filteredIndices.size} Sprüche",
                                                    color = AccentSteel,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                CompactButton("✓ Bestätigen") {
                                                    greetingDropdownExpanded = false
                                                }
                                            }
                                            AlphabetQuickBar(
                                                selectedLetter = selectedLetter,
                                                onSelectLetter = { selectedLetter = it }
                                            )
                                        }
                                        HorizontalDivider(color = BorderSlate)
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f, fill = false)
                                        ) {
                                            items(filteredIndices, key = { it }) { gIdx ->
                                                val gText = birthdayGreetings.getOrNull(gIdx).orEmpty()
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            searchQuery = (gIdx + 1).toString()
                                                            greetingDropdownExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "#${gIdx + 1}: $gText",
                                                        color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                HorizontalDivider(color = BorderSlate.copy(alpha = 0.45f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CompactButton("+ Neu") {
                        saveBirthdayGreetings(
                            listOf("Alles Liebe und Gute zum Geburtstag, {Name}! 🎂🎉") + birthdayGreetings
                        )
                        searchQuery = ""
                        selectedLetter = ""
                    }
                }
                AlphabetQuickBar(
                    selectedLetter = selectedLetter,
                    onSelectLetter = { selectedLetter = it }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredIndices, key = { it }) { idx ->
                        val currentText = birthdayGreetings.getOrNull(idx).orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgSlate, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "#${idx + 1}",
                                color = AccentSteel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = currentText,
                                onValueChange = { newVal ->
                                    val copy = birthdayGreetings.toMutableList()
                                    if (idx in copy.indices) {
                                        copy[idx] = newVal
                                        saveBirthdayGreetings(copy)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (birthdayGreetings.size > 1) {
                                Text(
                                    "✕",
                                    color = WarnAmber,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            val copy = birthdayGreetings.toMutableList()
                                            if (idx in copy.indices) {
                                                copy.removeAt(idx)
                                                saveBirthdayGreetings(copy)
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactButton("← Optionen") {
                        showGreetingsEditorDialog = false
                        showOptionsDialog = true
                    }
                    CompactButton("Fertig") {
                        showGreetingsEditorDialog = false
                    }
                }
            }
        }
    }

    // Dialog: Manuell Timer-Eintrag erstellen / bearbeiten (über den Plus-Button im Timer-Tab)
    if (showTimerEntryDialog) {
        val sortedCandidates = remember(visiblePersons) { visiblePersons.sortedByLastName() }
        val initialTarget = editingTimerPerson ?: sortedCandidates.firstOrNull()
        var selectedContactId by remember(editingTimerPerson, sortedCandidates.size) {
            mutableStateOf(initialTarget?.id ?: -1L)
        }
        var createNewContactMode by remember(editingTimerPerson, sortedCandidates.isEmpty()) {
            mutableStateOf(editingTimerPerson == null && sortedCandidates.isEmpty())
        }
        var newContactName by remember { mutableStateOf("") }
        var newContactPhone by remember { mutableStateOf("") }
        var intervalText by remember(editingTimerPerson) {
            mutableStateOf((initialTarget?.timerInterval?.takeIf { it > 0 } ?: 14).toString())
        }
        var unitVal by remember(editingTimerPerson) {
            mutableStateOf(initialTarget?.timerUnit?.ifBlank { TimerUnit.DAYS.name } ?: TimerUnit.DAYS.name)
        }
        var unitDropdownExpanded by remember { mutableStateOf(false) }
        var startDateText by remember(editingTimerPerson) {
            val baseIso = initialTarget?.lastContactedIso?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
            mutableStateOf(formatIsoToDdMmYyyy(baseIso))
        }
        var timerNote by remember(editingTimerPerson) {
            mutableStateOf(initialTarget?.notes.orEmpty())
        }

        val timerUnitOptions = listOf(
            "DAYS" to "Tage",
            "WEEKS" to "Wochen",
            "MONTHS" to "Monate",
            "YEARS" to "Jahre"
        )
        val selectedUnitLabel = timerUnitOptions.find { it.first == unitVal }?.second ?: "Tage"
        val parsedInterval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 14
        val parsedBaseDate = runCatching {
            val iso = normalizeDateInputToIso(startDateText)
            if (iso.isNotBlank() && !iso.startsWith("--")) LocalDate.parse(iso) else LocalDate.now()
        }.getOrElse { LocalDate.now() }
        val previewNextDate = addIntervalToIsoDate(parsedBaseDate, parsedInterval, unitVal)
        val previewNextFormatted = formatIsoToDdMmYyyy(previewNextDate.toString())

        fun saveTimerEntry() {
            scope.launch {
                val cleanInterval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 14
                val cleanBaseIso = normalizeDateInputToIso(startDateText)
                    .takeIf { it.isNotBlank() && !it.startsWith("--") }
                    ?: LocalDate.now().toString()

                if (createNewContactMode) {
                    if (newContactName.isNotBlank()) {
                        db.upsertPerson(
                            PersonEntity(
                                name = newContactName.trim(),
                                birthdayIso = null,
                                hasNoOwnPhone = false,
                                phone = newContactPhone.trim(),
                                email = "",
                                notes = timerNote.trim(),
                                timerEnabled = true,
                                timerInterval = cleanInterval,
                                timerUnit = unitVal,
                                lastContactedIso = cleanBaseIso
                            )
                        )
                        showTimerEntryDialog = false
                        editingTimerPerson = null
                    }
                } else {
                    val target = visiblePersons.find { it.id == selectedContactId }
                    if (target != null) {
                        db.updatePerson(
                            target.copy(
                                timerEnabled = true,
                                timerInterval = cleanInterval,
                                timerUnit = unitVal,
                                lastContactedIso = cleanBaseIso,
                                notes = if (timerNote.isNotBlank()) timerNote.trim() else target.notes
                            )
                        )
                        showTimerEntryDialog = false
                        editingTimerPerson = null
                    }
                }
            }
        }

        Dialog(
            onDismissRequest = {
                showTimerEntryDialog = false
                editingTimerPerson = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fixierte Kopfzeile oben (kein Scrollen nötig zum Speichern)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingTimerPerson != null) "⏳ Timer bearbeiten" else "⏳ Timer-Eintrag erstellen",
                        color = TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("✕") {
                            showTimerEntryDialog = false
                            editingTimerPerson = null
                        }
                        CompactButton("✓ Speichern") { saveTimerEntry() }
                    }
                }
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sortedCandidates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kontakt auswählen (Nachname, ABCD):",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            CompactButton(if (createNewContactMode) "Aus Kontakten wählen" else "＋ Neuer Kontakt") {
                                createNewContactMode = !createNewContactMode
                            }
                        }
                    }

                    if (createNewContactMode || sortedCandidates.isEmpty()) {
                        OutlinedTextField(
                            value = newContactName,
                            onValueChange = { newContactName = it },
                            label = { Text("Name des neuen Kontakts") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newContactPhone,
                            onValueChange = { newContactPhone = it },
                            label = { Text("Telefon (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        ContactSearchDropdown(
                            persons = sortedCandidates,
                            selectedPersonId = selectedContactId.takeIf { it > 0L },
                            onSelectPerson = { p ->
                                selectedContactId = p.id
                                intervalText = (p.timerInterval.takeIf { it > 0 } ?: 14).toString()
                                unitVal = p.timerUnit.ifBlank { TimerUnit.DAYS.name }
                                startDateText = formatIsoToDdMmYyyy(
                                    p.lastContactedIso?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
                                )
                                timerNote = p.notes
                            },
                            placeholderText = "🔎 Kontakt für Timer wählen",
                            confirmButtonLabel = "✓ Bestätigen",
                            showInlineAlphabet = true
                        )
                    }

                    Text(
                        text = "Schnellwahl für Melde-Rhythmus:",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val timerPresets = listOf(
                        Triple("7 Tage", "7", "DAYS"),
                        Triple("14 Tage", "14", "DAYS"),
                        Triple("1 Monat", "1", "MONTHS"),
                        Triple("3 Monate", "3", "MONTHS"),
                        Triple("6 Monate", "6", "MONTHS"),
                        Triple("1 Jahr", "1", "YEARS")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(timerPresets) { (label, countStr, uCode) ->
                            val isSelected = intervalText == countStr && unitVal == uCode
                            CompactButton(if (isSelected) "✓ $label" else label) {
                                intervalText = countStr
                                unitVal = uCode
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Intervall (Zahl)") },
                            modifier = Modifier.weight(0.9f),
                            singleLine = true
                        )
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text("Einheit", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgSlate, RoundedCornerShape(6.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                        .clickable { unitDropdownExpanded = true }
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedUnitLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("▼", color = AccentSteel, fontSize = 11.sp)
                                }
                                DropdownMenu(
                                    expanded = unitDropdownExpanded,
                                    onDismissRequest = { unitDropdownExpanded = false },
                                    modifier = Modifier.background(CardSlate)
                                ) {
                                    Column(modifier = Modifier.width(210.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgSlate)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "✓ $selectedUnitLabel",
                                                color = AccentSteel,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            CompactButton("✓ Bestätigen") {
                                                unitDropdownExpanded = false
                                            }
                                        }
                                        HorizontalDivider(color = BorderSlate)
                                        timerUnitOptions.forEach { (uCode, uLabel) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        uLabel,
                                                        color = if (uCode == unitVal) AccentSteel else TextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (uCode == unitVal) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    unitVal = uCode
                                                    unitDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DateInputWithCalendar(
                        value = startDateText,
                        onValueChange = { startDateText = it },
                        label = "Startdatum / Letzter Kontakt (TT.MM.JJJJ)"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "📅 Nächster Melde-Timer: $previewNextFormatted (🔁 Alle ${formatRhythmTextDe(parsedInterval, unitVal)})",
                            color = TreeGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = timerNote,
                        onValueChange = { timerNote = it },
                        label = { Text("Notiz / Anlass (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = BorderSlate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (editingTimerPerson != null) {
                        CompactButton("Timer ausschalten") {
                            val pToDisable = editingTimerPerson
                            if (pToDisable != null) {
                                scope.launch {
                                    db.updatePerson(pToDisable.copy(timerEnabled = false))
                                    showTimerEntryDialog = false
                                    editingTimerPerson = null
                                }
                            }
                        }
                    } else {
                        CompactButton("Abbrechen") {
                            showTimerEntryDialog = false
                            editingTimerPerson = null
                        }
                    }
                    CompactButton("✓ Timer speichern") { saveTimerEntry() }
                }
            }
        }
    }

    // Dialog: Neuen Kontakt anlegen (inkl. Beziehungen für den Stammbaum & Kontaktfoto)
    if (showCreateDialog) {
        val prefs = remember { context.getSharedPreferences("remember_me_prefs", android.content.Context.MODE_PRIVATE) }
        var name by remember { mutableStateOf("") }
        var bday by remember { mutableStateOf(formatIsoToDdMmYyyy(LocalDate.now().toString())) }
        var noPhone by remember { mutableStateOf(false) }
        var saveInDeviceContacts by remember { mutableStateOf(true) }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var photoVal by remember { mutableStateOf("") }
        var extraInfos by remember { mutableStateOf(listOf<Pair<String, String>>()) }
        var timerInterval by remember { mutableStateOf("14") }
        var relLabel by remember { mutableStateOf("Kind von") }
        var selectedRelTargetId by remember { mutableStateOf(-1L) }
        var createRelExpanded by remember { mutableStateOf(false) }

        val createPhotoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val encoded = encodeUriToBase64Jpeg(context, uri)
                if (encoded.isNotBlank()) {
                    photoVal = encoded
                }
            }
        }

        fun saveNewContact() {
            if (name.isNotBlank()) {
                scope.launch {
                    val basePerson = PersonEntity(
                        name = name.trim(),
                        birthdayIso = normalizeDateInputToIso(bday),
                        hasNoOwnPhone = noPhone,
                        phone = phone.trim(),
                        email = email.trim(),
                        notes = notes.trim(),
                        timerEnabled = true,
                        timerInterval = timerInterval.toIntOrNull() ?: 14,
                        timerUnit = TimerUnit.DAYS.name,
                        lastContactedIso = LocalDate.now().toString(),
                        photoBase64 = photoVal
                    )
                    var deviceCid: String? = null
                    if (saveInDeviceContacts) {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            deviceCid = syncRepo.saveOrUpdateDeviceContact(
                                context.contentResolver,
                                basePerson,
                                extraInfos
                            )
                            syncStatus = "Kontakt in App & Handykontakten gespeichert"
                        } else {
                            contactPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS
                                )
                            )
                            runCatching {
                                val insertIntent = Intent(Intent.ACTION_INSERT).apply {
                                    type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                                    putExtra(android.provider.ContactsContract.Intents.Insert.NAME, basePerson.name)
                                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, basePerson.phone)
                                    putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, basePerson.email)
                                    putExtra(android.provider.ContactsContract.Intents.Insert.NOTES, basePerson.notes)
                                }
                                context.startActivity(insertIntent)
                            }
                        }
                    }
                    val newId = db.upsertPerson(
                        basePerson.copy(importedFromDeviceContactId = deviceCid)
                    )
                    if (selectedRelTargetId > 0 && relLabel.isNotBlank()) {
                        addBidirectionalRelation(newId, selectedRelTargetId, relLabel)
                    }
                    val serializedExtras = extraInfos
                        .filter { it.first.isNotBlank() || it.second.isNotBlank() }
                        .joinToString("\n") { "${it.first.replace("\t", " ")}\t${it.second.replace("\t", " ")}" }
                    prefs.edit().putString("extra_infos_$newId", serializedExtras).apply()
                    selectedTreeRootId = newId
                    showCreateDialog = false
                }
            }
        }

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fixierte Kopfzeile oben (kein Scrollen nötig zum Bestätigen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Kontakt anlegen",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("✕") { showCreateDialog = false }
                        CompactButton("✓ Speichern") { saveNewContact() }
                    }
                }
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PersonAvatar(name = name.ifBlank { "?" }, photoBase64 = photoVal, sizeDp = 44)
                            Column {
                                Text("Kontaktfoto", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Wird auch mit Handykontakten synchronisiert", color = TextMuted, fontSize = 10.5.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactButton("📷 Foto") {
                                createPhotoPickerLauncher.launch("image/*")
                            }
                            if (photoVal.isNotBlank()) {
                                CompactButton("✕") {
                                    photoVal = ""
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    DateInputWithCalendar(value = bday, onValueChange = { bday = it }, label = "Geburtsdatum (TT.MM.JJJJ)")

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveInDeviceContacts, onCheckedChange = { saveInDeviceContacts = it })
                        Text("In Handykontakten speichern", color = TextSecondary, fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = noPhone, onCheckedChange = { noPhone = it })
                        Text("Keine eigene Nummer", color = TextSecondary, fontSize = 12.sp)
                    }

                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-Mail") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = timerInterval, onValueChange = { timerInterval = it }, label = { Text("Melde-Timer (Tage)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())

                    // Zusätzliche Informationszeilen (nur + Button unten links)
                    extraInfos.forEachIndexed { idx, (infoLabel, infoValue) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = infoLabel,
                                onValueChange = { newL ->
                                    extraInfos = extraInfos.toMutableList().also { it[idx] = newL to infoValue }
                                },
                                label = { Text("Bezeichnung") },
                                modifier = Modifier.weight(0.9f)
                            )
                            OutlinedTextField(
                                value = infoValue,
                                onValueChange = { newV ->
                                    extraInfos = extraInfos.toMutableList().also { it[idx] = infoLabel to newV }
                                },
                                label = { Text("Wert") },
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                "✕",
                                color = WarnAmber,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        pendingConfirmMessage = "Zusätzliche Info-Zeile wirklich löschen?"
                                        pendingConfirmAction = {
                                            extraInfos = extraInfos.filterIndexed { i, _ -> i != idx }
                                        }
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CompactButton("+") {
                            val defaultLabel = if (extraInfos.isEmpty()) "2. Telefon" else "Info"
                            extraInfos = extraInfos + (defaultLabel to "")
                        }
                    }

                    if (treeEnabled && persons.isNotEmpty()) {
                        HorizontalDivider(color = BorderSlate)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { createRelExpanded = !createRelExpanded }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${if (createRelExpanded) "▼" else "▶"} Beziehungen",
                                color = AccentSteel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (createRelExpanded) "Einklappen" else "Ausklappen",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (createRelExpanded) {
                            var createRelLabelExpanded by remember { mutableStateOf(false) }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Beziehungsart:", color = TextSecondary, fontSize = 11.sp)
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BgSlate, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                            .clickable { createRelLabelExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            relLabel,
                                            color = TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("▼", color = AccentSteel, fontSize = 11.sp)
                                    }
                                    DropdownMenu(
                                        expanded = createRelLabelExpanded,
                                        onDismissRequest = { createRelLabelExpanded = false },
                                        modifier = Modifier.background(CardSlate)
                                    ) {
                                        Column(modifier = Modifier.width(260.dp)) {
                                            // Fixierte Leiste oben im Dropdown
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(BgSlate)
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "✓ $relLabel",
                                                    color = AccentSteel,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                CompactButton("✓ Bestätigen") {
                                                    createRelLabelExpanded = false
                                                }
                                            }
                                            HorizontalDivider(color = BorderSlate)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 200.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                RELATION_PRESETS.forEach { preset ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                preset,
                                                                color = if (preset == relLabel) AccentSteel else TextPrimary,
                                                                fontSize = 13.sp,
                                                                fontWeight = if (preset == relLabel) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        },
                                                        onClick = {
                                                            relLabel = preset
                                                            createRelLabelExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Text("Verknüpfter Kontakt (Nachname, Vorname):", color = TextSecondary, fontSize = 11.sp)
                                ContactSearchDropdown(
                                    persons = persons,
                                    selectedPersonId = selectedRelTargetId.takeIf { it > 0L },
                                    onSelectPerson = { p -> selectedRelTargetId = p.id },
                                    allowNone = true,
                                    noneLabel = "-- Keine Verknüpfung --",
                                    onSelectNone = { selectedRelTargetId = -1L },
                                    showInlineAlphabet = true
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompactButton("Abbrechen") { showCreateDialog = false }
                    CompactButton("✓ Speichern") { saveNewContact() }
                }
            }
        }
    }

    // Dialog: Kontakt-Details (wird geöffnet durch Antippen des Namens in der Übersicht)
    detailPerson?.let { dp ->
        val prefs = remember { context.getSharedPreferences("remember_me_prefs", android.content.Context.MODE_PRIVATE) }
        var nameVal by remember(dp) { mutableStateOf(dp.name) }
        var bdayVal by remember(dp) { mutableStateOf(formatIsoToDdMmYyyy(dp.birthdayIso)) }
        var phoneVal by remember(dp) { mutableStateOf(dp.phone) }
        var noPhoneVal by remember(dp) { mutableStateOf(dp.hasNoOwnPhone) }
        var saveInDeviceVal by remember(dp) { mutableStateOf(true) }
        var isHiddenVal by remember(dp, hiddenPersonIds) { mutableStateOf(dp.isHidden || hiddenPersonIds.contains(dp.id)) }
        var emailVal by remember(dp) { mutableStateOf(dp.email) }
        var notesVal by remember(dp) { mutableStateOf(dp.notes) }
        var photoVal by remember(dp) { mutableStateOf(dp.photoBase64) }
        val detailPhotoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val encoded = encodeUriToBase64Jpeg(context, uri)
                if (encoded.isNotBlank()) {
                    photoVal = encoded
                }
            }
        }
        var extraInfos by remember(dp) {
            val raw = prefs.getString("extra_infos_${dp.id}", "").orEmpty()
            val parsed = raw.lines().filter { it.isNotBlank() }.map { line ->
                val parts = line.split("\t", limit = 2)
                (parts.getOrNull(0) ?: "Info") to (parts.getOrNull(1) ?: "")
            }
            mutableStateOf(parsed)
        }
        var newEvTitle by remember { mutableStateOf("") }
        var newEvCount by remember { mutableStateOf("1") }
        var newEvUnit by remember { mutableStateOf("DAYS") }
        var newEvRepeatChecked by remember { mutableStateOf(false) }
        var evUnitDropdownExpanded by remember { mutableStateOf(false) }

        // Neue Beziehung hinzufügen
        val otherPersons = remember(persons, dp.id) { persons.filter { it.id != dp.id }.sortedByLastName() }
        var newRelLabel by remember { mutableStateOf("Kind von") }
        var newRelTargetId by remember(dp, otherPersons.size) {
            mutableStateOf(otherPersons.firstOrNull()?.id ?: -1L)
        }

        val currentRelations = relations
            .filter { it.fromPersonId == dp.id }
            .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
        val currentReminders = specialEvents.filter { it.personId == dp.id }
        var detailRelExpanded by remember(dp) { mutableStateOf(false) }

        fun saveDetailChanges() {
            scope.launch {
                var updated = dp.copy(
                    name = nameVal.trim().ifBlank { dp.name },
                    birthdayIso = normalizeDateInputToIso(bdayVal),
                    phone = phoneVal.trim(),
                    hasNoOwnPhone = noPhoneVal,
                    email = emailVal.trim(),
                    notes = notesVal.trim(),
                    isHidden = isHiddenVal,
                    photoBase64 = photoVal
                )
                if (saveInDeviceVal) {
                    val hasWrite = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasWrite) {
                        val cid = syncRepo.saveOrUpdateDeviceContact(
                            context.contentResolver,
                            updated,
                            extraInfos
                        )
                        if (!cid.isNullOrBlank()) {
                            updated = updated.copy(importedFromDeviceContactId = cid)
                        }
                        syncStatus = "Kontakt in App & Handykontakten aktualisiert"
                    } else {
                        contactPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS
                            )
                        )
                    }
                }
                db.updatePerson(updated)
                setPersonHidden(dp.id, isHiddenVal)
                val serializedExtras = extraInfos
                    .filter { it.first.isNotBlank() || it.second.isNotBlank() }
                    .joinToString("\n") { "${it.first.replace("\t", " ")}\t${it.second.replace("\t", " ")}" }
                prefs.edit().putString("extra_infos_${dp.id}", serializedExtras).apply()
                if (newEvTitle.isNotBlank()) {
                    val cCount = newEvCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val nextDateIso = addIntervalToIsoDate(LocalDate.now(), cCount, newEvUnit).toString()
                    val targetDateSpec = if (newEvRepeatChecked) {
                        "REPEAT:$cCount:$newEvUnit:$nextDateIso"
                    } else {
                        nextDateIso
                    }
                    db.insertSpecialEvent(
                        SpecialEventEntity(
                            personId = dp.id,
                            title = newEvTitle.trim(),
                            dateIso = targetDateSpec,
                            leadDays = 1
                        )
                    )
                }
                detailPerson = null
            }
        }

        Dialog(onDismissRequest = { detailPerson = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fixierte Leiste oben im Dialog (kein Scrollen nötig zum Bestätigen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Details: ${dp.name}",
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (treeEnabled) {
                            CompactButton("🌳") {
                                selectedTreeRootId = dp.id
                                detailPerson = null
                                activeTab = "Stammbaum"
                            }
                        }
                        CompactButton("✕") { detailPerson = null }
                        CompactButton("✓ Speichern") { saveDetailChanges() }
                    }
                }
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PersonAvatar(name = nameVal.ifBlank { dp.name }, photoBase64 = photoVal, sizeDp = 44)
                            Column {
                                Text("Kontaktfoto", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (photoVal.isNotBlank()) "Foto vorhanden (Handy & App)" else "Aus Galerie wählen oder Sync",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactButton("📷 Foto") {
                                detailPhotoPickerLauncher.launch("image/*")
                            }
                            if (photoVal.isNotBlank()) {
                                CompactButton("✕") {
                                    photoVal = ""
                                }
                            }
                        }
                    }
                    if (persons.size >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CompactButton("🔗 Mit anderem Kontakt zusammenführen") {
                                mergeInitialPersonIds = listOf(dp.id)
                                detailPerson = null
                                showMergeContactsDialog = true
                            }
                        }
                    }
                    OutlinedTextField(value = nameVal, onValueChange = { nameVal = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    DateInputWithCalendar(value = bdayVal, onValueChange = { bdayVal = it }, label = "Geburtsdatum (TT.MM.JJJJ)")
                    OutlinedTextField(value = phoneVal, onValueChange = { phoneVal = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveInDeviceVal, onCheckedChange = { saveInDeviceVal = it })
                        Text("In Handykontakten speichern", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isHiddenVal,
                            onCheckedChange = {
                                isHiddenVal = it
                                setPersonHidden(dp.id, it)
                                if (it) {
                                    detailPerson = null
                                }
                            }
                        )
                        Text("Kontakt ausblenden", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = noPhoneVal, onCheckedChange = { noPhoneVal = it })
                        Text("Keine eigene Nummer", color = TextSecondary, fontSize = 12.sp)
                    }
                    OutlinedTextField(value = emailVal, onValueChange = { emailVal = it }, label = { Text("E-Mail-Adresse") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notesVal, onValueChange = { notesVal = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())

                    // Zusätzliche Informationszeilen (nur + Button unten links)
                    extraInfos.forEachIndexed { idx, (infoLabel, infoValue) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = infoLabel,
                                onValueChange = { newL ->
                                    extraInfos = extraInfos.toMutableList().also { it[idx] = newL to infoValue }
                                },
                                label = { Text("Bezeichnung") },
                                modifier = Modifier.weight(0.9f)
                            )
                            OutlinedTextField(
                                value = infoValue,
                                onValueChange = { newV ->
                                    extraInfos = extraInfos.toMutableList().also { it[idx] = infoLabel to newV }
                                },
                                label = { Text("Wert") },
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                "✕",
                                color = WarnAmber,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        pendingConfirmMessage = "Zusätzliche Info-Zeile wirklich löschen?"
                                        pendingConfirmAction = {
                                            extraInfos = extraInfos.filterIndexed { i, _ -> i != idx }
                                        }
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CompactButton("+") {
                            val defaultLabel = if (extraInfos.isEmpty()) "2. Telefon" else "Info"
                            extraInfos = extraInfos + (defaultLabel to "")
                        }
                    }

                    // Abschnitt: Beziehungen für Stammbaum (standardmäßig eingeklappt)
                    if (treeEnabled) {
                        HorizontalDivider(color = BorderSlate)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailRelExpanded = !detailRelExpanded }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${if (detailRelExpanded) "▼" else "▶"} Beziehungen (${currentRelations.size})",
                                color = AccentSteel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (detailRelExpanded) "Einklappen" else "Ausklappen",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        if (detailRelExpanded) {
                            // Oben fixierte Auswahl- und Bestätigungsleiste für neue Beziehung
                            if (otherPersons.isNotEmpty()) {
                                var detailRelLabelExpanded by remember { mutableStateOf(false) }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgSlate.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Beziehung zuordnen:",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        CompactButton("✓ + Beziehung speichern") {
                                            if (newRelTargetId > 0) {
                                                scope.launch {
                                                    addBidirectionalRelation(dp.id, newRelTargetId, newRelLabel)
                                                }
                                            }
                                        }
                                    }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CardSlate, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                            .clickable { detailRelLabelExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Beziehungsart: $newRelLabel",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text("▼", color = AccentSteel, fontSize = 10.sp)
                                        }
                                        DropdownMenu(
                                            expanded = detailRelLabelExpanded,
                                            onDismissRequest = { detailRelLabelExpanded = false },
                                            modifier = Modifier.background(CardSlate)
                                        ) {
                                            Column(modifier = Modifier.width(260.dp)) {
                                                // Fixierte Leiste oben im Dropdown
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(BgSlate)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "✓ $newRelLabel",
                                                        color = AccentSteel,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    CompactButton("✓ Bestätigen") {
                                                        detailRelLabelExpanded = false
                                                    }
                                                }
                                                HorizontalDivider(color = BorderSlate)
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 200.dp)
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    RELATION_PRESETS.forEach { preset ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    preset,
                                                                    color = if (preset == newRelLabel) AccentSteel else TextPrimary,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (preset == newRelLabel) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                            },
                                                            onClick = {
                                                                newRelLabel = preset
                                                                detailRelLabelExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    ContactSearchDropdown(
                                        persons = otherPersons,
                                        selectedPersonId = newRelTargetId.takeIf { it > 0L },
                                        onSelectPerson = { op -> newRelTargetId = op.id },
                                        confirmButtonLabel = "✓ + Speichern",
                                        onConfirmAction = {
                                            if (newRelTargetId > 0) {
                                                scope.launch {
                                                    addBidirectionalRelation(dp.id, newRelTargetId, newRelLabel)
                                                }
                                            }
                                        },
                                        showInlineAlphabet = true
                                    )
                                }
                            }

                            if (currentRelations.isEmpty()) {
                                Text("Noch keine Beziehungen verknüpft.", color = TextMuted, fontSize = 11.sp)
                            } else {
                                currentRelations.sortedWith { a, b -> LastNamePersonComparator.compare(a.second, b.second) }.forEach { (rel, targetPerson) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BgSlate, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${rel.relationLabel} ${formatLastNameFirst(targetPerson.name)}",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "✕",
                                            color = WarnAmber,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    pendingConfirmMessage = "Beziehung „${rel.relationLabel} ${targetPerson.name}\" wirklich löschen?"
                                                    pendingConfirmAction = {
                                                        scope.launch {
                                                            db.deleteRelation(rel)
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Abschnitt: Erinnerungen
                    HorizontalDivider(color = BorderSlate)
                    Text("Erinnerungen:", color = AccentSteel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    currentReminders.forEach { ev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgSlate, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${ev.title} (${formatIsoToDdMmYyyy(ev.dateIso)})",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "✕",
                                color = WarnAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        pendingConfirmMessage = "Erinnerung „${ev.title}\" wirklich löschen?"
                                        pendingConfirmAction = {
                                            scope.launch { db.deleteSpecialEvent(ev) }
                                        }
                                    }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }

                    Text("Neue Erinnerung eintragen:", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = newEvTitle, onValueChange = { newEvTitle = it }, label = { Text("Neue Erinnerung (z. B. Anrufen)") }, modifier = Modifier.fillMaxWidth())

                    val parsedEvCount = newEvCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val calculatedNextEvDateIso = addIntervalToIsoDate(LocalDate.now(), parsedEvCount, newEvUnit).toString()
                    val calculatedNextEvDateFormatted = formatIsoToDdMmYyyy(calculatedNextEvDateIso)
                    val unitOptions = listOf(
                        "DAYS" to "Tag",
                        "WEEKS" to "Woche",
                        "MONTHS" to "Monat",
                        "YEARS" to "Jahr"
                    )
                    val selectedUnitLabel = unitOptions.find { it.first == newEvUnit }?.second ?: "Tag"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newEvCount,
                            onValueChange = { newEvCount = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Rhythmus (Zahl)") },
                            modifier = Modifier.weight(0.9f)
                        )
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text("Einheit", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgSlate, RoundedCornerShape(6.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                        .clickable { evUnitDropdownExpanded = true }
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedUnitLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("▼", color = AccentSteel, fontSize = 11.sp)
                                }
                                DropdownMenu(
                                    expanded = evUnitDropdownExpanded,
                                    onDismissRequest = { evUnitDropdownExpanded = false },
                                    modifier = Modifier.background(CardSlate)
                                ) {
                                    Column(modifier = Modifier.width(210.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgSlate)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "✓ $selectedUnitLabel",
                                                color = AccentSteel,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            CompactButton("✓ Bestätigen") {
                                                evUnitDropdownExpanded = false
                                            }
                                        }
                                        HorizontalDivider(color = BorderSlate)
                                        unitOptions.forEach { (uCode, uLabel) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        uLabel,
                                                        color = if (uCode == newEvUnit) AccentSteel else TextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (uCode == newEvUnit) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    newEvUnit = uCode
                                                    evUnitDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newEvRepeatChecked, onCheckedChange = { newEvRepeatChecked = it })
                        Text("Wiederholen", color = TextSecondary, fontSize = 12.sp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        val suffixText = if (newEvRepeatChecked) {
                            "🔁 Alle ${formatRhythmTextDe(parsedEvCount, newEvUnit)}"
                        } else {
                            "Einmalig"
                        }
                        Text(
                            text = "📅 Nächster Termin: $calculatedNextEvDateFormatted ($suffixText)",
                            color = if (newEvRepeatChecked) TreeGreen else AccentSteel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = BorderSlate)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompactButton("Löschen") {
                        pendingConfirmMessage = "Kontakt „${dp.name}\" wirklich unwiderruflich löschen?"
                        pendingConfirmAction = {
                            scope.launch {
                                db.deletePerson(dp)
                                detailPerson = null
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("Schließen") { detailPerson = null }
                        CompactButton("✓ Speichern") { saveDetailChanges() }
                    }
                }
            }
        }
    }

    // Dialog: Nachricht über vorhandene Kanäle (WhatsApp, SMS, Anruf, Mail + "+ Hinzufügen" für weitere Messenger)
    popupPerson?.let { p ->
        val relPairs = relations
            .filter { it.fromPersonId == p.id }
            .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
        val resolved = ContactResolver.resolveTarget(p, relPairs)
        val prefs = remember { context.getSharedPreferences("remember_me_prefs", android.content.Context.MODE_PRIVATE) }
        var enabledExtras by remember {
            mutableStateOf(
                prefs.getStringSet("enabled_extra_messengers", emptySet())?.toList().orEmpty()
            )
        }
        var showAddMessenger by remember { mutableStateOf(false) }
        val availableExtras = listOf("Telegram", "Signal", "Threema", "Messenger", "Instagram", "Viber")

        fun toggleMessenger(name: String) {
            val next = if (enabledExtras.contains(name)) enabledExtras - name else enabledExtras + name
            enabledExtras = next
            prefs.edit().putStringSet("enabled_extra_messengers", next.toSet()).apply()
        }

        Dialog(onDismissRequest = { popupPerson = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Nachricht an ${p.name}", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(popupReason, color = AccentSteel, fontSize = 13.sp)

                resolved.warningNotice?.let { warning ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3B2F1E), RoundedCornerShape(6.dp))
                            .border(1.dp, WarnAmber, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(warning, color = Color(0xFFFDE68A), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Nachricht / Gruß (zufälliger Glückwunsch)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    CompactButton("🎲 Anderer Spruch") {
                        quickMessageText = pickRandomBirthdayGreeting(
                            extractFirstName(resolved.targetPersonName),
                            false
                        )
                    }
                }
                OutlinedTextField(
                    value = quickMessageText,
                    onValueChange = { quickMessageText = it },
                    label = { Text("Nachricht / Gruß", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Haupt-Kanäle mit Symbolen: WhatsApp, SMS, Anruf (+ Mail wenn vorhanden)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChannelButton(channel = "WhatsApp", isPrimary = true) {
                        QuickActionDispatcher.openWhatsApp(context, resolved.phone, quickMessageText)
                    }
                    ChannelButton(channel = "SMS") {
                        QuickActionDispatcher.openSms(context, resolved.phone, quickMessageText)
                    }
                    ChannelButton(channel = "Anruf") {
                        QuickActionDispatcher.startPhoneCall(context, resolved.phone)
                    }
                    if (resolved.email.isNotBlank()) {
                        ChannelButton(channel = "Mail") {
                            QuickActionDispatcher.openEmail(context, resolved.email, popupReason)
                        }
                    }
                }

                // Hinzugefügte Messenger + Button "+ Hinzufügen"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    enabledExtras.forEach { extra ->
                        ChannelButton(channel = extra) {
                            QuickActionDispatcher.openExtraMessenger(context, extra, resolved.phone, quickMessageText)
                        }
                    }
                    CompactButton("＋ Hinzufügen") {
                        showAddMessenger = !showAddMessenger
                    }
                }

                if (showAddMessenger) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Weitere Messenger ein-/ausblenden:",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableExtras) { m ->
                                val active = enabledExtras.contains(m)
                                ChannelButton(
                                    channel = m,
                                    labelOverride = if (active) "✓ $m" else "+ $m",
                                    isPrimary = active
                                ) {
                                    toggleMessenger(m)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactButton("Kontaktiert") {
                        scope.launch {
                            db.updatePerson(p.copy(lastContactedIso = LocalDate.now().toString()))
                            popupPerson = null
                        }
                    }
                    CompactButton("Schließen") { popupPerson = null }
                }
            }
        }
    }

    // Dialog: Doppelte oder mehrere Kontakte intelligent zusammenführen (ohne doppelte Nummern / Mails / Adressen)
    if (showMergeContactsDialog) {
        val allCandidates = remember(persons) { persons.sortedWith(LastNamePersonComparator) }
        val allDuplicateGroups = remember(persons) { findDuplicateSuggestionGroups(persons) }
        var selectedIds by remember(mergeInitialPersonIds) { mutableStateOf(mergeInitialPersonIds.toSet()) }
        var primaryId by remember(mergeInitialPersonIds) { mutableStateOf(mergeInitialPersonIds.firstOrNull() ?: -1L) }
        var activeMergeTab by remember(mergeInitialPersonIds) {
            mutableStateOf(
                if (mergeInitialPersonIds.isEmpty() && allDuplicateGroups.isNotEmpty()) "suggestions" else "manual"
            )
        }
        val selectedPersons = remember(persons, selectedIds) {
            persons.filter { selectedIds.contains(it.id) }.sortedWith(LastNamePersonComparator)
        }
        LaunchedEffect(selectedIds, selectedPersons) {
            if (selectedPersons.isNotEmpty() && selectedPersons.none { it.id == primaryId }) {
                primaryId = selectedPersons.first().id
            } else if (selectedPersons.isEmpty()) {
                primaryId = -1L
            }
        }
        var customTargetName by remember { mutableStateOf("") }
        LaunchedEffect(primaryId) {
            val prim = persons.find { it.id == primaryId }
            if (prim != null) {
                customTargetName = prim.name
            }
        }
        var mergeInDeviceContacts by remember { mutableStateOf(true) }
        var isMerging by remember { mutableStateOf(false) }
        var lastMergedNotice by remember { mutableStateOf("") }
        var deviceDetailsMap by remember { mutableStateOf<Map<String, DeviceContactFullDetails>>(emptyMap()) }

        LaunchedEffect(selectedPersons) {
            val hasRead = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasRead && selectedPersons.isNotEmpty()) {
                val missingCids = selectedPersons
                    .mapNotNull { it.importedFromDeviceContactId?.takeIf(String::isNotBlank) }
                    .filter { !deviceDetailsMap.containsKey(it) }
                if (missingCids.isNotEmpty()) {
                    val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        missingCids.associateWith { cid ->
                            syncRepo.readDeviceContactFullDetails(context.contentResolver, cid)
                        }
                    }
                    deviceDetailsMap = deviceDetailsMap + loaded
                }
            }
        }

        val localExtrasMap = remember(selectedPersons) {
            selectedPersons.associate { p ->
                p.id to parseExtraInfosPref(appPrefs.getString("extra_infos_${p.id}", null))
            }
        }
        val preview = remember(selectedPersons, primaryId, customTargetName, localExtrasMap, deviceDetailsMap) {
            if (selectedPersons.size >= 2) {
                buildMergedContactPreview(
                    selectedPersons = selectedPersons,
                    primaryId = primaryId,
                    customName = customTargetName,
                    localExtraInfosByPersonId = localExtrasMap,
                    deviceDetailsByContactId = deviceDetailsMap
                )
            } else {
                null
            }
        }

        val combinedRelationsCount = remember(relations, selectedIds, primaryId) {
            if (selectedIds.size < 2) 0
            else {
                val targetId = if (primaryId > 0) primaryId else selectedIds.first()
                relations
                    .mapNotNull { rel ->
                        val nf = if (selectedIds.contains(rel.fromPersonId)) targetId else rel.fromPersonId
                        val nt = if (selectedIds.contains(rel.toPersonId)) targetId else rel.toPersonId
                        if (nf == nt) null
                        else if (nf == targetId || nt == targetId) "$nf:$nt:${rel.relationLabel.trim().lowercase()}"
                        else null
                    }
                    .distinct()
                    .count { it.startsWith("$targetId:") }
            }
        }
        val combinedRemindersCount = remember(specialEvents, selectedIds) {
            if (selectedIds.size < 2) 0
            else {
                specialEvents
                    .filter { selectedIds.contains(it.personId) }
                    .distinctBy { "${it.title.trim().lowercase()}:${it.dateIso.trim()}" }
                    .size
            }
        }

        fun executeMerge() {
            val currentPreview = preview ?: return
            if (selectedPersons.size < 2 || isMerging) return
            isMerging = true
            scope.launch {
                try {
                    val targetPrimary = currentPreview.primaryPerson
                    val secondaryPersons = selectedPersons.filter { it.id != targetPrimary.id }
                    val secondaryIds = secondaryPersons.map { it.id }.toSet()
                    val allWereHidden = selectedPersons.all { it.isHidden || hiddenPersonIds.contains(it.id) }

                    var mergedEntity = targetPrimary.copy(
                        name = currentPreview.finalName,
                        birthdayIso = currentPreview.birthdayIso,
                        hasNoOwnPhone = currentPreview.hasNoOwnPhone,
                        phone = currentPreview.primaryPhone,
                        email = currentPreview.primaryEmail,
                        notes = currentPreview.notes,
                        timerEnabled = currentPreview.timerEnabled,
                        timerInterval = currentPreview.timerInterval,
                        timerUnit = currentPreview.timerUnit,
                        lastContactedIso = currentPreview.lastContactedIso,
                        isHidden = allWereHidden,
                        photoBase64 = currentPreview.photoBase64
                    )

                    val secondaryDeviceCids = secondaryPersons
                        .mapNotNull { it.importedFromDeviceContactId?.takeIf(String::isNotBlank) }
                        .filter { it != targetPrimary.importedFromDeviceContactId }
                        .toSet()

                    if (secondaryDeviceCids.isNotEmpty()) {
                        val updatedIgnored = mergedAwayDeviceCids + secondaryDeviceCids
                        mergedAwayDeviceCids = updatedIgnored
                        appPrefs.edit().putStringSet("merged_away_device_cids", updatedIgnored).apply()
                    }

                    if (mergeInDeviceContacts) {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            val updatedCid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val savedCid = syncRepo.saveOrUpdateDeviceContact(
                                    contentResolver = context.contentResolver,
                                    person = mergedEntity,
                                    extraInfos = currentPreview.deduplicatedExtraInfos
                                )
                                secondaryDeviceCids
                                    .filter { it != savedCid && it != mergedEntity.importedFromDeviceContactId }
                                    .forEach { dupCid ->
                                        syncRepo.deleteDeviceContactById(context.contentResolver, dupCid)
                                    }
                                savedCid
                            }
                            if (!updatedCid.isNullOrBlank()) {
                                mergedEntity = mergedEntity.copy(importedFromDeviceContactId = updatedCid)
                            }
                        }
                    }

                    db.updatePerson(mergedEntity)

                    val prefsEditor = appPrefs.edit()
                    prefsEditor.putString(
                        "extra_infos_${targetPrimary.id}",
                        serializeExtraInfosPref(currentPreview.deduplicatedExtraInfos)
                    )
                    secondaryIds.forEach { secId ->
                        prefsEditor.remove("extra_infos_$secId")
                    }
                    val nextHiddenIds = (hiddenPersonIds - secondaryIds).let {
                        if (allWereHidden) it + targetPrimary.id else it - targetPrimary.id
                    }
                    hiddenPersonIds = nextHiddenIds
                    prefsEditor.putStringSet("hidden_person_ids", nextHiddenIds.map { it.toString() }.toSet())
                    prefsEditor.apply()

                    // Beziehungen auf Zielkontakt umhängen & deduplizieren
                    val allRels = db.getAllRelationsOnce()
                    val survivingRelKeys = allRels
                        .filter { !secondaryIds.contains(it.fromPersonId) && !secondaryIds.contains(it.toPersonId) }
                        .map { "${it.fromPersonId}:${it.toPersonId}:${it.relationLabel.trim().lowercase()}" }
                        .toMutableSet()

                    allRels
                        .filter { secondaryIds.contains(it.fromPersonId) || secondaryIds.contains(it.toPersonId) }
                        .forEach { oldRel ->
                            db.deleteRelation(oldRel)
                            val nf = if (secondaryIds.contains(oldRel.fromPersonId)) targetPrimary.id else oldRel.fromPersonId
                            val nt = if (secondaryIds.contains(oldRel.toPersonId)) targetPrimary.id else oldRel.toPersonId
                            if (nf != nt) {
                                val key = "$nf:$nt:${oldRel.relationLabel.trim().lowercase()}"
                                if (survivingRelKeys.add(key)) {
                                    db.insertRelation(
                                        oldRel.copy(
                                            id = 0,
                                            fromPersonId = nf,
                                            toPersonId = nt
                                        )
                                    )
                                }
                            }
                        }

                    // Erinnerungen / Sondertermine auf Zielkontakt umhängen & deduplizieren
                    val allEvs = db.getAllSpecialEventsOnce()
                    val survivingEvKeys = allEvs
                        .filter { !secondaryIds.contains(it.personId) }
                        .map { "${it.personId}:${it.title.trim().lowercase()}:${it.dateIso.trim()}" }
                        .toMutableSet()

                    allEvs
                        .filter { secondaryIds.contains(it.personId) }
                        .forEach { oldEv ->
                            db.deleteSpecialEvent(oldEv)
                            val key = "${targetPrimary.id}:${oldEv.title.trim().lowercase()}:${oldEv.dateIso.trim()}"
                            if (survivingEvKeys.add(key)) {
                                db.insertSpecialEvent(
                                    oldEv.copy(
                                        id = 0,
                                        personId = targetPrimary.id
                                    )
                                )
                            }
                        }

                    // Direktnachrichten übernehmen
                    secondaryIds.forEach { secId ->
                        db.reassignMessagesForPerson(secId, targetPrimary.id)
                    }

                    // Wichtel-Verknüpfungen aktualisieren
                    if (wichtelGroups.any { g -> g.participants.any { it.personId != null && secondaryIds.contains(it.personId) } }) {
                        val updatedGroups = wichtelGroups.map { g ->
                            val seenPersonIdsInGroup = mutableSetOf<Long>()
                            val remappedParts = g.participants.mapNotNull { part ->
                                val newPid = if (part.personId != null && secondaryIds.contains(part.personId)) {
                                    targetPrimary.id
                                } else {
                                    part.personId
                                }
                                if (newPid != null && !g.isDrawn && !seenPersonIdsInGroup.add(newPid)) {
                                    null
                                } else {
                                    part.copy(
                                        personId = newPid,
                                        name = if (newPid == targetPrimary.id) mergedEntity.name else part.name,
                                        phone = if (newPid == targetPrimary.id && part.phone.isBlank()) mergedEntity.phone else part.phone
                                    )
                                }
                            }
                            g.copy(participants = remappedParts)
                        }
                        saveWichtelGroups(updatedGroups)
                    }
                    if (wichtelHistory.any { h ->
                            h.pairings.any {
                                (it.giverPersonId != null && secondaryIds.contains(it.giverPersonId)) ||
                                    (it.receiverPersonId != null && secondaryIds.contains(it.receiverPersonId))
                            }
                        }
                    ) {
                        val updatedHistory = wichtelHistory.map { h ->
                            h.copy(
                                pairings = h.pairings.map { pair ->
                                    pair.copy(
                                        giverPersonId = if (pair.giverPersonId != null && secondaryIds.contains(pair.giverPersonId)) targetPrimary.id else pair.giverPersonId,
                                        receiverPersonId = if (pair.receiverPersonId != null && secondaryIds.contains(pair.receiverPersonId)) targetPrimary.id else pair.receiverPersonId
                                    )
                                }
                            )
                        }
                        saveWichtelHistory(updatedHistory)
                    }

                    // Überzählige Einzelkontakte aus lokaler Datenbank entfernen
                    secondaryPersons.forEach { secPerson ->
                        db.deletePerson(secPerson)
                    }

                    val currentRoot = selectedTreeRootId
                    if (currentRoot != null && secondaryIds.contains(currentRoot)) {
                        selectedTreeRootId = targetPrimary.id
                    }

                    val mergedCount = selectedPersons.size
                    syncStatus = "$mergedCount Kontakte zu „${mergedEntity.name}“ zusammengeführt"
                    lastMergedNotice = "✓ $mergedCount Kontakte erfolgreich zu „${mergedEntity.name}“ zusammengeführt!"

                    val remainingSuggestions = allDuplicateGroups.count { grp ->
                        grp.members.none { it.id == targetPrimary.id || secondaryIds.contains(it.id) }
                    }
                    if (mergeInitialPersonIds.isNotEmpty() || remainingSuggestions == 0) {
                        showMergeContactsDialog = false
                    } else {
                        selectedIds = emptySet()
                        primaryId = -1L
                        activeMergeTab = "suggestions"
                    }
                } finally {
                    isMerging = false
                }
            }
        }

        Dialog(
            onDismissRequest = { if (!isMerging) showMergeContactsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardSlate, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fixierte Kopfzeile oben
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔗 Kontakte zusammenführen",
                            color = TextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactButton("✕") {
                                if (!isMerging) showMergeContactsDialog = false
                            }
                            if (preview != null) {
                                CompactButton(if (isMerging) "⏳..." else "✓ Zusammenführen") {
                                    executeMerge()
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSlate)

                    if (lastMergedNotice.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TreeGreen.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .border(1.dp, TreeGreen, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = lastMergedNotice,
                                color = TreeGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Modus-Umschalter: Automatische Duplikat-Vorschläge vs. Manuelle Mehrfachauswahl
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactButton(
                            label = if (activeMergeTab == "suggestions") {
                                "✓ 🔍 Vorschläge (${allDuplicateGroups.size})"
                            } else {
                                "🔍 Vorschläge (${allDuplicateGroups.size})"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            activeMergeTab = "suggestions"
                        }
                        CompactButton(
                            label = if (activeMergeTab == "manual") {
                                "✓ ☑️ Manuell & Vorschau (${selectedIds.size})"
                            } else {
                                "☑️ Manuell & Vorschau (${selectedIds.size})"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            activeMergeTab = "manual"
                        }
                    }

                    if (activeMergeTab == "suggestions") {
                        if (allDuplicateGroups.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(BgSlate, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Keine automatischen Duplikate gefunden 🎉",
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Du kannst jederzeit über „☑️ Manuell & Vorschau“ beliebige 2 oder mehr Kontakte auswählen und deren Nummern, E-Mails, Adressen, Termine und Beziehungen ohne doppelte Einträge in einem Kontakt bündeln.",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    CompactButton("☑️ Beliebige Kontakte manuell auswählen") {
                                        activeMergeTab = "manual"
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Tippe bei einer erkannten Gruppe auf „Prüfen →“, um die bereinigte Vorschau zu sehen und sie zusammenzuführen:",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allDuplicateGroups, key = { it.key }) { grp ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BgSlate, RoundedCornerShape(10.dp))
                                            .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedIds = grp.members.map { it.id }.toSet()
                                                primaryId = grp.members.first().id
                                                activeMergeTab = "manual"
                                            }
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${grp.reasonBadge} • ${grp.members.size} Kontakte",
                                                color = AccentSteel,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            CompactButton("Prüfen →") {
                                                selectedIds = grp.members.map { it.id }.toSet()
                                                primaryId = grp.members.first().id
                                                activeMergeTab = "manual"
                                            }
                                        }
                                        grp.members.forEach { m ->
                                            val subParts = buildList {
                                                if (!m.birthdayIso.isNullOrBlank()) add("🎂 ${formatIsoToDdMmYyyy(m.birthdayIso)}")
                                                if (m.phone.isNotBlank()) add("📞 ${m.phone}")
                                                if (m.email.isNotBlank()) add("✉️ ${m.email}")
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                PersonAvatar(name = m.name, photoBase64 = m.photoBase64, sizeDp = 28)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = formatLastNameFirst(m.name),
                                                        color = TextPrimary,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (subParts.isNotEmpty()) {
                                                        Text(
                                                            text = subParts.joinToString("  •  "),
                                                            color = TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1. Kontakte zum Zusammenführen wählen (mind. 2):",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            ContactSearchDropdown(
                                persons = allCandidates,
                                onSelectPerson = { p ->
                                    val next = if (selectedIds.contains(p.id)) selectedIds - p.id else selectedIds + p.id
                                    selectedIds = next
                                    if (primaryId <= 0L && next.isNotEmpty()) primaryId = p.id
                                },
                                placeholderText = if (selectedIds.isEmpty()) {
                                    "🔎 Kontakte per Suche / ABCD ankreuzen"
                                } else {
                                    "☑️ ${selectedIds.size} Kontakte ausgewählt – weitere hinzufügen"
                                },
                                confirmButtonLabel = "✓ Auswahl übernehmen",
                                showInlineAlphabet = true,
                                checkedPersonIds = selectedIds,
                                onToggleCheckedPerson = { p ->
                                    val next = if (selectedIds.contains(p.id)) selectedIds - p.id else selectedIds + p.id
                                    selectedIds = next
                                    if (primaryId <= 0L && next.isNotEmpty()) primaryId = p.id
                                }
                            )

                            if (selectedPersons.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "2. Ausgewählte Kontakte (${selectedPersons.size}) – Hauptkontakt bestimmen:",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactButton("Auswahl leeren") {
                                        selectedIds = emptySet()
                                        primaryId = -1L
                                    }
                                }

                                selectedPersons.forEach { sp ->
                                    val isMain = sp.id == primaryId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isMain) AccentSteel.copy(alpha = 0.12f) else BgSlate,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isMain) AccentSteel else BorderSlate,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { primaryId = sp.id }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            PersonAvatar(name = sp.name, photoBase64 = sp.photoBase64, sizeDp = 30)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = formatLastNameFirst(sp.name),
                                                    color = TextPrimary,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                val detailsLine = buildList {
                                                    if (!sp.birthdayIso.isNullOrBlank()) add("🎂 ${formatIsoToDdMmYyyy(sp.birthdayIso)}")
                                                    if (sp.phone.isNotBlank()) add("📞 ${sp.phone}")
                                                    if (sp.email.isNotBlank()) add("✉️ ${sp.email}")
                                                }.joinToString(" • ")
                                                if (detailsLine.isNotBlank()) {
                                                    Text(
                                                        text = detailsLine,
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CompactButton(if (isMain) "⭐ Haupt" else "Als Haupt") {
                                                primaryId = sp.id
                                            }
                                            Text(
                                                text = "✕",
                                                color = WarnAmber,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable {
                                                        selectedIds = selectedIds - sp.id
                                                    }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (selectedPersons.size < 2) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgSlate, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = if (selectedPersons.size == 1) {
                                            "Noch mindestens 1 weiteren Kontakt auswählen, um die Informationen mit „${formatLastNameFirst(selectedPersons.first().name)}“ zusammenzuführen."
                                        } else {
                                            "Wähle oben mindestens 2 Kontakte aus. Doppelte Telefonnummern (auch mit unterschiedlichen Leerzeichen, Bindestrichen oder +49), E-Mail-Adressen, Postadressen und Notizen werden automatisch erkannt und jeweils nur ein einziges Mal übernommen."
                                        },
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            preview?.let { pv ->
                                HorizontalDivider(color = BorderSlate)
                                Text(
                                    text = "3. Bereinigte Vorschau des zusammengeführten Kontakts:",
                                    color = AccentSteel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (pv.removedDuplicatesCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(TreeGreen.copy(alpha = 0.13f), RoundedCornerShape(8.dp))
                                            .border(1.dp, TreeGreen, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "✨ ${pv.removedDuplicatesCount} doppelte Einträge (Nummern/Mails/Adressen/Notizen) erkannt & automatisch auf je 1 Eintrag bereinigt!",
                                            color = TreeGreen,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = customTargetName,
                                    onValueChange = { customTargetName = it },
                                    label = { Text("Ziel-Name des zusammengeführten Kontakts") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgSlate, RoundedCornerShape(10.dp))
                                        .border(1.dp, AccentSteel, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        PersonAvatar(name = pv.finalName, photoBase64 = pv.photoBase64, sizeDp = 40)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = formatLastNameFirst(pv.finalName),
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (!pv.birthdayIso.isNullOrBlank()) {
                                                Text(
                                                    text = "🎂 Geburtstag: ${formatIsoToDdMmYyyy(pv.birthdayIso)}",
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    if (pv.primaryPhone.isNotBlank()) {
                                        Text(
                                            text = "📞 Hauptnummer: ${pv.primaryPhone}",
                                            color = TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (pv.primaryEmail.isNotBlank()) {
                                        Text(
                                            text = "✉️ Haupt-E-Mail: ${pv.primaryEmail}",
                                            color = TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    pv.deduplicatedExtraInfos.forEach { (lbl, value) ->
                                        val icon = when {
                                            ContactDeduplicator.isPhoneLike(value) -> "📞"
                                            ContactDeduplicator.isEmailLike(value) -> "✉️"
                                            ContactDeduplicator.isAddressLabel(lbl) -> "🏠"
                                            else -> "ℹ️"
                                        }
                                        Text(
                                            text = "$icon $lbl: $value",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (pv.notes.isNotBlank()) {
                                        Text(
                                            text = "📝 Notiz: ${pv.notes}",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (combinedRelationsCount > 0 || combinedRemindersCount > 0 || pv.timerEnabled) {
                                        val metaItems = buildList {
                                            if (combinedRelationsCount > 0) add("🔗 $combinedRelationsCount Beziehungen")
                                            if (combinedRemindersCount > 0) add("🔔 $combinedRemindersCount Erinnerungen")
                                            if (pv.timerEnabled) add("⏳ Melde-Timer aktiv")
                                        }
                                        Text(
                                            text = metaItems.joinToString("  •  "),
                                            color = AccentSteel,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { mergeInDeviceContacts = !mergeInDeviceContacts }
                                ) {
                                    Checkbox(
                                        checked = mergeInDeviceContacts,
                                        onCheckedChange = { mergeInDeviceContacts = it }
                                    )
                                    Text(
                                        text = "Auch in Handykontakten zusammenführen (Zielkontakt aktualisieren & Duplikate entfernen)",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSlate)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton("Schließen") {
                            if (!isMerging) showMergeContactsDialog = false
                        }
                        if (preview != null) {
                            CompactButton(
                                if (isMerging) "⏳ Wird zusammengeführt..."
                                else "✓ ${selectedPersons.size} Kontakte jetzt zusammenführen"
                            ) {
                                executeMerge()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Automatischer Stammbaum-Generator und visuelle Baum-Ansicht.
 * Berechnet aus allen Beziehungen (Kind von, Ehe-Partner von, Bruder von, Mutter/Vater von, Oma/Opa von ...)
 * die Generationen-Ebenen rund um die gewählte Fokus-Person sowie alle verbundenen Familienzweige.
 */
@Composable
fun FamilyTreeScreen(
    persons: List<PersonEntity>,
    relations: List<RelationEntity>,
    selectedRootId: Long?,
    onSelectRoot: (Long) -> Unit,
    onOpenDetails: (PersonEntity) -> Unit,
    onOpenMessage: (PersonEntity) -> Unit
) {
    if (persons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSlate, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text("Lege Kontakte und Beziehungen an, um den Stammbaum zu sehen.", color = TextSecondary, fontSize = 12.sp)
        }
        return
    }

    val sortedPersons = remember(persons) { persons.sortedByLastName() }
    val rootPerson = sortedPersons.find { it.id == selectedRootId } ?: sortedPersons.first()
    val personMap = remember(sortedPersons) { sortedPersons.associateBy { it.id } }

    // Berechne relative Generationen-Ebenen per Breitensuche (BFS) ab rootPerson
    val generationByPersonId = remember(persons, relations, rootPerson.id) {
        val genMap = mutableMapOf<Long, Int>()
        val queue = ArrayDeque<Long>()
        genMap[rootPerson.id] = 0
        queue.add(rootPerson.id)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            val currentGen = genMap[currentId] ?: 0

            // Ausgehende Beziehungen von currentId
            relations.filter { it.fromPersonId == currentId }.forEach { rel ->
                if (!genMap.containsKey(rel.toPersonId) && personMap.containsKey(rel.toPersonId)) {
                    val step = relationGenerationStep(rel.relationLabel)
                    genMap[rel.toPersonId] = currentGen + step
                    queue.add(rel.toPersonId)
                }
            }
            // Eingehende Beziehungen zu currentId
            relations.filter { it.toPersonId == currentId }.forEach { rel ->
                if (!genMap.containsKey(rel.fromPersonId) && personMap.containsKey(rel.fromPersonId)) {
                    val step = -relationGenerationStep(rel.relationLabel)
                    genMap[rel.fromPersonId] = currentGen + step
                    queue.add(rel.fromPersonId)
                }
            }
        }
        genMap
    }

    fun shortRelationName(rawLabel: String): String {
        val l = rawLabel.trim().lowercase()
        return when {
            l.startsWith("mutter") -> "Mutter"
            l.startsWith("vater") -> "Vater"
            l.startsWith("elternteil") || l.startsWith("eltern") -> "Eltern"
            l.startsWith("ehe-partner") || l.startsWith("ehepartner") -> "Ehe-Partner"
            l.startsWith("partner") -> "Partner"
            l.startsWith("bruder") -> "Bruder"
            l.startsWith("schwester") -> "Schwester"
            l.startsWith("geschwister") -> "Geschwister"
            l.startsWith("kind") -> "Kind"
            l.startsWith("oma") || l.startsWith("opa") || l.startsWith("großelter") -> "Oma/Opa"
            l.startsWith("enkel") -> "Enkelkind"
            l.startsWith("onkel") || l.startsWith("tante") -> "Onkel/Tante"
            l.startsWith("neffe") || l.startsWith("nichte") -> "Neffe/Nichte"
            l.startsWith("cousin") -> "Cousin/Cousine"
            else -> rawLabel.replace(Regex("\\s+von$", RegexOption.IGNORE_CASE), "").trim().ifBlank { "Familie" }
        }
    }

    // Kurze Rolle jeder Person relativ zur Fokus-Person ermitteln (z.B. Eltern, Bruder, Kind)
    fun roleRelativeToRoot(p: PersonEntity, genLevel: Int): String {
        if (p.id == rootPerson.id) return "Fokus"
        val directOut = relations.find { it.fromPersonId == p.id && it.toPersonId == rootPerson.id }
        if (directOut != null) return shortRelationName(directOut.relationLabel)
        val directIn = relations.find { it.fromPersonId == rootPerson.id && it.toPersonId == p.id }
        if (directIn != null) return shortRelationName(inverseRelationLabel(directIn.relationLabel))

        if (genLevel <= -2) return "Oma/Opa"
        if (genLevel >= 2) return "Enkelkind"
        if (genLevel == -1) {
            val relToParent = relations.find { r ->
                r.fromPersonId == p.id && generationByPersonId[r.toPersonId] == -1 &&
                    relations.any { pr ->
                        (pr.fromPersonId == r.toPersonId && pr.toPersonId == rootPerson.id && relationGenerationStep(pr.relationLabel) == 1) ||
                            (pr.fromPersonId == rootPerson.id && pr.toPersonId == r.toPersonId && relationGenerationStep(pr.relationLabel) == -1)
                    }
            }
            if (relToParent != null && isPartnerRelation(relToParent.relationLabel)) return "Eltern"
            if (relToParent != null && isSiblingRelation(relToParent.relationLabel)) return "Onkel/Tante"
            return "Eltern"
        }
        if (genLevel == 1) return "Kind"

        val anyRel = relations.firstOrNull { it.fromPersonId == p.id }
        if (anyRel != null) return shortRelationName(anyRel.relationLabel)
        return "Familie"
    }

    fun arePartnersOrCoParents(idA: Long, idB: Long): Boolean {
        val directRel = relations.find {
            (it.fromPersonId == idA && it.toPersonId == idB) ||
                (it.fromPersonId == idB && it.toPersonId == idA)
        }
        if (directRel != null && isPartnerRelation(directRel.relationLabel)) return true
        val childrenOfA = relations
            .filter { it.toPersonId == idA && relationGenerationStep(it.relationLabel) == -1 }
            .map { it.fromPersonId }
        return childrenOfA.any { cid ->
            relations.any { it.fromPersonId == cid && it.toPersonId == idB && relationGenerationStep(it.relationLabel) == -1 }
        }
    }

    // Sortiert Mitglieder einer Ebene:
    // - Auf Ebene 0: Fokus-Person zentriert in der Mitte (Geschwister links, Partner rechts)
    // - Auf anderen Ebenen: Eltern / Partner direkt nebeneinander
    fun orderRowMembers(members: List<PersonEntity>, genLevel: Int): List<PersonEntity> {
        if (genLevel == 0 && members.any { it.id == rootPerson.id }) {
            val others = members.filter { it.id != rootPerson.id }
            val partners = others.filter { arePartnersOrCoParents(rootPerson.id, it.id) }
            val nonPartners = others.filter { !arePartnersOrCoParents(rootPerson.id, it.id) }
            return if (partners.isNotEmpty()) {
                nonPartners + listOf(rootPerson) + partners
            } else {
                val half = (nonPartners.size + 1) / 2
                nonPartners.take(half) + listOf(rootPerson) + nonPartners.drop(half)
            }
        }

        val visited = mutableSetOf<Long>()
        val ordered = mutableListOf<PersonEntity>()
        val sortedSeed = members.sortedWith { a, b ->
            val aRole = roleRelativeToRoot(a, genLevel)
            val bRole = roleRelativeToRoot(b, genLevel)
            val aIsParent = aRole == "Eltern" || aRole == "Mutter" || aRole == "Vater"
            val bIsParent = bRole == "Eltern" || bRole == "Mutter" || bRole == "Vater"
            when {
                aIsParent && !bIsParent -> -1
                !aIsParent && bIsParent -> 1
                else -> LastNamePersonComparator.compare(a, b)
            }
        }
        for (m in sortedSeed) {
            if (!visited.add(m.id)) continue
            ordered.add(m)
            for (candidate in members) {
                if (!visited.contains(candidate.id) && arePartnersOrCoParents(m.id, candidate.id)) {
                    visited.add(candidate.id)
                    ordered.add(candidate)
                }
            }
        }
        return ordered
    }

    val levels = generationByPersonId.entries
        .groupBy({ it.value }, { personMap[it.key] })
        .mapValues { entry -> orderRowMembers(entry.value.filterNotNull(), entry.key) }
        .toSortedMap()

    val maxRowCount = remember(levels) { levels.values.maxOfOrNull { it.size } ?: 1 }
    val levelCount = levels.size

    var dropdownExpanded by remember { mutableStateOf(false) }
    var treeZoom by remember { mutableStateOf(1.0f) }
    var treePanX by remember { mutableStateOf(0f) }
    var treePanY by remember { mutableStateOf(0f) }
    val zoomPct = (treeZoom * 100f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Obere Steuerleiste: Kontakt-Auswahl per Dropdown mit ABCD-Schnellwahl (nach Nachname sortiert) + Zoom-Funktion
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSlate, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "Fokus-Kontakt im Stammbaum (Nachname, Vorname):",
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            ContactSearchDropdown(
                persons = sortedPersons,
                selectedPersonId = rootPerson.id,
                onSelectPerson = { p -> onSelectRoot(p.id) },
                showInlineAlphabet = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔍 Zoom & ✋ Ziehen:",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactButton("➖") {
                        treeZoom = (treeZoom - 0.15f).coerceIn(0.35f, 2.2f)
                    }
                    CompactButton("$zoomPct%") {
                        treeZoom = 1.0f
                        treePanX = 0f
                        treePanY = 0f
                    }
                    CompactButton("➕") {
                        treeZoom = (treeZoom + 0.15f).coerceIn(0.35f, 2.2f)
                    }
                    CompactButton("⛶ Passend") {
                        val widthScale = if (maxRowCount > 2) 2.9f / maxRowCount else 1.0f
                        val heightScale = if (levelCount > 4) 4.0f / levelCount else 1.0f
                        treeZoom = minOf(1.0f, widthScale, heightScale).coerceIn(0.4f, 1.0f)
                        treePanX = 0f
                        treePanY = 0f
                    }
                }
            }
        }

        // Zoombarer & frei verschiebbarer (2D-Pan) visueller Stammbaum
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardSlate, RoundedCornerShape(10.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    treeZoom = (treeZoom * zoomChange).coerceIn(0.35f, 2.2f)
                                    treePanX += panChange.x
                                    treePanY += panChange.y
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (pressedCount == 1) {
                                val panChange = event.calculatePan()
                                if (panChange != Offset.Zero) {
                                    treePanX += panChange.x
                                    treePanY += panChange.y
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.graphicsLayer(
                    translationX = treePanX,
                    translationY = treePanY
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val levelKeys = levels.keys.toList()
                val z = treeZoom
                val nodeMinW = (76f * z).coerceAtLeast(32f).dp
                val nodeMaxW = (155f * z).coerceAtLeast(64f).dp
                val padH = (8f * z).coerceAtLeast(3f).dp
                val padV = (5f * z).coerceAtLeast(2f).dp
                val nameFontSize = (12f * z).coerceIn(6.5f, 24f).sp
                val nameLineHeight = (14f * z).coerceIn(7.5f, 28f).sp
                val roleFontSize = (10f * z).coerceIn(5.5f, 20f).sp
                val roleLineHeight = (12f * z).coerceIn(6.5f, 24f).sp
                val connFontSize = (11f * z).coerceIn(6f, 22f).sp
                val rowSpacing = (4f * z).coerceAtLeast(2f).dp
                val cornerRad = (6f * z).coerceAtLeast(3f).dp

                levelKeys.forEachIndexed { idx, genLevel ->
                    val members = levels[genLevel].orEmpty()

                    // Knoten dieser Generation nebeneinander
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rowSpacing, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        members.forEachIndexed { mIdx, member ->
                            val isRoot = member.id == rootPerson.id
                            val roleText = roleRelativeToRoot(member, genLevel)
                            val isPartner = isPartnerRelation(roleText)

                            Column(
                                modifier = Modifier
                                    .widthIn(min = nodeMinW, max = nodeMaxW)
                                    .background(
                                        if (isRoot) RootNodeBg else BgSlate,
                                        RoundedCornerShape(cornerRad)
                                    )
                                    .border(
                                        width = if (isRoot) 1.5.dp else 1.dp,
                                        color = when {
                                            isRoot -> AccentSteel
                                            isPartner -> PartnerRose
                                            else -> BorderSlate
                                        },
                                        shape = RoundedCornerShape(cornerRad)
                                    )
                                    .clickable { onOpenDetails(member) }
                                    .padding(horizontal = padH, vertical = padV),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (member.photoBase64.isNotBlank()) {
                                    PersonAvatar(
                                        name = member.name,
                                        photoBase64 = member.photoBase64,
                                        sizeDp = (22f * z).roundToInt().coerceAtLeast(14)
                                    )
                                    Spacer(modifier = Modifier.height((2f * z).coerceAtLeast(1f).dp))
                                }
                                Text(
                                    text = member.name,
                                    color = TextPrimary,
                                    fontSize = nameFontSize,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = nameLineHeight
                                )
                                Text(
                                    text = roleText,
                                    color = when {
                                        isRoot -> AccentSteel
                                        isPartner -> PartnerRose
                                        else -> TreeGreen
                                    },
                                    fontSize = roleFontSize,
                                    textAlign = TextAlign.Center,
                                    lineHeight = roleLineHeight
                                )
                            }

                            // Horizontale Verbindung zwischen Eltern / Ehe-Partnern nebeneinander
                            if (mIdx < members.size - 1) {
                                val nextMember = members[mIdx + 1]
                                val coupled = arePartnersOrCoParents(member.id, nextMember.id)
                                if (coupled) {
                                    Text(
                                        text = "─❤️─",
                                        color = PartnerRose,
                                        fontSize = connFontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width((2f * z).coerceAtLeast(1f).dp))
                                }
                            }
                        }
                    }

                    // Vertikale Stammbaum-Verbindungslinie zu den Kindern darunter
                    if (idx < levelKeys.size - 1) {
                        val nextMembers = levels[levelKeys[idx + 1]].orEmpty()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = (2f * z).coerceAtLeast(1f).dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width((2f * z).coerceAtLeast(1.5f).dp)
                                    .height((14f * z).coerceAtLeast(6f).dp)
                                    .background(AccentSteel)
                            )
                            if (nextMembers.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .width((96f * z).coerceAtLeast(40f).dp)
                                        .height((2f * z).coerceAtLeast(1.5f).dp)
                                        .background(AccentSteel)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Nicht verbundene Kontakte anzeigen, falls vorhanden (nach Nachname sortiert + Dropdown mit ABCD)
        val unconnected = remember(sortedPersons, generationByPersonId) {
            sortedPersons.filter { !generationByPersonId.containsKey(it.id) }
        }
        if (unconnected.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Weitere Kontakte (${unconnected.size}, noch nicht mit ${formatLastNameFirst(rootPerson.name)} verknüpft):",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Wähle per Dropdown-Liste (ABCD) oder tippe auf einen Namen, um eine Beziehung hinzuzufügen:",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                ContactSearchDropdown(
                    persons = unconnected,
                    selectedPersonId = null,
                    onSelectPerson = { up -> onOpenDetails(up) },
                    placeholderText = "🔎 Nicht verknüpften Kontakt auswählen",
                    showInlineAlphabet = true
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(unconnected, key = { it.id }) { up ->
                        Box(
                            modifier = Modifier
                                .background(BgSlate, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                .clickable { onOpenDetails(up) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatLastNameFirst(up.name),
                                color = AccentSteel,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                style = CenteredPillTextStyle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelButton(
    channel: String,
    labelOverride: String? = null,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(if (isPrimary) Color(0xFF0284C7) else BtnBg, RoundedCornerShape(6.dp))
            .border(1.dp, if (isPrimary) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        ChannelSymbolIcon(channel)
        Text(
            text = labelOverride ?: channel,
            color = if (isPrimary) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = CenteredPillTextStyle,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun ChannelSymbolIcon(channel: String) {
    val iconColor = when (channel) {
        "WhatsApp" -> Color(0xFF25D366)
        "SMS" -> Color(0xFF38BDF8)
        "Anruf" -> Color(0xFF22C55E)
        "Mail" -> Color(0xFFF59E0B)
        "Telegram" -> Color(0xFF2AABEE)
        "Signal" -> Color(0xFF3A76F0)
        "Threema" -> Color(0xFF05A63F)
        "Messenger" -> Color(0xFF00B2FF)
        "Instagram" -> Color(0xFFE1306C)
        "Viber" -> Color(0xFF7360F2)
        else -> AccentSteel
    }

    Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (channel) {
            "WhatsApp", "Signal", "Viber" -> {
                // Sprechblasen-Symbol
                drawCircle(color = iconColor, radius = w * 0.42f, center = Offset(w * 0.52f, h * 0.48f), style = stroke)
                val tail = Path().apply {
                    moveTo(w * 0.25f, h * 0.72f)
                    lineTo(w * 0.12f, h * 0.92f)
                    lineTo(w * 0.42f, h * 0.82f)
                }
                drawPath(tail, color = iconColor, style = stroke)
            }
            "SMS" -> {
                // Eckige Nachrichtenblase
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(w * 0.1f, h * 0.15f),
                    size = Size(w * 0.8f, h * 0.58f),
                    style = stroke
                )
                drawLine(iconColor, Offset(w * 0.25f, h * 0.73f), Offset(w * 0.15f, h * 0.92f), strokeWidth = 2.2f)
            }
            "Anruf" -> {
                // Telefonhörer-Symbol
                val phonePath = Path().apply {
                    moveTo(w * 0.2f, h * 0.25f)
                    lineTo(w * 0.38f, h * 0.25f)
                    lineTo(w * 0.45f, h * 0.45f)
                    lineTo(w * 0.35f, h * 0.55f)
                    lineTo(w * 0.45f, h * 0.65f)
                    lineTo(w * 0.55f, h * 0.55f)
                    lineTo(w * 0.75f, h * 0.62f)
                    lineTo(w * 0.75f, h * 0.8f)
                    lineTo(w * 0.5f, h * 0.8f)
                    lineTo(w * 0.2f, h * 0.5f)
                    close()
                }
                drawPath(phonePath, color = iconColor, style = stroke)
            }
            "Mail" -> {
                // Briefumschlag-Symbol
                drawRect(
                    color = iconColor,
                    topLeft = Offset(w * 0.1f, h * 0.22f),
                    size = Size(w * 0.8f, h * 0.56f),
                    style = stroke
                )
                val flap = Path().apply {
                    moveTo(w * 0.1f, h * 0.25f)
                    lineTo(w * 0.5f, h * 0.55f)
                    lineTo(w * 0.9f, h * 0.25f)
                }
                drawPath(flap, color = iconColor, style = stroke)
            }
            "Telegram" -> {
                // Papierflieger-Symbol
                val plane = Path().apply {
                    moveTo(w * 0.9f, h * 0.15f)
                    lineTo(w * 0.1f, h * 0.5f)
                    lineTo(w * 0.45f, h * 0.62f)
                    lineTo(w * 0.65f, h * 0.88f)
                    close()
                }
                drawPath(plane, color = iconColor, style = stroke)
            }
            else -> {
                drawCircle(color = iconColor, radius = w * 0.4f, style = stroke)
            }
        }
    }
}

@Composable
fun DateInputWithCalendar(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    fun showCalendar() {
        val iso = normalizeDateInputToIso(value)
        val parsed = runCatching {
            val clean = if (iso.startsWith("--")) "${LocalDate.now().year}-${iso.removePrefix("--")}" else iso
            LocalDate.parse(clean)
        }.getOrElse { LocalDate.now() }

        DatePickerDialog(
            context,
            { _, y, m, d ->
                val dayStr = d.toString().padStart(2, '0')
                val monthStr = (m + 1).toString().padStart(2, '0')
                onValueChange("$dayStr.$monthStr.$y")
            },
            parsed.year,
            parsed.monthValue - 1,
            parsed.dayOfMonth
        ).show()
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showCalendar()
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        interactionSource = interactionSource,
        trailingIcon = {
            Text(
                "📅",
                fontSize = 15.sp,
                modifier = Modifier
                    .clickable { showCalendar() }
                    .padding(6.dp)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun WichtelScreen(
    groups: List<WichtelGroup>,
    history: List<WichtelHistoryEntry>,
    persons: List<PersonEntity>,
    relations: List<RelationEntity>,
    onSaveGroups: (List<WichtelGroup>) -> Unit,
    onSaveHistory: (List<WichtelHistoryEntry>) -> Unit,
    onConfirmDelete: (String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val mottos = listOf(
        "🎁 Klassisches Wichteln",
        "♻️ Schrottwichteln",
        "📖 Buch-Wichteln",
        "🛠️ Selbstgemachtes",
        "🎲 Würfel-/Räuberwichteln"
    )
    var selectedGroupId by remember(groups) {
        mutableStateOf(groups.firstOrNull()?.id)
    }

    // Dialog-States
    var editingGroup by remember { mutableStateOf<WichtelGroup?>(null) }
    var isCreatingGroup by remember { mutableStateOf(false) }
    var isReusingHistory by remember { mutableStateOf(false) }
    var checklistGroup by remember { mutableStateOf<WichtelGroup?>(null) }
    var editingParticipantGroup by remember { mutableStateOf<WichtelGroup?>(null) }
    var editingParticipant by remember { mutableStateOf<WichtelParticipant?>(null) }
    var secretPeekPair by remember { mutableStateOf<Triple<WichtelGroup, WichtelParticipant, WichtelParticipant>?>(null) }
    var anonQuestionTarget by remember { mutableStateOf<Pair<WichtelGroup, WichtelParticipant>?>(null) }
    var showImportHistoryDialog by remember { mutableStateOf(false) }
    var showDecodeSecretDialog by remember { mutableStateOf(false) }

    val activeGroup = groups.find { it.id == selectedGroupId } ?: groups.firstOrNull()

    fun isGroupRevealed(g: WichtelGroup): Boolean {
        if (g.forceRevealed) return true
        val todayStr = LocalDate.now().toString()
        return g.revealDateIso.isNotBlank() && todayStr >= g.revealDateIso
    }

    fun getHistoricalTargetsForParticipant(part: WichtelParticipant): List<Pair<Int, String>> {
        val normName = part.name.trim().lowercase()
        val result = mutableListOf<Pair<Int, String>>()
        for (h in history) {
            for (pr in h.pairings) {
                val matchById = part.personId != null && pr.giverPersonId != null && part.personId == pr.giverPersonId
                val matchByName = pr.giverName.trim().lowercase() == normName
                if (matchById || matchByName) {
                    result.add(h.year to pr.receiverName)
                }
            }
        }
        return result
    }

    fun hasDrawnTargetInHistory(giver: WichtelParticipant, receiver: WichtelParticipant): Boolean {
        val giverNorm = giver.name.trim().lowercase()
        val recvNorm = receiver.name.trim().lowercase()
        for (h in history) {
            for (pr in h.pairings) {
                val giverMatch = (giver.personId != null && pr.giverPersonId != null && giver.personId == pr.giverPersonId) ||
                    pr.giverName.trim().lowercase() == giverNorm
                val recvMatch = (receiver.personId != null && pr.receiverPersonId != null && receiver.personId == pr.receiverPersonId) ||
                    pr.receiverName.trim().lowercase() == recvNorm
                if (giverMatch && recvMatch) return true
            }
        }
        return false
    }

    fun archiveGroupToHistory(g: WichtelGroup): WichtelHistoryEntry? {
        if (!g.isDrawn) return null
        val yearMatch = Regex("20\\d{2}").find("${g.groupName} ${g.revealDateIso}")
        val year = yearMatch?.value?.toIntOrNull() ?: LocalDate.now().year
        val pairings = g.participants.mapNotNull { p ->
            val t = g.participants.find { it.id == p.drawnTargetId } ?: return@mapNotNull null
            WichtelHistoryPair(
                giverName = p.name,
                giverPersonId = p.personId,
                giverPhone = p.phone,
                receiverName = t.name,
                receiverPersonId = t.personId,
                wishText = p.wishText,
                noGos = p.noGos,
                address = p.address,
                wishlistUrl = p.wishlistUrl
            )
        }
        val existingIdx = history.indexOfFirst {
            it.groupName.equals(g.groupName, ignoreCase = true) && it.year == year
        }
        val entry = WichtelHistoryEntry(
            id = if (existingIdx >= 0) history[existingIdx].id else System.currentTimeMillis(),
            year = year,
            groupName = g.groupName,
            adminName = g.adminName,
            motto = g.motto,
            budget = g.budget,
            completedDateIso = g.revealDateIso.ifBlank { LocalDate.now().toString() },
            pairings = pairings
        )
        val nextHistory = if (existingIdx >= 0) {
            history.mapIndexed { idx, item -> if (idx == existingIdx) entry else item }
        } else {
            listOf(entry) + history
        }
        onSaveHistory(nextHistory)
        return entry
    }

    fun shareHistoryEntry(entry: WichtelHistoryEntry) {
        val payloadObj = JSONObject().apply {
            put("groupName", entry.groupName)
            put("year", entry.year)
            put("adminName", entry.adminName)
            put("motto", entry.motto)
            put("budget", entry.budget)
            put("completedDateIso", entry.completedDateIso)
            val arr = JSONArray()
            entry.pairings.forEach { pr ->
                arr.put(
                    JSONObject().apply {
                        put("giverName", pr.giverName)
                        put("receiverName", pr.receiverName)
                        put("phone", pr.giverPhone)
                        put("wishText", pr.wishText)
                        put("wishlistUrl", pr.wishlistUrl)
                        put("noGos", pr.noGos)
                        put("address", pr.address)
                    }
                )
            }
            put("pairings", arr)
        }
        val msg = buildString {
            append("📜🎄 *Wichtel-Historie abgeschlossen: ${entry.groupName} (${entry.year})*\n")
            append("👑 Admin: ${entry.adminName} • ${entry.motto} • Budget: ${entry.budget}\n\n")
            append("🎁 *Auflösung – Wer hat wen beschenkt:*\n")
            entry.pairings.forEach { p ->
                append("• ${p.giverName} ➔ ${p.receiverName}\n")
            }
            append("\n♻️ *Für deine eigene Wichtel-Historie in Remember Me:*\n")
            append("Jeder Teilnehmer kann diesen Eintrag unter „🎄 Wichteln ➔ 📜 Wichtel-Historie ➔ 📥 Importieren“ einfügen und künftig selbst als Admin wiederverwenden (inkl. „Keine Wiederholungen“), einsehen oder löschen:\n\n")
            append("WICHTEL_HISTORY_JSON:$payloadObj")
        }
        runCatching {
            val url = "https://wa.me/?text=${android.net.Uri.encode(msg)}"
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }

    fun reuseHistoryEntry(entry: WichtelHistoryEntry) {
        val nextYear = maxOf(LocalDate.now().year, entry.year + 1)
        val baseName = entry.groupName.replace(Regex("\\b20\\d{2}\\b"), "").trim().ifBlank { "Familien-Wichteln" }
        val newParticipants = entry.pairings.mapIndexed { idx, pr ->
            val matchedPerson = persons.find { p ->
                (pr.giverPersonId != null && p.id == pr.giverPersonId) ||
                    p.name.equals(pr.giverName.trim(), ignoreCase = true)
            }
            WichtelParticipant(
                id = System.currentTimeMillis() + idx + 10L,
                personId = matchedPerson?.id ?: pr.giverPersonId,
                name = matchedPerson?.name ?: pr.giverName,
                phone = matchedPerson?.phone?.ifBlank { pr.giverPhone } ?: pr.giverPhone,
                wishText = pr.wishText,
                wishlistUrl = pr.wishlistUrl,
                noGos = pr.noGos,
                address = pr.address
            )
        }
        isCreatingGroup = true
        isReusingHistory = true
        editingGroup = WichtelGroup(
            id = System.currentTimeMillis(),
            groupName = "$baseName $nextYear",
            adminName = entry.adminName.ifBlank { "Ich (Admin)" },
            motto = entry.motto.ifBlank { "🎁 Klassisches Wichteln" },
            budget = entry.budget.ifBlank { "25 €" },
            revealDateIso = "$nextYear-12-24",
            reminderDaysBefore = 7,
            excludeCouples = true,
            excludeLastYear = true,
            isDrawn = false,
            forceRevealed = false,
            participants = newParticipants
        )
    }

    fun getCouplePartnerForPerson(personId: Long?): PersonEntity? {
        if (personId == null || personId <= 0L) return null
        val directRel = relations.find { r ->
            (r.fromPersonId == personId || r.toPersonId == personId) &&
                isPartnerRelation(r.relationLabel)
        }
        if (directRel != null) {
            val partnerId = if (directRel.fromPersonId == personId) directRel.toPersonId else directRel.fromPersonId
            return persons.find { it.id == partnerId }
        }
        val childIds = relations
            .filter { r -> r.toPersonId == personId && relationGenerationStep(r.relationLabel) == -1 }
            .map { it.fromPersonId }
        for (cid in childIds) {
            val coParentRel = relations.find { r ->
                r.fromPersonId == cid && r.toPersonId != personId && relationGenerationStep(r.relationLabel) == -1
            }
            if (coParentRel != null) {
                return persons.find { it.id == coParentRel.toPersonId }
            }
        }
        return null
    }

    fun areCouplesInTree(idA: Long?, idB: Long?): Boolean {
        if (idA == null || idB == null || idA <= 0L || idB <= 0L) return false
        val partner = getCouplePartnerForPerson(idA)
        return partner?.id == idB
    }

    fun solveMatchingBacktrack(
        parts: List<WichtelParticipant>,
        excludeCouples: Boolean,
        excludeHistory: Boolean
    ): Map<Long, Long>? {
        val n = parts.size
        if (n < 2) return null
        val givers = parts.shuffled()
        val usedReceivers = BooleanArray(n)
        val assignment = mutableMapOf<Long, Long>()

        fun isAllowed(giver: WichtelParticipant, receiver: WichtelParticipant): Boolean {
            if (giver.id == receiver.id) return false
            if (excludeCouples && n > 2 && areCouplesInTree(giver.personId, receiver.personId)) return false
            if (excludeHistory && n > 2) {
                val sameAsLast = giver.lastYearTargetId != null && giver.lastYearTargetId == receiver.id
                val sameInHistory = hasDrawnTargetInHistory(giver, receiver)
                if (sameAsLast || sameInHistory) return false
            }
            return true
        }

        fun backtrack(idx: Int): Boolean {
            if (idx == n) return true
            val giver = givers[idx]
            val candidateIndices = (0 until n).shuffled()
            for (rIdx in candidateIndices) {
                if (usedReceivers[rIdx]) continue
                val receiver = parts[rIdx]
                if (!isAllowed(giver, receiver)) continue
                usedReceivers[rIdx] = true
                assignment[giver.id] = receiver.id
                if (backtrack(idx + 1)) return true
                usedReceivers[rIdx] = false
                assignment.remove(giver.id)
            }
            return false
        }

        return if (backtrack(0)) assignment else null
    }

    fun rollGroup(g: WichtelGroup) {
        val parts = g.participants.map { p ->
            if (p.drawnTargetId != null) p.copy(lastYearTargetId = p.drawnTargetId) else p
        }
        if (parts.size < 2) return
        // Deterministischer Backtracking-Solver (Stufe 1: Paar-Sperre + Historie -> Stufe 2: Paar-Sperre -> Stufe 3: Derangement)
        val chosenMap = solveMatchingBacktrack(parts, g.excludeCouples, g.excludeLastYear)
            ?: solveMatchingBacktrack(parts, g.excludeCouples, false)
            ?: solveMatchingBacktrack(parts, false, false)
            ?: return
        val updatedGroup = g.copy(
            isDrawn = true,
            forceRevealed = false,
            participants = parts.map { p ->
                p.copy(drawnTargetId = chosenMap[p.id], notifiedVia = "")
            }
        )
        onSaveGroups(groups.map { if (it.id == g.id) updatedGroup else it })
    }

    fun encodeBlindCode(g: WichtelGroup, giver: WichtelParticipant, target: WichtelParticipant): String {
        val allUrls = (listOf(target.wishlistUrl) + target.extraWishUrls).filter { it.isNotBlank() }
        val payload = JSONObject().apply {
            put("g", g.groupName)
            put("a", g.adminName)
            put("m", g.motto)
            put("b", g.budget)
            put("r", formatIsoToDdMmYyyy(g.revealDateIso).ifBlank { "24.12." })
            put("f", giver.name)
            put("t", target.name)
            put("w", target.wishText)
            put("n", target.noGos)
            put("d", target.address)
            val uArr = JSONArray()
            allUrls.forEach { uArr.put(it) }
            put("u", uArr)
        }
        val b64 = android.util.Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "WICHTEL_SECRET:$b64"
    }

    fun buildBlindWhatsAppMessage(g: WichtelGroup, giver: WichtelParticipant, target: WichtelParticipant): String {
        val code = encodeBlindCode(g, giver, target)
        val revealStr = formatIsoToDdMmYyyy(g.revealDateIso).ifBlank { "Weihnachten" }
        return buildString {
            append("🎄🔐 Hallo ${giver.name.substringBefore(" ")}!\n")
            append("Hier ist dein *verschlüsselter Wichtel-Los-Code* für „${g.groupName}“ (${g.motto}, Budget: ${g.budget}):\n\n")
            append("$code\n\n")
            append("🔓 *So öffnest du dein Los geheim in Remember Me (selbst der Admin sieht den Namen hier im Chat nicht!):*\n")
            append("Kopiere diese Nachricht und tippe in der App unter „🎄 Wichteln“ oben auf *„🔐 Los-Code“*!\n")
            append("🤫 Geheimhaltung bis $revealStr.")
        }
    }

    fun buildSecretMessage(g: WichtelGroup, giver: WichtelParticipant, target: WichtelParticipant): String {
        val revealStr = formatIsoToDdMmYyyy(g.revealDateIso).ifBlank { "Weihnachten" }
        val allUrls = (listOf(target.wishlistUrl) + target.extraWishUrls).filter { it.isNotBlank() }
        return buildString {
            append("🎄 Hallo ${giver.name.substringBefore(" ")}! Hier ist dein geheimes Los für unsere Wichtelrunde „${g.groupName}“ (Admin: ${g.adminName}):\n\n")
            append("🎭 Motto: ${g.motto}\n")
            append("🎁 Du beschenkst: *${target.name}*!\n")
            if (g.budget.isNotBlank()) append("💶 Budget: ${g.budget}\n")
            if (target.wishText.isNotBlank()) append("📝 Wunschzettel: ${target.wishText}\n")
            if (target.noGos.isNotBlank()) append("🚫 Bitte NICHT / Hinweis: ${target.noGos}\n")
            if (target.address.isNotBlank()) append("📦 Post-Adresse: ${target.address}\n")
            allUrls.forEachIndexed { idx, u ->
                append("🛒 ${if (idx == 0) "Amazon-Wunschliste" else "Wunsch-Link ${idx + 1}"}: $u\n")
            }
            append("\n🤫 Pssst: Niemand sonst kennt dein Los! Erst ab dem $revealStr wird für alle aufgelöst, wer wen gezogen hat.")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(groups) { g ->
                    val isSel = g.id == activeGroup?.id
                    CompactButton(if (isSel) "✓ 🎄 ${g.groupName}" else "🎄 ${g.groupName}") {
                        selectedGroupId = g.id
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            CompactButton("🔐 Los-Code") {
                showDecodeSecretDialog = true
            }
            Spacer(modifier = Modifier.width(4.dp))
            CompactButton("+ Neue Runde") {
                isCreatingGroup = true
                isReusingHistory = false
                editingGroup = WichtelGroup(
                    id = System.currentTimeMillis(),
                    groupName = "Weihnachts-Wichteln ${LocalDate.now().year}",
                    adminName = persons.firstOrNull()?.name ?: "Ich (Admin)",
                    motto = "🎁 Klassisches Wichteln",
                    budget = "25 €",
                    revealDateIso = "${LocalDate.now().year}-12-24",
                    reminderDaysBefore = 7,
                    excludeCouples = true,
                    excludeLastYear = true
                )
            }
        }

        val revealed = activeGroup?.let { isGroupRevealed(it) } ?: false
        val revealDateFormatted = activeGroup?.let { formatIsoToDdMmYyyy(it.revealDateIso).ifBlank { "24.12." } } ?: "24.12."

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeGroup == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSlate, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎄 Noch keine Wichtelrunde erstellt", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Erstelle als Gruppenadmin eine Wichtelrunde, kreuze Kontakte (inkl. Paar-Sperre) an oder verwende unten einen Eintrag aus deiner 📜 Wichtel-Historie wieder!",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            CompactButton("🎄 Erste Wichtelrunde starten") {
                                isCreatingGroup = true
                                isReusingHistory = false
                                editingGroup = WichtelGroup(
                                    id = System.currentTimeMillis(),
                                    groupName = "Familien-Wichteln ${LocalDate.now().year}",
                                    adminName = persons.firstOrNull()?.name ?: "Ich (Admin)",
                                    motto = "🎁 Klassisches Wichteln",
                                    budget = "25 €",
                                    revealDateIso = "${LocalDate.now().year}-12-24",
                                    reminderDaysBefore = 7,
                                    excludeCouples = true,
                                    excludeLastYear = true
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSlate, RoundedCornerShape(8.dp))
                            .border(1.dp, AccentSteel, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "🎄 ${activeGroup.groupName}",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "👑 Admin: ${activeGroup.adminName}" +
                                        if (activeGroup.budget.isNotBlank()) " • 💶 Budget: ${activeGroup.budget}" else "",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "${activeGroup.motto} • ⏰ Wecker: ${activeGroup.reminderDaysBefore}T vorher",
                                    color = TreeGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "📅 Frist: $revealDateFormatted" +
                                        (if (activeGroup.excludeCouples) " • 💑 Paar-Sperre" else "") +
                                        (if (activeGroup.excludeLastYear) " • 🔄 Keine Wiederholungen (inkl. Historie)" else ""),
                                    color = AccentSteel,
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CompactButton("✏️") {
                                    isCreatingGroup = false
                                    isReusingHistory = false
                                    editingGroup = activeGroup
                                }
                                CompactButton("🗑️") {
                                    onConfirmDelete("Wichtelrunde „${activeGroup.groupName}“ wirklich löschen?") {
                                        onSaveGroups(groups.filter { it.id != activeGroup.id })
                                    }
                                }
                            }
                        }

                        if (!activeGroup.isDrawn) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, AccentSteel, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "🎲 Bereit zum Würfeln: Sobald alle Teilnehmer angekreuzt sind, tippe auf „🎲 Jetzt auslosen!“. Vor Ablauf der Frist ($revealDateFormatted) sieht niemand die Gesamtliste.",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp
                                )
                            }
                        } else if (!revealed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, TreeGreen, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "🔒 Geheimhaltung aktiv bis $revealDateFormatted!\nNiemand in der Gruppe sieht, wer wen gezogen hat. Teile jedem sein Los einzeln per 📲 WhatsApp oder 💬 In-App mit.",
                                    color = TreeGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, WarnAmber, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "🔓 Frist abgelaufen ($revealDateFormatted) – Alle Paarungen sind jetzt sichtbar:",
                                    color = WarnAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                activeGroup.participants.forEach { p ->
                                    val t = activeGroup.participants.find { it.id == p.drawnTargetId }
                                    Text(
                                        "🎁 ${p.name} ➔ hat ${t?.name ?: "?"} beschenkt",
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactButton(if (activeGroup.isDrawn) "🎲 Neu würfeln" else "🎲 Jetzt auslosen!") {
                                rollGroup(activeGroup)
                            }
                            CompactButton("☑️ Teilnehmer (${activeGroup.participants.size})") {
                                checklistGroup = activeGroup
                            }
                            CompactButton("⏰ Wecker") {
                                runCatching {
                                    val msg = "⏰🎄 Erinnerung zur Wichtelrunde „${activeGroup.groupName}“ (${activeGroup.motto}):\nBis zum $revealDateFormatted ist es nicht mehr weit – denk daran, dein Wichtelgeschenk (Budget: ${activeGroup.budget}) rechtzeitig zu besorgen! 🎁"
                                    val url = "https://wa.me/?text=${android.net.Uri.encode(msg)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                            }
                            if (activeGroup.isDrawn) {
                                CompactButton(if (revealed) "🔒" else "🔓") {
                                    val nextForce = !activeGroup.forceRevealed
                                    val toggled = activeGroup.copy(forceRevealed = nextForce)
                                    onSaveGroups(groups.map { if (it.id == activeGroup.id) toggled else it })
                                    if (nextForce) {
                                        archiveGroupToHistory(toggled)
                                    }
                                }
                            }
                        }
                        if (activeGroup.isDrawn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompactButton("📜 In Historie abschließen & an Teilnehmer senden") {
                                    val revealedGroup = activeGroup.copy(forceRevealed = true)
                                    onSaveGroups(groups.map { if (it.id == activeGroup.id) revealedGroup else it })
                                    val entry = archiveGroupToHistory(revealedGroup)
                                    if (entry != null) {
                                        shareHistoryEntry(entry)
                                    }
                                }
                            }
                        }
                    }
                }

                items(activeGroup.participants) { part ->
                    val target = activeGroup.participants.find { it.id == part.drawnTargetId }
                    val partnerContact = getCouplePartnerForPerson(part.personId)
                    val partnerInGroup = if (partnerContact != null) {
                        activeGroup.participants.find { it.personId == partnerContact.id }
                    } else null
                    val pastDraws = getHistoricalTargetsForParticipant(part)
                    val allUrls = (listOf(part.wishlistUrl) + part.extraWishUrls).filter { it.isNotBlank() }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSlate, RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (partnerInGroup != null && activeGroup.excludeCouples) PartnerRose else BorderSlate,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val contactPhoto = persons.find {
                            (part.personId != null && it.id == part.personId) ||
                                it.name.equals(part.name, ignoreCase = true)
                        }?.photoBase64.orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            PersonAvatar(name = part.name, photoBase64 = contactPhoto, sizeDp = 34)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        part.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (part.name.equals(activeGroup.adminName, ignoreCase = true)) {
                                        Text("👑 Admin", color = AccentSteel, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                    if (part.notifiedVia.isNotBlank()) {
                                        Text("✓ ${part.notifiedVia}", color = TreeGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
                                if (partnerInGroup != null && activeGroup.excludeCouples) {
                                    Text(
                                        "💑 Paar mit ${partnerInGroup.name} (ziehen sich nicht gegenseitig)",
                                        color = PartnerRose,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (activeGroup.excludeLastYear && pastDraws.isNotEmpty()) {
                                    Text(
                                        "📜 Keine Wiederholung (Historie): " + pastDraws.joinToString(", ") { "${it.second} (${it.first})" },
                                        color = AccentSteel,
                                        fontSize = 11.sp
                                    )
                                }
                                val statusLine = when {
                                    !activeGroup.isDrawn -> "Noch nicht ausgelost"
                                    revealed && target != null -> "🎁 Hat gezogen: ${target.name}"
                                    else -> "🎲 Zugelost • 🔒 Geheim bis $revealDateFormatted"
                                }
                                Text(
                                    statusLine,
                                    color = if (!activeGroup.isDrawn) TextMuted else if (revealed) WarnAmber else TreeGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (part.wishText.isNotBlank()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🎁 Wunsch: ${part.wishText}", color = TextSecondary, fontSize = 11.5.sp, modifier = Modifier.weight(1f, fill = false))
                                        Text(
                                            "🔎 Amazon ↗",
                                            color = AccentSteel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                runCatching {
                                                    val url = "https://www.amazon.de/s?k=${android.net.Uri.encode(part.wishText)}"
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                                }
                                            }
                                        )
                                    }
                                }
                                if (part.noGos.isNotBlank()) {
                                    Text("🚫 Bitte NICHT / Hinweis: ${part.noGos}", color = WarnAmber, fontSize = 11.sp)
                                }
                                if (part.address.isNotBlank()) {
                                    Text("📦 Adresse: ${part.address}", color = TextMuted, fontSize = 11.sp)
                                }
                                allUrls.forEachIndexed { idx, u ->
                                    Text(
                                        "🛒 ${if (idx == 0) "Amazon-Wunschliste öffnen ↗" else "Wunsch-Link ${idx + 1} öffnen ↗"}",
                                        color = AccentSteel,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                val url = if (u.startsWith("http")) u else "https://$u"
                                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                            }
                                        }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompactButton("🎁 Wunsch") {
                                    editingParticipantGroup = activeGroup
                                    editingParticipant = part
                                }
                                CompactButton("🕵️") {
                                    anonQuestionTarget = activeGroup to part
                                }
                                Text(
                                    "✕",
                                    color = WarnAmber,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            onConfirmDelete("Teilnehmer „${part.name}“ aus der Wichtelrunde entfernen?") {
                                                val updated = activeGroup.copy(
                                                    isDrawn = false,
                                                    participants = activeGroup.participants
                                                        .filter { it.id != part.id }
                                                        .map { it.copy(drawnTargetId = null, notifiedVia = "") }
                                                )
                                                onSaveGroups(groups.map { if (it.id == activeGroup.id) updated else it })
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }

                        if (activeGroup.isDrawn && target != null) {
                            HorizontalDivider(color = BorderSlate, modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompactButton("🔐 Blind-Code") {
                                    val msg = buildBlindWhatsAppMessage(activeGroup, part, target)
                                    val updatedGroup = activeGroup.copy(
                                        participants = activeGroup.participants.map {
                                            if (it.id == part.id) it.copy(notifiedVia = "WhatsApp (Blind-Code)") else it
                                        }
                                    )
                                    onSaveGroups(groups.map { if (it.id == activeGroup.id) updatedGroup else it })
                                    runCatching {
                                        val cleanPhone = part.phone.replace(Regex("[^0-9]"), "")
                                        val url = if (cleanPhone.isNotBlank()) {
                                            "https://wa.me/$cleanPhone?text=${android.net.Uri.encode(msg)}"
                                        } else {
                                            "https://wa.me/?text=${android.net.Uri.encode(msg)}"
                                        }
                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                    }
                                }
                                CompactButton("📲 Los per WhatsApp") {
                                    val msg = buildSecretMessage(activeGroup, part, target)
                                    val updatedGroup = activeGroup.copy(
                                        participants = activeGroup.participants.map {
                                            if (it.id == part.id) it.copy(notifiedVia = "WhatsApp") else it
                                        }
                                    )
                                    onSaveGroups(groups.map { if (it.id == activeGroup.id) updatedGroup else it })
                                    runCatching {
                                        val cleanPhone = part.phone.replace(Regex("[^0-9]"), "")
                                        val url = if (cleanPhone.isNotBlank()) {
                                            "https://wa.me/$cleanPhone?text=${android.net.Uri.encode(msg)}"
                                        } else {
                                            "https://wa.me/?text=${android.net.Uri.encode(msg)}"
                                        }
                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                    }
                                }
                                CompactButton("💬 In-App") {
                                    val updatedGroup = activeGroup.copy(
                                        participants = activeGroup.participants.map {
                                            if (it.id == part.id) it.copy(notifiedVia = "In-App") else it
                                        }
                                    )
                                    onSaveGroups(groups.map { if (it.id == activeGroup.id) updatedGroup else it })
                                }
                                CompactButton("👁️ Geheimes Los") {
                                    secretPeekPair = Triple(activeGroup, part, target)
                                }
                            }
                        }
                    }
                }
            }

            // 📜 Wichtel-Historie unter dem Wichteln
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(CardSlate, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "📜 Wichtel-Historie (${history.size})",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Vergangene Jahre werden bei „🔄 Keine Wiederholungen“ automatisch berücksichtigt. Jeder Teilnehmer kann seine Historie als Admin wiederverwenden, vor Neustart bearbeiten, einsehen oder einzeln löschen.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        CompactButton("📥 Importieren") {
                            showImportHistoryDialog = true
                        }
                    }

                    if (history.isEmpty()) {
                        Text(
                            "Noch keine abgeschlossenen Wichtelrunden in der Historie.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        history.sortedByDescending { it.year }.forEach { h ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "🎄 ${h.groupName} (${h.year})",
                                            color = TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "👑 Admin: ${h.adminName} • ${h.motto}" +
                                                if (h.budget.isNotBlank()) " • 💶 ${h.budget}" else "",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    CompactButton("🗑️") {
                                        onConfirmDelete("Wichtel-Historie „${h.groupName} (${h.year})“ wirklich löschen?") {
                                            onSaveHistory(history.filter { it.id != h.id })
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    h.pairings.forEachIndexed { idx, pr ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CardSlate, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "🎁 ${pr.giverName} ➔ ${pr.receiverName}",
                                                color = TextPrimary,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "✕",
                                                color = WarnAmber,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable {
                                                        onConfirmDelete("Einzelnen Historieneintrag „${pr.giverName} ➔ ${pr.receiverName}“ (${h.year}) löschen?") {
                                                            val nextPairings = h.pairings.filterIndexed { i, _ -> i != idx }
                                                            val nextHist = if (nextPairings.isEmpty()) {
                                                                history.filter { it.id != h.id }
                                                            } else {
                                                                history.map { if (it.id == h.id) it.copy(pairings = nextPairings) else it }
                                                            }
                                                            onSaveHistory(nextHist)
                                                        }
                                                    }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CompactButton("♻️ Wiederverwenden & Bearbeiten") {
                                        reuseHistoryEntry(h)
                                    }
                                    CompactButton("📲 An Teilnehmer") {
                                        shareHistoryEntry(h)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Wichtel-Historie importieren (für Teilnehmer nach Abschluss der Wichtelperiode)
    if (showImportHistoryDialog) {
        var rawImport by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showImportHistoryDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📥 Eigene Wichtel-Historie importieren", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Füge hier die vom Gruppenadmin geteilte Nachricht (mit WICHTEL_HISTORY_JSON:...) ein. Danach kannst du den alten Wichteleintrag selbst als Admin wiederverwenden, vor Neustart bearbeiten, einsehen oder löschen.",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
                OutlinedTextField(
                    value = rawImport,
                    onValueChange = { rawImport = it },
                    label = { Text("Nachricht oder WICHTEL_HISTORY_JSON einfügen") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CompactButton("Abbrechen") { showImportHistoryDialog = false }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("Importieren") {
                        runCatching {
                            val marker = "WICHTEL_HISTORY_JSON:"
                            val jsonPart = if (rawImport.contains(marker)) {
                                rawImport.substringAfter(marker).trim()
                            } else {
                                rawImport.trim()
                            }
                            val obj = JSONObject(jsonPart)
                            val pairingsArr = obj.optJSONArray("pairings") ?: JSONArray()
                            val pairings = mutableListOf<WichtelHistoryPair>()
                            for (i in 0 until pairingsArr.length()) {
                                val p = pairingsArr.optJSONObject(i) ?: continue
                                pairings.add(
                                    WichtelHistoryPair(
                                        giverName = p.optString("giverName", ""),
                                        giverPhone = p.optString("phone", p.optString("giverPhone", "")),
                                        receiverName = p.optString("receiverName", ""),
                                        wishText = p.optString("wishText", ""),
                                        wishlistUrl = p.optString("wishlistUrl", ""),
                                        noGos = p.optString("noGos", ""),
                                        address = p.optString("address", "")
                                    )
                                )
                            }
                            if (pairings.isNotEmpty()) {
                                val imported = WichtelHistoryEntry(
                                    id = System.currentTimeMillis(),
                                    year = obj.optInt("year", LocalDate.now().year - 1),
                                    groupName = obj.optString("groupName", "Importierte Wichtelrunde"),
                                    adminName = obj.optString("adminName", "Admin"),
                                    motto = obj.optString("motto", "🎁 Klassisches Wichteln"),
                                    budget = obj.optString("budget", "25 €"),
                                    completedDateIso = obj.optString("completedDateIso", obj.optString("completedAtIso", LocalDate.now().toString())),
                                    pairings = pairings
                                )
                                onSaveHistory(listOf(imported) + history)
                                showImportHistoryDialog = false
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Wichtelrunde erstellen / bearbeiten / aus Historie wiederverwenden
    editingGroup?.let { eg ->
        var gName by remember(eg) { mutableStateOf(eg.groupName) }
        var gAdmin by remember(eg) { mutableStateOf(eg.adminName) }
        var gMotto by remember(eg) { mutableStateOf(eg.motto) }
        var gBudget by remember(eg) { mutableStateOf(eg.budget) }
        var gReveal by remember(eg) { mutableStateOf(formatIsoToDdMmYyyy(eg.revealDateIso)) }
        var gReminder by remember(eg) { mutableStateOf(eg.reminderDaysBefore.toString()) }
        var gCouples by remember(eg) { mutableStateOf(eg.excludeCouples) }
        var gLastYear by remember(eg) { mutableStateOf(eg.excludeLastYear) }

        fun saveGroupChanges() {
            val finalGroup = eg.copy(
                groupName = gName.trim().ifBlank { "Wichtelrunde" },
                adminName = gAdmin.trim().ifBlank { "Admin" },
                motto = gMotto,
                budget = gBudget.trim(),
                revealDateIso = normalizeDateInputToIso(gReveal).ifBlank { "${LocalDate.now().year}-12-24" },
                reminderDaysBefore = gReminder.toIntOrNull() ?: 7,
                excludeCouples = gCouples,
                excludeLastYear = gLastYear,
                participants = if (isCreatingGroup && eg.participants.isEmpty()) {
                    val adminContact = persons.find { it.name.equals(gAdmin.trim(), ignoreCase = true) }
                    listOf(
                        WichtelParticipant(
                            id = System.currentTimeMillis() + 1,
                            personId = adminContact?.id,
                            name = gAdmin.trim().ifBlank { "Admin" },
                            phone = adminContact?.phone.orEmpty()
                        )
                    )
                } else eg.participants
            )
            val nextList = if (isCreatingGroup) groups + finalGroup else groups.map { if (it.id == eg.id) finalGroup else it }
            onSaveGroups(nextList)
            selectedGroupId = finalGroup.id
            isReusingHistory = false
            editingGroup = null
        }

        Dialog(onDismissRequest = { editingGroup = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            isReusingHistory -> "♻️ Altes Wichteln wiederverwenden"
                            isCreatingGroup -> "🎄 Neue Wichtelrunde"
                            else -> "🎄 Wichtelrunde bearbeiten"
                        },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CompactButton("✕") { editingGroup = null }
                        CompactButton("✓ Speichern") { saveGroupChanges() }
                    }
                }
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isReusingHistory) {
                        Text(
                            "✨ ${eg.participants.size} Teilnehmer & Wunschzettel aus der Historie wurden übernommen. Du kannst alle Angaben vor dem Neustart frei anpassen und danach auch die Teilnehmerliste bearbeiten.",
                            color = TreeGreen,
                            fontSize = 11.5.sp
                        )
                    }
                    OutlinedTextField(value = gName, onValueChange = { gName = it }, label = { Text("Name der Wichtelrunde") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gAdmin, onValueChange = { gAdmin = it }, label = { Text("👑 Gruppenadmin (Ersteller)") }, modifier = Modifier.fillMaxWidth())
                    if (persons.isNotEmpty()) {
                        Text("Oder Gruppenadmin per Dropdown-Liste (ABCD, Nachname) wählen:", color = TextSecondary, fontSize = 11.sp)
                        ContactSearchDropdown(
                            persons = persons,
                            selectedPersonId = persons.find { it.name.equals(gAdmin.trim(), ignoreCase = true) }?.id,
                            onSelectPerson = { p -> gAdmin = p.name },
                            placeholderText = "🔎 Gruppenadmin aus Kontakten wählen"
                        )
                    }
                    Text("🎭 Wichtel-Modus / Motto:", color = TextSecondary, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(mottos) { m ->
                            CompactButton(if (gMotto == m) "✓ $m" else m) { gMotto = m }
                        }
                    }
                    OutlinedTextField(value = gBudget, onValueChange = { gBudget = it }, label = { Text("💶 Budget / Preisrahmen") }, modifier = Modifier.fillMaxWidth())
                    DateInputWithCalendar(
                        value = gReveal,
                        onValueChange = { gReveal = it },
                        label = "📅 Auflösungs-Frist (TT.MM.JJJJ)"
                    )
                    OutlinedTextField(value = gReminder, onValueChange = { gReminder = it }, label = { Text("⏰ Geschenk-Wecker (Tage vor Frist)") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = gCouples, onCheckedChange = { gCouples = it })
                        Text("💑 Paare aus Stammbaum nicht gegenseitig zulosen", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = gLastYear, onCheckedChange = { gLastYear = it })
                        Text("🔄 Keine Wiederholungen (Vorjahre aus Wichtel-Historie mit einbeziehen)", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = BorderSlate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") { editingGroup = null }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton(if (isReusingHistory) "♻️ Übernehmen & Runde anlegen" else "✓ Speichern") {
                        saveGroupChanges()
                    }
                }
            }
        }
    }

    // Dialog: Teilnehmer per Ankreuzliste (Multi-Select) einladen – Paare besonders hervorgehoben!
    checklistGroup?.let { cg ->
        var checkedIds by remember(cg) {
            mutableStateOf(cg.participants.mapNotNull { it.personId }.toSet())
        }
        var checklistSearchQuery by remember(cg) { mutableStateOf("") }
        var checklistSelectedLetter by remember(cg) { mutableStateOf("") }
        var customName by remember(cg) { mutableStateOf("") }
        var customPhone by remember(cg) { mutableStateOf("") }

        val sortedWichtelPersons = remember(persons) { persons.sortedByLastName() }

        // Gruppiere Paare zusammen für besondere visuelle Darstellung (nach Nachname sortiert)
        val groupedContacts = remember(sortedWichtelPersons, relations, checklistSearchQuery, checklistSelectedLetter) {
            val filtered = sortedWichtelPersons.filter {
                matchesPersonSearchAndLetter(it, checklistSearchQuery, checklistSelectedLetter)
            }
            val filteredIds = filtered.map { it.id }.toSet()
            val result = mutableListOf<List<PersonEntity>>()
            val visited = mutableSetOf<Long>()
            for (p in filtered) {
                if (visited.contains(p.id)) continue
                val partner = getCouplePartnerForPerson(p.id)
                if (partner != null && !visited.contains(partner.id) && filteredIds.contains(partner.id)) {
                    visited.add(p.id)
                    visited.add(partner.id)
                    result.add(listOf(p, partner))
                } else {
                    visited.add(p.id)
                    result.add(listOf(p))
                }
            }
            result
        }

        fun confirmChecklistParticipants() {
            val keptCustom = cg.participants.filter { it.personId == null }
            val nextParticipants = mutableListOf<WichtelParticipant>()
            nextParticipants.addAll(keptCustom)

            sortedWichtelPersons.filter { checkedIds.contains(it.id) }.forEach { contact ->
                val existing = cg.participants.find { it.personId == contact.id }
                if (existing != null) {
                    nextParticipants.add(existing)
                } else {
                    nextParticipants.add(
                        WichtelParticipant(
                            id = System.currentTimeMillis() + contact.id,
                            personId = contact.id,
                            name = contact.name,
                            phone = contact.phone,
                            wishText = contact.notes
                        )
                    )
                }
            }

            if (customName.isNotBlank()) {
                nextParticipants.add(
                    WichtelParticipant(
                        id = System.currentTimeMillis() + 999L,
                        personId = null,
                        name = customName.trim(),
                        phone = customPhone.trim()
                    )
                )
            }

            val updatedGroup = cg.copy(
                isDrawn = false,
                participants = nextParticipants.map { it.copy(drawnTargetId = null, notifiedVia = "") }
            )
            onSaveGroups(groups.map { if (it.id == cg.id) updatedGroup else it })
            checklistGroup = null
        }

        Dialog(onDismissRequest = { checklistGroup = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fixierte Leiste oben im Dialog (kein Scrollen nötig zum Bestätigen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "☑️ Teilnehmer einladen (${checkedIds.size})",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CompactButton("✕") { checklistGroup = null }
                        CompactButton("✓ Übernehmen") { confirmChecklistParticipants() }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nachname, Vorname (ABCD-Schnellwahl):", color = TextSecondary, fontSize = 11.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("Alle") { checkedIds = persons.map { it.id }.toSet() }
                        CompactButton("Keine") { checkedIds = emptySet() }
                    }
                }
                ContactSearchDropdown(
                    persons = sortedWichtelPersons,
                    selectedPersonId = null,
                    onSelectPerson = { p ->
                        checkedIds = if (checkedIds.contains(p.id)) checkedIds - p.id else checkedIds + p.id
                    },
                    placeholderText = "🔎 Teilnehmer per Dropdown-Liste (ABCD) an-/abwählen",
                    confirmButtonLabel = "✓ Übernehmen",
                    onConfirmAction = { confirmChecklistParticipants() },
                    showInlineAlphabet = true,
                    externalSearchQuery = checklistSearchQuery,
                    onExternalSearchQueryChange = { checklistSearchQuery = it },
                    externalSelectedLetter = checklistSelectedLetter,
                    onExternalSelectedLetterChange = { checklistSelectedLetter = it },
                    checkedPersonIds = checkedIds,
                    onToggleCheckedPerson = { p ->
                        checkedIds = if (checkedIds.contains(p.id)) checkedIds - p.id else checkedIds + p.id
                    }
                )
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedContacts.forEach { groupMembers ->
                        if (groupMembers.size == 2) {
                            val p1 = groupMembers[0]
                            val p2 = groupMembers[1]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate, RoundedCornerShape(8.dp))
                                    .border(1.5.dp, PartnerRose, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("💑 Ehe-/Lebenspartner (Paar-Sperre)", color = PartnerRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("🔒 Ziehen sich nicht gegenseitig!", color = PartnerRose, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                listOf(p1 to p2, p2 to p1).forEach { (member, spouse) ->
                                    val isChecked = checkedIds.contains(member.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                checkedIds = if (isChecked) checkedIds - member.id else checkedIds + member.id
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { ch ->
                                                checkedIds = if (ch) checkedIds + member.id else checkedIds - member.id
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(formatLastNameFirst(member.name), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("💑 Paar mit ${formatLastNameFirst(spouse.name)}", color = PartnerRose, fontSize = 10.5.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            val member = groupMembers.first()
                            val isChecked = checkedIds.contains(member.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                    .clickable {
                                        checkedIds = if (isChecked) checkedIds - member.id else checkedIds + member.id
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { ch ->
                                        checkedIds = if (ch) checkedIds + member.id else checkedIds - member.id
                                    }
                                )
                                Text(formatLastNameFirst(member.name), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSlate)
                    Text("Oder weitere Person frei hinzufügen (optional):", color = TextSecondary, fontSize = 11.5.sp)
                    OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customPhone, onValueChange = { customPhone = it }, label = { Text("Telefon (WhatsApp)") }, modifier = Modifier.fillMaxWidth())
                }

                HorizontalDivider(color = BorderSlate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") { checklistGroup = null }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("✓ Teilnehmer übernehmen") { confirmChecklistParticipants() }
                }
            }
        }
    }

    // Dialog: Wunschliste, No-Gos, Post-Adresse & Links für einzelnen Teilnehmer bearbeiten
    val pGroup = editingParticipantGroup
    val pItem = editingParticipant
    if (pGroup != null && pItem != null) {
        var pName by remember(pItem) { mutableStateOf(pItem.name) }
        var pPhone by remember(pItem) { mutableStateOf(pItem.phone) }
        var pWish by remember(pItem) { mutableStateOf(pItem.wishText) }
        var pNoGos by remember(pItem) { mutableStateOf(pItem.noGos) }
        var pAddress by remember(pItem) { mutableStateOf(pItem.address) }
        var pUrl by remember(pItem) { mutableStateOf(pItem.wishlistUrl) }
        var pExtraUrls by remember(pItem) { mutableStateOf(pItem.extraWishUrls) }

        fun saveParticipantWishes() {
            if (pName.isNotBlank()) {
                val updatedPart = pItem.copy(
                    name = pName.trim(),
                    phone = pPhone.trim(),
                    wishText = pWish.trim(),
                    noGos = pNoGos.trim(),
                    address = pAddress.trim(),
                    wishlistUrl = pUrl.trim(),
                    extraWishUrls = pExtraUrls.map { it.trim() }.filter { it.isNotBlank() }
                )
                val updatedGroup = pGroup.copy(
                    participants = pGroup.participants.map { if (it.id == pItem.id) updatedPart else it }
                )
                onSaveGroups(groups.map { if (it.id == pGroup.id) updatedGroup else it })
                editingParticipantGroup = null
                editingParticipant = null
            }
        }

        Dialog(
            onDismissRequest = {
                editingParticipantGroup = null
                editingParticipant = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🎁 Wunschliste: ${pItem.name}",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CompactButton("✕") {
                            editingParticipantGroup = null
                            editingParticipant = null
                        }
                        CompactButton("✓ Speichern") { saveParticipantWishes() }
                    }
                }
                HorizontalDivider(color = BorderSlate)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pPhone, onValueChange = { pPhone = it }, label = { Text("Telefon (für WhatsApp-Los)") }, modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pWish,
                            onValueChange = { pWish = it },
                            label = { Text("🎁 Wunschzettel / Geschenk-Ideen") },
                            modifier = Modifier.weight(1f)
                        )
                        CompactButton("🔎 Amazon") {
                            runCatching {
                                val q = pWish.trim()
                                val url = if (q.isNotBlank()) "https://www.amazon.de/s?k=${android.net.Uri.encode(q)}" else "https://www.amazon.de"
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = pNoGos,
                        onValueChange = { pNoGos = it },
                        label = { Text("🚫 Bitte NICHT schenken / Allergien & Größen") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pAddress,
                        onValueChange = { pAddress = it },
                        label = { Text("📦 Post-Adresse (für Fern-Wichteln)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pUrl,
                        onValueChange = { pUrl = it },
                        label = { Text("🛒 Haupt-Wunschliste (Amazon-Wunschliste)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    pExtraUrls.forEachIndexed { idx, extraUrl ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = extraUrl,
                                onValueChange = { newVal ->
                                    pExtraUrls = pExtraUrls.toMutableList().also { it[idx] = newVal }
                                },
                                label = { Text("🔗 Weiterer Wunsch-Link ${idx + 2}") },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "✕",
                                color = WarnAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    pExtraUrls = pExtraUrls.toMutableList().also { it.removeAt(idx) }
                                }
                            )
                        }
                    }
                    CompactButton("+ Weiterer Link") {
                        pExtraUrls = pExtraUrls + ""
                    }
                }

                HorizontalDivider(color = BorderSlate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") {
                        editingParticipantGroup = null
                        editingParticipant = null
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("✓ Speichern") { saveParticipantWishes() }
                }
            }
        }
    }

    // Dialog: Anonyme Wichtel-Frage („Geheimer Bote“)
    anonQuestionTarget?.let { (g, target) ->
        var qText by remember(target) { mutableStateOf("") }
        Dialog(onDismissRequest = { anonQuestionTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🕵️ Anonyme Wichtel-Frage („Geheimer Bote“)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Stelle ${target.name} anonym eine Frage (z. B. nach Größe, Lieblingsfarbe oder Buchtitel), ohne deine Identität als Wichtel zu verraten!",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = qText,
                    onValueChange = { qText = it },
                    label = { Text("Geheime Frage an ${target.name.substringBefore(" ")}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") { anonQuestionTarget = null }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("📲 Per WhatsApp fragen") {
                        if (qText.isNotBlank()) {
                            runCatching {
                                val msg = "🕵️🎄 *Geheime Wichtel-Frage* aus der Runde „${g.groupName}“:\n\nDein geheimer Wichtel lässt über Gruppenadmin ${g.adminName} fragen:\n„${qText.trim()}“\n\nAntworte einfach hier – deine Antwort wird anonym weitergeleitet! 🤫"
                                val cleanPhone = target.phone.replace(Regex("[^0-9]"), "")
                                val url = if (cleanPhone.isNotBlank()) {
                                    "https://wa.me/$cleanPhone?text=${android.net.Uri.encode(msg)}"
                                } else {
                                    "https://wa.me/?text=${android.net.Uri.encode(msg)}"
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                            anonQuestionTarget = null
                        }
                    }
                }
            }
        }
    }

    // Dialog: Geheimes Einzel-Los für einen Teilnehmer
    secretPeekPair?.let { (g, giver, target) ->
        val allUrls = (listOf(target.wishlistUrl) + target.extraWishUrls).filter { it.isNotBlank() }
        Dialog(onDismissRequest = { secretPeekPair = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, TreeGreen, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🤫 Geheimes Wichtel-Los für ${giver.name}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${g.groupName} (${g.motto}) • Budget: ${g.budget}", color = TextSecondary, fontSize = 12.sp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgSlate, RoundedCornerShape(8.dp))
                        .border(1.dp, TreeGreen, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Du beschenkst zu Weihnachten:", color = TextMuted, fontSize = 11.sp)
                    Text("🎁 ${target.name}", color = TreeGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (target.wishText.isNotBlank()) {
                        Text("📝 Wunschzettel: ${target.wishText}", color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    if (target.noGos.isNotBlank()) {
                        Text("🚫 Bitte NICHT: ${target.noGos}", color = WarnAmber, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                    }
                    if (target.address.isNotBlank()) {
                        Text("📦 Adresse: ${target.address}", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    allUrls.forEachIndexed { idx, u ->
                        Spacer(modifier = Modifier.height(2.dp))
                        CompactButton("🛒 ${if (idx == 0) "Amazon-Wunschliste öffnen ↗" else "Wunsch-Link ${idx + 1} öffnen ↗"}") {
                            runCatching {
                                val url = if (u.startsWith("http")) u else "https://$u"
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        }
                    }
                }
                CompactButton("🕵️ Anonyme Frage an ${target.name.substringBefore(" ")}") {
                    secretPeekPair = null
                    anonQuestionTarget = g to target
                }
                Text(
                    "🔒 Geheim bis zur Auflösung am ${formatIsoToDdMmYyyy(g.revealDateIso)}!",
                    color = WarnAmber,
                    fontSize = 11.sp
                )
                CompactButton("Verstanden & schließen") { secretPeekPair = null }
            }
        }
    }

    // Dialog: Verschlüsselten Wichtel-Los-Code (WICHTEL_SECRET:...) entschlüsseln
    if (showDecodeSecretDialog) {
        var rawCodeInput by remember { mutableStateOf("") }
        var decodedObj by remember { mutableStateOf<JSONObject?>(null) }
        var decodeError by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showDecodeSecretDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, AccentSteel, RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "🔐 Geheimen Wichtel-Los-Code entschlüsseln",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Füge hier die WhatsApp-Nachricht oder den Code (WICHTEL_SECRET:...) ein, den dir der Gruppenadmin geschickt hat. So erfährst nur du allein, wen du beschenkst!",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
                OutlinedTextField(
                    value = rawCodeInput,
                    onValueChange = {
                        rawCodeInput = it
                        decodeError = false
                    },
                    label = { Text("Nachricht mit WICHTEL_SECRET:... einfügen") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (decodeError) {
                    Text(
                        "⚠️ Kein gültiger WICHTEL_SECRET-Code gefunden.",
                        color = WarnAmber,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                decodedObj?.let { d ->
                    val targetName = d.optString("t", "?")
                    val giverName = d.optString("f", "")
                    val groupName = d.optString("g", "Wichtelrunde")
                    val motto = d.optString("m", "")
                    val budget = d.optString("b", "")
                    val revealDate = d.optString("r", "24.12.")
                    val wish = d.optString("w", "")
                    val noGos = d.optString("n", "")
                    val addr = d.optString("d", "")
                    val urlsArr = d.optJSONArray("u") ?: JSONArray()
                    val urls = (0 until urlsArr.length()).mapNotNull { idx ->
                        urlsArr.optString(idx).takeIf { it.isNotBlank() }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(8.dp))
                            .border(1.5.dp, TreeGreen, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "🎄 $groupName ($motto)" + if (budget.isNotBlank()) " • Budget: $budget" else "",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Hallo $giverName, du beschenkst:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            "🎁 $targetName",
                            color = TreeGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (wish.isNotBlank()) {
                            Text("📝 Wunschzettel: $wish", color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        if (noGos.isNotBlank()) {
                            Text("🚫 Bitte NICHT: $noGos", color = WarnAmber, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                        }
                        if (addr.isNotBlank()) {
                            Text("📦 Adresse: $addr", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                        urls.forEachIndexed { idx, u ->
                            CompactButton("🛒 ${if (idx == 0) "Amazon-Wunschliste öffnen ↗" else "Wunsch-Link ${idx + 1} öffnen ↗"}") {
                                runCatching {
                                    val url = if (u.startsWith("http")) u else "https://$u"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                            }
                        }
                        Text("🔒 Geheimhaltung bis $revealDate!", color = WarnAmber, fontSize = 11.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Schließen") { showDecodeSecretDialog = false }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("🔓 Los entschlüsseln") {
                        val match = Regex("WICHTEL_SECRET:([A-Za-z0-9+/=_\\-]+)").find(rawCodeInput.trim())
                        val b64Part = match?.groupValues?.getOrNull(1) ?: rawCodeInput.trim()
                        val parsed = runCatching {
                            val jsonStr = String(android.util.Base64.decode(b64Part, android.util.Base64.DEFAULT), Charsets.UTF_8)
                            JSONObject(jsonStr)
                        }.getOrNull()
                        if (parsed != null && parsed.optString("t").isNotBlank()) {
                            decodedObj = parsed
                            decodeError = false
                        } else {
                            decodedObj = null
                            decodeError = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(BtnBg, RoundedCornerShape(6.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = CenteredPillTextStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
