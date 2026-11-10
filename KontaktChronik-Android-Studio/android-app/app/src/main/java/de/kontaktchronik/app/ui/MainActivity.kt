package de.kontaktchronik.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.data.ContactsSyncRepository
import de.kontaktchronik.app.messaging.DirectMessagingService
import de.kontaktchronik.app.model.*
import de.kontaktchronik.app.reminders.DailyReminderWorker
import de.kontaktchronik.app.reminders.QuickActionDispatcher
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
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
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
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
    return clean.split(Regex("\\s+")).firstOrNull()?.ifBlank { clean } ?: clean
}

fun extractLastName(fullName: String): String {
    val clean = stripNameRoleSuffix(fullName)
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (parts.size > 1) parts.last() else (parts.firstOrNull() ?: clean)
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyReminderWorker.schedule(this)

        val initialPopupPersonId = intent?.getLongExtra("POPUP_PERSON_ID", -1L) ?: -1L
        val initialPopupReason = intent?.getStringExtra("POPUP_REASON") ?: "Erinnerung"

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
                    initialPopupPersonId = initialPopupPersonId,
                    initialPopupReason = initialPopupReason
                )
            }
        }
    }
}

@Composable
fun KontaktChronikApp(
    initialPopupPersonId: Long,
    initialPopupReason: String
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
    var contactSortMode by remember { mutableStateOf("birthday") } // "birthday" | "firstname" | "lastname"
    var allContactsFilter by remember { mutableStateOf("all") } // "all" | "hidden"
    var hiddenPersonIds by remember {
        val rawSet = appPrefs.getStringSet("hidden_person_ids", emptySet()).orEmpty()
        mutableStateOf(rawSet.mapNotNull { it.toLongOrNull() }.toSet())
    }
    val visiblePersons = remember(persons, hiddenPersonIds) {
        persons.filter { !it.isHidden && !hiddenPersonIds.contains(it.id) }
    }
    var selectedTreeRootId by remember { mutableStateOf<Long?>(null) }
    var popupPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var popupReason by remember { mutableStateOf(initialPopupReason) }
    var quickMessageText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var detailPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var syncStatus by remember { mutableStateOf("") }
    var pendingConfirmMessage by remember { mutableStateOf<String?>(null) }
    var pendingConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val clipboardManager = LocalClipboardManager.current

    fun setPersonHidden(personId: Long, hidden: Boolean) {
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
    }

    LaunchedEffect(Unit) {
        val savedTheme = appPrefs.getString("theme_mode", null)
        appThemeMode = if (savedTheme != null) {
            savedTheme
        } else {
            "warm"
        }
    }

    LaunchedEffect(persons, initialPopupPersonId) {
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

        if (initialPopupPersonId > 0 && popupPerson == null) {
            val found = persons.find { it.id == initialPopupPersonId }
            if (found != null) {
                val firstName = stripNameRoleSuffix(found.name).substringBefore(" ")
                if (quickMessageText.isBlank()) {
                    quickMessageText = pickRandomBirthdayGreeting(firstName, false)
                }
                popupPerson = found
            }
        }
        if (selectedTreeRootId == null && persons.isNotEmpty()) {
            selectedTreeRootId = persons.first().id
        }
        val remainingPersons = persons.filterNot { dummyPersons.contains(it) }
        if (remainingPersons.any { it.photoBase64.isBlank() }) {
            val hasRead = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasRead) {
                syncRepo.syncDeviceContactsWithBirthdays(context.contentResolver)
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
                val count = syncRepo.syncDeviceContactsWithBirthdays(context.contentResolver)
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
            Text("Remember Me v1.0.22", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            val count = syncRepo.syncDeviceContactsWithBirthdays(context.contentResolver)
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(iconStr, fontSize = 14.sp)
                    Text(
                        shortLabel,
                        color = if (active) TextPrimary else TextMuted,
                        fontSize = 9.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
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
                                    val count = syncRepo.syncDeviceContactsWithBirthdays(context.contentResolver)
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
                val hiddenCount = remember(persons, hiddenPersonIds) {
                    persons.count { it.isHidden || hiddenPersonIds.contains(it.id) }
                }
                val baseList = remember(persons, hiddenPersonIds, allContactsFilter) {
                    if (allContactsFilter == "hidden") {
                        persons.filter { it.isHidden || hiddenPersonIds.contains(it.id) }
                    } else {
                        persons
                    }
                }
                val sortedAll = remember(baseList, contactSortMode) {
                    when (contactSortMode) {
                        "firstname" -> baseList.sortedWith(
                            compareBy<PersonEntity> { extractFirstName(it.name).lowercase() }
                                .thenBy { extractLastName(it.name).lowercase() }
                        )
                        "lastname" -> baseList.sortedWith(
                            compareBy<PersonEntity> { extractLastName(it.name).lowercase() }
                                .thenBy { extractFirstName(it.name).lowercase() }
                        )
                        else -> baseList.sortedWith(
                            compareBy<PersonEntity> {
                                ContactResolver.daysUntilNextBirthday(it.birthdayIso) ?: Long.MAX_VALUE
                            }.thenBy { it.name.lowercase() }
                        )
                    }
                }
                val sortBtnLabel = when (contactSortMode) {
                    "firstname" -> "⇅ Vorname"
                    "lastname" -> "⇅ Name"
                    else -> "⇅ Geburtstag"
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(sortBtnLabel) {
                            contactSortMode = when (contactSortMode) {
                                "birthday" -> "firstname"
                                "firstname" -> "lastname"
                                else -> "birthday"
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactButton(if (allContactsFilter == "all") "✓ Alle (${persons.size})" else "Alle (${persons.size})") {
                                allContactsFilter = "all"
                            }
                            CompactButton(
                                if (allContactsFilter == "hidden") "✓ Ausgeblendet ($hiddenCount)"
                                else "Ausgeblendet ($hiddenCount)"
                            ) {
                                allContactsFilter = "hidden"
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sortedAll) { person ->
                            val isHidden = person.isHidden || hiddenPersonIds.contains(person.id)
                            val bdayLine = formatCompactBirthdayLine(person.birthdayIso)
                            val subText = bdayLine ?: if (person.phone.isNotBlank()) "Ohne Datum • ${person.phone}" else "Ohne Datumsangabe"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (isHidden) WarnAmber else BorderSlate, RoundedCornerShape(10.dp))
                                    .clickable { detailPerson = person }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(name = person.name, photoBase64 = person.photoBase64)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = person.name,
                                            color = if (isHidden) TextMuted else TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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
                                        if (person.birthdayIso.isNullOrBlank()) {
                                            Text(
                                                text = "Ohne Datum",
                                                color = AccentSteel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    Text(
                                        text = subText,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
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
                    selectedRootId = selectedTreeRootId?.takeIf { id -> visiblePersons.any { it.id == id } } ?: visiblePersons.firstOrNull()?.id,
                    onSelectRoot = { selectedTreeRootId = it },
                    onOpenDetails = { detailPerson = it },
                    onOpenMessage = { p ->
                        val relPairs = relations
                            .filter { it.fromPersonId == p.id }
                            .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                        val resolved = ContactResolver.resolveTarget(p, relPairs)
                        popupReason = "Nachricht"
                        quickMessageText = pickRandomBirthdayGreeting(resolved.targetPersonName.substringBefore(" "), false)
                        popupPerson = p
                    }
                )
            }

            "Timer" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timerPersons = visiblePersons.filter { it.timerEnabled }
                    items(timerPersons) { p ->
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
                                    .clickable { detailPerson = p }
                            ) {
                                Text(p.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CompactButton("Nachricht") {
                                    val relPairs = relations
                                        .filter { it.fromPersonId == p.id }
                                        .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                    val resolved = ContactResolver.resolveTarget(p, relPairs)
                                    popupReason = "Melde-Timer"
                                    quickMessageText = pickRandomBirthdayGreeting(resolved.targetPersonName.substringBefore(" "), false)
                                    popupPerson = p
                                }
                            }
                        }
                    }
                }
            }

            "Erinnerung" -> {
                val visibleEvents = remember(specialEvents, visiblePersons) {
                    val visIds = visiblePersons.map { it.id }.toSet()
                    specialEvents.filter { visIds.contains(it.personId) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visibleEvents.isEmpty() && visiblePersons.none { !it.birthdayIso.isNullOrBlank() }) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    "Noch keine Erinnerungen eingetragen. Tippe in der Übersicht auf einen Namen, um eine Erinnerung hinzuzufügen.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    items(visibleEvents) { ev ->
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
                                    "${ev.title} – ${p?.name ?: "Kontakt"}",
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
                                    quickMessageText = pickRandomBirthdayGreeting(resolved.targetPersonName.substringBefore(" "), false)
                                    popupPerson = p
                                }
                            }
                        }
                    }
                    items(visiblePersons.filter { !it.birthdayIso.isNullOrBlank() }) { p ->
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
                                        "🎂 ${p.name}",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                                quickMessageText = pickRandomBirthdayGreeting(resolved.targetPersonName.substringBefore(" "), false)
                                popupPerson = p
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
                val sortedPersons = remember(visiblePersons, showPastDates, contactSortMode) {
                    when (contactSortMode) {
                        "firstname" -> visiblePersons.sortedWith(
                            compareBy<PersonEntity> { extractFirstName(it.name).lowercase() }
                                .thenBy { extractLastName(it.name).lowercase() }
                        )
                        "lastname" -> visiblePersons.sortedWith(
                            compareBy<PersonEntity> { extractLastName(it.name).lowercase() }
                                .thenBy { extractFirstName(it.name).lowercase() }
                        )
                        else -> if (!showPastDates) {
                            visiblePersons.sortedWith(
                                compareBy<PersonEntity> {
                                    ContactResolver.daysUntilNextBirthday(it.birthdayIso) ?: Long.MAX_VALUE
                                }.thenBy { it.name.lowercase() }
                            )
                        } else {
                            visiblePersons.sortedWith(
                                compareByDescending<PersonEntity> {
                                    ContactResolver.negativeDaysSinceLastBirthday(it.birthdayIso) ?: Long.MIN_VALUE
                                }.thenBy { it.name.lowercase() }
                            )
                        }
                    }
                }

                val sortBtnLabel = when (contactSortMode) {
                    "firstname" -> "⇅ Vorname"
                    "lastname" -> "⇅ Name"
                    else -> "⇅ Geburtstag"
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(sortBtnLabel) {
                            contactSortMode = when (contactSortMode) {
                                "birthday" -> "firstname"
                                "firstname" -> "lastname"
                                else -> "birthday"
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (showPastDates) AccentSteel else BtnBg,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, if (showPastDates) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                .clickable { showPastDates = !showPastDates }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (showPastDates) "Anstehende zeigen" else "Vergangene Termine",
                                color = if (showPastDates) BgSlate else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sortedPersons) { person ->
                            val personRelations = relations
                                .filter { it.fromPersonId == person.id }
                                .mapNotNull { rel -> visiblePersons.find { it.id == rel.toPersonId }?.let { rel to it } }
                            val personEvents = specialEvents.filter { it.personId == person.id }
                            val resolved = ContactResolver.resolveTarget(person, personRelations)
                            val bdayLine = if (showPastDates) {
                                formatPassedBirthdayLine(person.birthdayIso)
                            } else {
                                formatCompactBirthdayLine(person.birthdayIso)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlate, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                                    .clickable { detailPerson = person }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(name = person.name, photoBase64 = person.photoBase64)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = person.name,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        BirthdayCountdownPill(birthdayIso = person.birthdayIso, isPastMode = showPastDates)
                                    }
                                    if (!bdayLine.isNullOrBlank()) {
                                        Text(
                                            text = bdayLine,
                                            color = if (showPastDates) WarnAmber else TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(
                                            text = "Ohne Datumsangabe",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (personEvents.isNotEmpty()) {
                                        personEvents.forEach { ev ->
                                            val diff = daysUntilEventSpec(ev.dateIso)
                                            val isDueToday = diff == 0L
                                            val isWeekly = isWeeklyDateSpec(ev.dateIso) || isRepeatDateSpec(ev.dateIso)
                                            val badgeColor = when {
                                                isDueToday -> WarnAmber
                                                isWeekly -> TreeGreen
                                                else -> AccentSteel
                                            }
                                            Text(
                                                text = formatEventBadgeText(ev),
                                                color = badgeColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                CompactButton("Nachricht") {
                                    popupReason = if (showPastDates) "Nachträglich zum Geburtstag" else "Erinnerung"
                                    quickMessageText = pickRandomBirthdayGreeting(
                                        resolved.targetPersonName.substringBefore(" "),
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
                put("version", "1.0.22")
                put("createdAt", dateStr)
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
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("⚙ Optionen & Design", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

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

                Text("🎨 Design-Theme (Sanftes Blau / Festlich)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactButton(if (appThemeMode == "warm") "✓ ☀️ Sanftes Blau" else "☀️ Sanftes Blau") {
                                applyThemeMode("warm")
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactButton(if (appThemeMode == "cozy-dark") "✓ 🌙 Nachtblau" else "🌙 Nachtblau") {
                                applyThemeMode("cozy-dark")
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactButton(if (appThemeMode == "xmas") "✓ 🎄 Weihnachten" else "🎄 Weihnachten") {
                                applyThemeMode("xmas")
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactButton(if (appThemeMode == "classic") "✓ 💎 Klassisch" else "💎 Klassisch") {
                                applyThemeMode("classic")
                            }
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
        val filteredIndices = remember(birthdayGreetings, searchQuery) {
            val q = searchQuery.trim().lowercase()
            birthdayGreetings.indices.filter { idx ->
                q.isBlank() ||
                    birthdayGreetings[idx].lowercase().contains(q) ||
                    (idx + 1).toString() == q
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
                        fontWeight = FontWeight.Bold
                    )
                    CompactButton("🔄 100 Standard") {
                        pendingConfirmMessage = "Alle Geburtstagsgrüße auf die 100 Standard-Glückwunschtexte zurücksetzen?"
                        pendingConfirmAction = {
                            saveBirthdayGreetings(DEFAULT_BIRTHDAY_GREETINGS)
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
                    CompactButton("+ Neu") {
                        saveBirthdayGreetings(
                            listOf("Alles Liebe und Gute zum Geburtstag, {Name}! 🎂🎉") + birthdayGreetings
                        )
                        searchQuery = ""
                    }
                }

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

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kontakt anlegen", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        var createRelTargetExpanded by remember { mutableStateOf(false) }
                        val sortedTargetPersons = remember(persons) {
                            persons.sortedBy { it.name.lowercase() }
                        }
                        val selectedTargetName = sortedTargetPersons
                            .find { it.id == selectedRelTargetId }?.name ?: "-- Keine Verknüpfung --"

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

                            Text("Verknüpfter Kontakt:", color = TextSecondary, fontSize = 11.sp)
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgSlate, RoundedCornerShape(6.dp))
                                        .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                        .clickable { createRelTargetExpanded = true }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        selectedTargetName,
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("▼", color = AccentSteel, fontSize = 11.sp)
                                }
                                DropdownMenu(
                                    expanded = createRelTargetExpanded,
                                    onDismissRequest = { createRelTargetExpanded = false },
                                    modifier = Modifier
                                        .background(CardSlate)
                                        .heightIn(max = 280.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "-- Keine Verknüpfung --",
                                                color = if (selectedRelTargetId == -1L) AccentSteel else TextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = if (selectedRelTargetId == -1L) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedRelTargetId = -1L
                                            createRelTargetExpanded = false
                                        }
                                    )
                                    sortedTargetPersons.forEach { targetP ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    targetP.name,
                                                    color = if (targetP.id == selectedRelTargetId) AccentSteel else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (targetP.id == selectedRelTargetId) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                selectedRelTargetId = targetP.id
                                                createRelTargetExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompactButton("Abbrechen") { showCreateDialog = false }
                    CompactButton("Speichern") {
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
        val otherPersons = persons.filter { it.id != dp.id }
        var newRelLabel by remember { mutableStateOf("Kind von") }
        var newRelTargetId by remember(dp, otherPersons.size) {
            mutableStateOf(otherPersons.firstOrNull()?.id ?: -1L)
        }

        val currentRelations = relations
            .filter { it.fromPersonId == dp.id }
            .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
        val currentReminders = specialEvents.filter { it.personId == dp.id }
        var detailRelExpanded by remember(dp) { mutableStateOf(false) }

        Dialog(onDismissRequest = { detailPerson = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Details: ${dp.name}",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (treeEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CompactButton("🌳 Stammbaum") {
                            selectedTreeRootId = dp.id
                            detailPerson = null
                            activeTab = "Stammbaum"
                        }
                    }
                }

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
                    OutlinedTextField(value = nameVal, onValueChange = { nameVal = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    DateInputWithCalendar(value = bdayVal, onValueChange = { bdayVal = it }, label = "Geburtsdatum (TT.MM.JJJJ)")
                    OutlinedTextField(value = phoneVal, onValueChange = { phoneVal = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveInDeviceVal, onCheckedChange = { saveInDeviceVal = it })
                        Text("In Handykontakten speichern", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHiddenVal, onCheckedChange = { isHiddenVal = it })
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
                            if (currentRelations.isEmpty()) {
                                Text("Noch keine Beziehungen verknüpft.", color = TextMuted, fontSize = 11.sp)
                            } else {
                                currentRelations.forEach { (rel, targetPerson) ->
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
                                            "${rel.relationLabel} ${targetPerson.name}",
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

                            if (otherPersons.isNotEmpty()) {
                                var detailRelLabelExpanded by remember { mutableStateOf(false) }
                                var detailRelTargetExpanded by remember { mutableStateOf(false) }
                                val sortedOtherPersons = remember(otherPersons) {
                                    otherPersons.sortedBy { it.name.lowercase() }
                                }
                                val selectedOtherName = sortedOtherPersons
                                    .find { it.id == newRelTargetId }?.name
                                    ?: sortedOtherPersons.firstOrNull()?.also { newRelTargetId = it.id }?.name
                                    ?: "Kontakt wählen"

                                Text("Beziehung hinzufügen:", color = TextSecondary, fontSize = 11.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgSlate, RoundedCornerShape(6.dp))
                                                .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                                .clickable { detailRelLabelExpanded = true }
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                newRelLabel,
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

                                    Box(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BgSlate, RoundedCornerShape(6.dp))
                                                .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                                .clickable { detailRelTargetExpanded = true }
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                selectedOtherName,
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
                                            expanded = detailRelTargetExpanded,
                                            onDismissRequest = { detailRelTargetExpanded = false },
                                            modifier = Modifier
                                                .background(CardSlate)
                                                .heightIn(max = 280.dp)
                                        ) {
                                            sortedOtherPersons.forEach { op ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            op.name,
                                                            color = if (op.id == newRelTargetId) AccentSteel else TextPrimary,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (op.id == newRelTargetId) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        newRelTargetId = op.id
                                                        detailRelTargetExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                CompactButton("+ Beziehung speichern") {
                                    if (newRelTargetId > 0) {
                                        scope.launch {
                                            addBidirectionalRelation(dp.id, newRelTargetId, newRelLabel)
                                        }
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
                        CompactButton("Speichern") {
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
                            resolved.targetPersonName.substringBefore(" "),
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

    val rootPerson = persons.find { it.id == selectedRootId } ?: persons.first()
    val personMap = remember(persons) { persons.associateBy { it.id } }

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
                else -> a.name.compareTo(b.name)
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
        // Obere Steuerleiste: Kontakt-Auswahl + Zoom-Funktion für größere Stammbäume
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSlate, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Kontakt:",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSlate, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rootPerson.name,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text("▼", color = AccentSteel, fontSize = 11.sp)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(CardSlate)
                    ) {
                        persons.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = p.name,
                                        color = if (p.id == rootPerson.id) AccentSteel else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (p.id == rootPerson.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectRoot(p.id)
                                    dropdownExpanded = false
                                }
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
                val nodeMaxW = (122f * z).coerceAtLeast(52f).dp
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

        // Nicht verbundene Kontakte anzeigen, falls vorhanden
        val unconnected = persons.filter { !generationByPersonId.containsKey(it.id) }
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
                    "Weitere Kontakte (noch nicht mit ${rootPerson.name} verknüpft):",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tippe auf einen Namen, um in den Details eine Beziehung (z.B. 'Kind von', 'Ehe-Partner von', 'Bruder von') hinzuzufügen.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(unconnected) { up ->
                        Box(
                            modifier = Modifier
                                .background(BgSlate, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
                                .clickable { onOpenDetails(up) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(up.name, color = AccentSteel, fontSize = 12.sp)
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
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChannelSymbolIcon(channel)
        Text(
            text = labelOverride ?: channel,
            color = if (isPrimary) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                                        "💑 Paar mit ${partnerInGroup.name.substringBefore(" ")} (ziehen sich nicht gegenseitig)",
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

        Dialog(onDismissRequest = { editingGroup = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    when {
                        isReusingHistory -> "♻️ Altes Wichteln wiederverwenden & bearbeiten"
                        isCreatingGroup -> "🎄 Neue Wichtelrunde erstellen"
                        else -> "🎄 Wichtelrunde bearbeiten"
                    },
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isReusingHistory) {
                    Text(
                        "✨ ${eg.participants.size} Teilnehmer & Wunschzettel aus der Historie wurden übernommen. Du kannst alle Angaben vor dem Neustart frei anpassen und danach auch die Teilnehmerliste bearbeiten.",
                        color = TreeGreen,
                        fontSize = 11.5.sp
                    )
                }
                OutlinedTextField(value = gName, onValueChange = { gName = it }, label = { Text("Name der Wichtelrunde") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = gAdmin, onValueChange = { gAdmin = it }, label = { Text("👑 Gruppenadmin (Ersteller)") }, modifier = Modifier.fillMaxWidth())
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") { editingGroup = null }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton(if (isReusingHistory) "♻️ Übernehmen & Runde anlegen" else "Speichern") {
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
                }
            }
        }
    }

    // Dialog: Teilnehmer per Ankreuzliste (Multi-Select) einladen – Paare besonders hervorgehoben!
    checklistGroup?.let { cg ->
        var checkedIds by remember(cg) {
            mutableStateOf(cg.participants.mapNotNull { it.personId }.toSet())
        }
        var customName by remember(cg) { mutableStateOf("") }
        var customPhone by remember(cg) { mutableStateOf("") }

        // Gruppiere Paare zusammen für besondere visuelle Darstellung
        val groupedContacts = remember(persons, relations) {
            val result = mutableListOf<List<PersonEntity>>()
            val visited = mutableSetOf<Long>()
            for (p in persons) {
                if (visited.contains(p.id)) continue
                val partner = getCouplePartnerForPerson(p.id)
                if (partner != null && !visited.contains(partner.id)) {
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

        Dialog(onDismissRequest = { checklistGroup = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "☑️ Teilnehmer zur Wichtelrunde einladen",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kontakte zum Mitwichteln ankreuzen:", color = TextSecondary, fontSize = 11.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("Alle") { checkedIds = persons.map { it.id }.toSet() }
                        CompactButton("Keine") { checkedIds = emptySet() }
                    }
                }

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
                                        Text(member.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("💑 Paar mit ${spouse.name.substringBefore(" ")}", color = PartnerRose, fontSize = 10.5.sp)
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
                            Text(member.name, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)
                Text("Oder weitere Person frei hinzufügen (optional):", color = TextSecondary, fontSize = 11.5.sp)
                OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = customPhone, onValueChange = { customPhone = it }, label = { Text("Telefon (WhatsApp)") }, modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") { checklistGroup = null }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("Teilnehmer übernehmen") {
                        val keptCustom = cg.participants.filter { it.personId == null }
                        val nextParticipants = mutableListOf<WichtelParticipant>()
                        nextParticipants.addAll(keptCustom)

                        checkedIds.forEach { pid ->
                            val existing = cg.participants.find { it.personId == pid }
                            if (existing != null) {
                                nextParticipants.add(existing)
                            } else {
                                val contact = persons.find { it.id == pid }
                                if (contact != null) {
                                    nextParticipants.add(
                                        WichtelParticipant(
                                            id = System.currentTimeMillis() + pid,
                                            personId = contact.id,
                                            name = contact.name,
                                            phone = contact.phone,
                                            wishText = contact.notes
                                        )
                                    )
                                }
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
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "🎁 Wunschliste & Details: ${pItem.name}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactButton("Abbrechen") {
                        editingParticipantGroup = null
                        editingParticipant = null
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("Speichern") {
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
fun CompactButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
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
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
