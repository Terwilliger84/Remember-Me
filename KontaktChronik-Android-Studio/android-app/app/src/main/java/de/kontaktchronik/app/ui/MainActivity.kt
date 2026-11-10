package de.kontaktchronik.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.data.ContactsSyncRepository
import de.kontaktchronik.app.messaging.DirectMessagingService
import de.kontaktchronik.app.model.*
import de.kontaktchronik.app.reminders.DailyReminderWorker
import de.kontaktchronik.app.reminders.QuickActionDispatcher
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val BgSlate = Color(0xFF0F172A)
private val CardSlate = Color(0xFF1E293B)
private val BorderSlate = Color(0xFF334155)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFCBD5E1)
private val TextMuted = Color(0xFF94A3B8)
private val AccentSteel = Color(0xFF38BDF8)
private val WarnAmber = Color(0xFFF59E0B)
private val PartnerRose = Color(0xFFF43F5E)
private val TreeGreen = Color(0xFF10B981)

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

fun formatIsoToDdMmYyyy(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val clean = iso.trim()
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyReminderWorker.schedule(this)

        val initialPopupPersonId = intent?.getLongExtra("POPUP_PERSON_ID", -1L) ?: -1L
        val initialPopupReason = intent?.getStringExtra("POPUP_REASON") ?: "Erinnerung"

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgSlate,
                    surface = CardSlate,
                    primary = AccentSteel,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
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

    val persons by db.observeAllPersons().collectAsState(initial = emptyList())
    val relations by db.observeAllRelations().collectAsState(initial = emptyList())
    val specialEvents by db.observeAllSpecialEvents().collectAsState(initial = emptyList())

    var activeTab by remember { mutableStateOf("Übersicht") }
    var showPastDates by remember { mutableStateOf(false) }
    var selectedTreeRootId by remember { mutableStateOf<Long?>(null) }
    var popupPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var popupReason by remember { mutableStateOf(initialPopupReason) }
    var quickMessageText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var detailPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var syncStatus by remember { mutableStateOf("Geburtstage, Stammbaum & Erinnerungen") }

    LaunchedEffect(persons, initialPopupPersonId) {
        if (initialPopupPersonId > 0 && popupPerson == null) {
            popupPerson = persons.find { it.id == initialPopupPersonId }
        }
        if (selectedTreeRootId == null && persons.isNotEmpty()) {
            selectedTreeRootId = persons.first().id
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
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
        // Kompakte Kopfzeile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Remember Me v1.0.2", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(syncStatus, color = TextMuted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactButton("Sync") {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        scope.launch {
                            val count = syncRepo.syncDeviceContactsWithBirthdays(context.contentResolver)
                            syncStatus = "$count Kontakte synchronisiert"
                        }
                    } else {
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
                CompactButton("Neu") { showCreateDialog = true }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation inkl. Stammbaum & Erinnerung
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Übersicht", "Stammbaum", "Timer", "Erinnerung").forEach { tab ->
                val active = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (active) Color(0xFF334155) else CardSlate, RoundedCornerShape(6.dp))
                        .border(1.dp, if (active) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                        .clickable { activeTab = tab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab,
                        color = if (active) TextPrimary else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
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
                        "Tippe oben auf 'Sync', um deine Telefonkontakte nach Geburtstagen zu durchsuchen, auf 'Neu' zum Anlegen, oder lade Beispielkontakte mit komplettem Familien-Stammbaum.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    CompactButton("Beispieldaten mit Stammbaum anlegen") {
                        scope.launch {
                            val omaId = db.upsertPerson(
                                PersonEntity(
                                    name = "Helga Weber (Oma)",
                                    birthdayIso = LocalDate.now().plusDays(18).withYear(1958).toString(),
                                    phone = "+491701112233",
                                    notes = "Mag Gartenarbeit & Apfelkuchen",
                                    timerEnabled = true,
                                    timerInterval = 7,
                                    timerUnit = TimerUnit.DAYS.name,
                                    lastContactedIso = LocalDate.now().minusDays(6).toString()
                                )
                            )
                            val annaId = db.upsertPerson(
                                PersonEntity(
                                    name = "Anna Weber",
                                    birthdayIso = LocalDate.now().plusDays(4).withYear(1992).toString(),
                                    phone = "+491712345678",
                                    email = "anna.weber@beispiel.de",
                                    appPeerId = "KC-ANNA-92",
                                    notes = "Lieblingsblumen: Pfingstrosen",
                                    timerEnabled = true,
                                    timerInterval = 14,
                                    timerUnit = TimerUnit.DAYS.name,
                                    lastContactedIso = LocalDate.now().minusDays(15).toString()
                                )
                            )
                            val thomasId = db.upsertPerson(
                                PersonEntity(
                                    name = "Thomas Weber",
                                    birthdayIso = LocalDate.now().minusDays(12).withYear(1990).toString(),
                                    phone = "+491719988776",
                                    email = "thomas.weber@beispiel.de",
                                    notes = "Trinkt gerne Espresso",
                                    timerEnabled = true,
                                    timerInterval = 14,
                                    timerUnit = TimerUnit.DAYS.name,
                                    lastContactedIso = LocalDate.now().minusDays(8).toString()
                                )
                            )
                            val markusId = db.upsertPerson(
                                PersonEntity(
                                    name = "Markus Weber",
                                    birthdayIso = LocalDate.now().minusDays(5).withYear(1995).toString(),
                                    phone = "+491605544332",
                                    notes = "Annas Bruder",
                                    timerEnabled = true,
                                    timerInterval = 30,
                                    timerUnit = TimerUnit.DAYS.name,
                                    lastContactedIso = LocalDate.now().minusDays(20).toString()
                                )
                            )
                            val leoId = db.upsertPerson(
                                PersonEntity(
                                    name = "Leo Weber (Baby)",
                                    birthdayIso = LocalDate.now().withYear(2026).toString(),
                                    hasNoOwnPhone = true,
                                    notes = "Kleidergröße 68, mag Holzrasseln",
                                    timerEnabled = true,
                                    timerInterval = 1,
                                    timerUnit = TimerUnit.MONTHS.name,
                                    lastContactedIso = LocalDate.now().minusDays(10).toString()
                                )
                            )

                            // Beziehungen für den Stammbaum
                            addBidirectionalRelation(annaId, omaId, "Kind von")
                            addBidirectionalRelation(markusId, omaId, "Kind von")
                            addBidirectionalRelation(markusId, annaId, "Bruder von")
                            addBidirectionalRelation(thomasId, annaId, "Ehe-Partner von")
                            addBidirectionalRelation(leoId, annaId, "Kind von")
                            addBidirectionalRelation(leoId, thomasId, "Kind von")

                            db.insertSpecialEvent(
                                SpecialEventEntity(
                                    personId = annaId,
                                    title = "Kommt zu Besuch",
                                    dateIso = LocalDate.now().plusDays(7).toString(),
                                    leadDays = 2,
                                    note = "Mit Baby Leo übers Wochenende"
                                )
                            )
                            selectedTreeRootId = annaId
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (activeTab) {
            "Stammbaum" -> {
                FamilyTreeScreen(
                    persons = persons,
                    relations = relations,
                    selectedRootId = selectedTreeRootId ?: persons.firstOrNull()?.id,
                    onSelectRoot = { selectedTreeRootId = it },
                    onOpenDetails = { detailPerson = it },
                    onOpenMessage = { p ->
                        val relPairs = relations
                            .filter { it.fromPersonId == p.id }
                            .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
                        val resolved = ContactResolver.resolveTarget(p, relPairs)
                        popupReason = "Nachricht"
                        quickMessageText = "Hallo ${resolved.targetPersonName.substringBefore(" ")}, herzliche Grüße!"
                        popupPerson = p
                    }
                )
            }

            "Timer" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timerPersons = persons.filter { it.timerEnabled }
                    items(timerPersons) { p ->
                        val nextTimer = ContactResolver.nextTimerDate(p)
                        val diff = nextTimer?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) } ?: 0L
                        val nextTimerStr = formatIsoToDdMmYyyy(nextTimer?.toString())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSlate, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { detailPerson = p }
                            ) {
                                Text(p.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Intervall: alle ${p.timerInterval} ${p.timerUnit} • Zuletzt: ${formatIsoToDdMmYyyy(p.lastContactedIso).ifBlank { "-" }}",
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
                                        .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                    val resolved = ContactResolver.resolveTarget(p, relPairs)
                                    popupReason = "Melde-Timer"
                                    quickMessageText = "Hallo ${resolved.targetPersonName.substringBefore(" ")}, herzliche Grüße!"
                                    popupPerson = p
                                }
                            }
                        }
                    }
                }
            }

            "Erinnerung" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (specialEvents.isEmpty() && persons.none { !it.birthdayIso.isNullOrBlank() }) {
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
                    items(specialEvents) { ev ->
                        val p = persons.find { it.id == ev.personId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSlate, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                        .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                    val resolved = ContactResolver.resolveTarget(p, relPairs)
                                    popupReason = "Erinnerung: ${ev.title}"
                                    quickMessageText = "Hallo ${resolved.targetPersonName.substringBefore(" ")}, herzliche Grüße!"
                                    popupPerson = p
                                }
                            }
                        }
                    }
                    items(persons.filter { !it.birthdayIso.isNullOrBlank() }) { p ->
                        val bdayDays = ContactResolver.daysUntilNextBirthday(p.birthdayIso)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSlate, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { detailPerson = p }
                            ) {
                                Text(
                                    "Geburtstag: ${p.name}",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    formatCompactBirthdayLine(p.birthdayIso).orEmpty(),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            CompactButton("Nachricht") {
                                val relPairs = relations
                                    .filter { it.fromPersonId == p.id }
                                    .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
                                val resolved = ContactResolver.resolveTarget(p, relPairs)
                                popupReason = "Geburtstag"
                                quickMessageText = "Herzlichen Glückwunsch zum Geburtstag, ${resolved.targetPersonName.substringBefore(" ")}!"
                                popupPerson = p
                            }
                        }
                    }
                }
            }

            else -> {
                // Sortierung: Standardmäßig nach nächstem Geburtstag aufsteigend;
                // bei aktivem Button "Vergangene Termine" nach zuletzt vergangenem Geburtstag (-1T, -5T, -12T ...)
                val sortedPersons = remember(persons, showPastDates) {
                    if (!showPastDates) {
                        persons.sortedWith(
                            compareBy<PersonEntity> {
                                ContactResolver.daysUntilNextBirthday(it.birthdayIso) ?: Long.MAX_VALUE
                            }.thenBy { it.name }
                        )
                    } else {
                        persons.sortedWith(
                            compareByDescending<PersonEntity> {
                                ContactResolver.negativeDaysSinceLastBirthday(it.birthdayIso) ?: Long.MIN_VALUE
                            }.thenBy { it.name }
                        )
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
                        Text(
                            text = if (showPastDates) "Vergangene Geburtstage & Termine" else "Nächste Geburtstage zuerst",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (showPastDates) AccentSteel else Color(0xFF334155),
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
                                fontWeight = FontWeight.SemiBold
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
                                .mapNotNull { rel -> persons.find { it.id == rel.toPersonId }?.let { rel to it } }
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
                                    .background(CardSlate, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                                    .clickable { detailPerson = person }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = person.name,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!bdayLine.isNullOrBlank()) {
                                        Text(
                                            text = bdayLine,
                                            color = if (showPastDates) WarnAmber else TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (personEvents.isNotEmpty()) {
                                        Text(
                                            text = personEvents.joinToString(" • ") { ev ->
                                                val evDate = runCatching { LocalDate.parse(ev.dateIso) }.getOrNull()
                                                val diff = evDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
                                                val diffSuffix = if (showPastDates && diff != null && diff < 0) " (${diff} Tage)" else ""
                                                "${ev.title} (${formatIsoToDdMmYyyy(ev.dateIso)})$diffSuffix"
                                            },
                                            color = AccentSteel,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                CompactButton("Nachricht") {
                                    popupReason = if (showPastDates) "Nachträglich zum Geburtstag" else "Erinnerung"
                                    quickMessageText = if (showPastDates) {
                                        "Alles Gute nachträglich zum Geburtstag, ${resolved.targetPersonName.substringBefore(" ")}!"
                                    } else {
                                        "Hallo ${resolved.targetPersonName.substringBefore(" ")}, herzliche Grüße!"
                                    }
                                    popupPerson = person
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Neuen Kontakt anlegen (inkl. Beziehungen für den Stammbaum)
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var bday by remember { mutableStateOf(formatIsoToDdMmYyyy(LocalDate.now().toString())) }
        var noPhone by remember { mutableStateOf(false) }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var timerInterval by remember { mutableStateOf("14") }
        var relLabel by remember { mutableStateOf("Kind von") }
        var selectedRelTargetId by remember { mutableStateOf(-1L) }

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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bday, onValueChange = { bday = it }, label = { Text("Geburtsdatum (TT.MM.JJJJ)") }, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noPhone, onCheckedChange = { noPhone = it })
                    Text("Keine eigene Nummer (z. B. Baby)", color = TextSecondary, fontSize = 12.sp)
                }

                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-Mail (aktiviert Mail-Button)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = timerInterval, onValueChange = { timerInterval = it }, label = { Text("Melde-Timer (Tage)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())

                if (persons.isNotEmpty()) {
                    Divider(color = BorderSlate)
                    Text("Beziehung für Stammbaum (optional):", color = AccentSteel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RELATION_PRESETS) { preset ->
                            val selected = relLabel == preset
                            Box(
                                modifier = Modifier
                                    .background(if (selected) AccentSteel else BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (selected) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                    .clickable { relLabel = preset }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    preset,
                                    color = if (selected) BgSlate else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Text("Ziel-Kontakt auswählen:", color = TextSecondary, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val noneSelected = selectedRelTargetId == -1L
                            Box(
                                modifier = Modifier
                                    .background(if (noneSelected) Color(0xFF334155) else BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (noneSelected) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                    .clickable { selectedRelTargetId = -1L }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text("Keine", color = TextPrimary, fontSize = 11.sp)
                            }
                        }
                        items(persons) { targetP ->
                            val isSel = selectedRelTargetId == targetP.id
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) AccentSteel else BgSlate, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isSel) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                    .clickable { selectedRelTargetId = targetP.id }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    targetP.name,
                                    color = if (isSel) BgSlate else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompactButton("Abbrechen") { showCreateDialog = false }
                    CompactButton("Speichern") {
                        if (name.isNotBlank()) {
                            scope.launch {
                                val newId = db.upsertPerson(
                                    PersonEntity(
                                        name = name.trim(),
                                        birthdayIso = normalizeDateInputToIso(bday),
                                        hasNoOwnPhone = noPhone,
                                        phone = phone.trim(),
                                        email = email.trim(),
                                        notes = notes.trim(),
                                        timerEnabled = true,
                                        timerInterval = timerInterval.toIntOrNull() ?: 14,
                                        timerUnit = TimerUnit.DAYS.name,
                                        lastContactedIso = LocalDate.now().toString()
                                    )
                                )
                                if (selectedRelTargetId > 0 && relLabel.isNotBlank()) {
                                    addBidirectionalRelation(newId, selectedRelTargetId, relLabel)
                                }
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
        var nameVal by remember(dp) { mutableStateOf(dp.name) }
        var bdayVal by remember(dp) { mutableStateOf(formatIsoToDdMmYyyy(dp.birthdayIso)) }
        var phoneVal by remember(dp) { mutableStateOf(dp.phone) }
        var noPhoneVal by remember(dp) { mutableStateOf(dp.hasNoOwnPhone) }
        var emailVal by remember(dp) { mutableStateOf(dp.email) }
        var notesVal by remember(dp) { mutableStateOf(dp.notes) }
        var newEvTitle by remember { mutableStateOf("") }
        var newEvDate by remember { mutableStateOf(formatIsoToDdMmYyyy(LocalDate.now().plusDays(3).toString())) }

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
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactButton("🌳 Stammbaum") {
                        selectedTreeRootId = dp.id
                        detailPerson = null
                        activeTab = "Stammbaum"
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = nameVal, onValueChange = { nameVal = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bdayVal, onValueChange = { bdayVal = it }, label = { Text("Geburtsdatum (TT.MM.JJJJ)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phoneVal, onValueChange = { phoneVal = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = noPhoneVal, onCheckedChange = { noPhoneVal = it })
                        Text("Keine eigene Nummer (z. B. Baby)", color = TextSecondary, fontSize = 12.sp)
                    }
                    OutlinedTextField(value = emailVal, onValueChange = { emailVal = it }, label = { Text("E-Mail-Adresse") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notesVal, onValueChange = { notesVal = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())

                    // Abschnitt: Beziehungen für Stammbaum
                    Divider(color = BorderSlate)
                    Text("Beziehungen (Stammbaum):", color = AccentSteel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

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
                                            scope.launch {
                                                db.deleteRelation(rel)
                                            }
                                        }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }

                    if (otherPersons.isNotEmpty()) {
                        Text("Beziehung hinzufügen:", color = TextSecondary, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(RELATION_PRESETS) { preset ->
                                val isSel = newRelLabel == preset
                                Box(
                                    modifier = Modifier
                                        .background(if (isSel) AccentSteel else BgSlate, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (isSel) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                        .clickable { newRelLabel = preset }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        preset,
                                        color = if (isSel) BgSlate else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(otherPersons) { op ->
                                val isSel = newRelTargetId == op.id
                                Box(
                                    modifier = Modifier
                                        .background(if (isSel) AccentSteel else BgSlate, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (isSel) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                                        .clickable { newRelTargetId = op.id }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        op.name,
                                        color = if (isSel) BgSlate else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
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

                    // Abschnitt: Erinnerungen
                    Divider(color = BorderSlate)
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
                                        scope.launch { db.deleteSpecialEvent(ev) }
                                    }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }

                    Text("Neue Erinnerung eintragen:", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = newEvTitle, onValueChange = { newEvTitle = it }, label = { Text("Erinnerung (z.B. Operation, Besuch)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newEvDate, onValueChange = { newEvDate = it }, label = { Text("Datum (TT.MM.JJJJ)") }, modifier = Modifier.fillMaxWidth())
                }

                Divider(color = BorderSlate)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompactButton("Löschen") {
                        scope.launch {
                            db.deletePerson(dp)
                            detailPerson = null
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton("Schließen") { detailPerson = null }
                        CompactButton("Speichern") {
                            scope.launch {
                                val updated = dp.copy(
                                    name = nameVal.trim().ifBlank { dp.name },
                                    birthdayIso = normalizeDateInputToIso(bdayVal),
                                    phone = phoneVal.trim(),
                                    hasNoOwnPhone = noPhoneVal,
                                    email = emailVal.trim(),
                                    notes = notesVal.trim()
                                )
                                db.updatePerson(updated)
                                if (newEvTitle.isNotBlank()) {
                                    db.insertSpecialEvent(
                                        SpecialEventEntity(
                                            personId = dp.id,
                                            title = newEvTitle.trim(),
                                            dateIso = normalizeDateInputToIso(newEvDate),
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

    // Sortiert Mitglieder einer Ebene so, dass Eltern / Partner direkt nebeneinander stehen
    fun orderRowMembers(members: List<PersonEntity>, genLevel: Int): List<PersonEntity> {
        val visited = mutableSetOf<Long>()
        val ordered = mutableListOf<PersonEntity>()
        val sortedSeed = members.sortedWith { a, b ->
            when {
                a.id == rootPerson.id -> -1
                b.id == rootPerson.id -> 1
                else -> {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Kompakte Auswahl der Fokus-Person für den Stammbaum
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSlate, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(persons) { p ->
                val isSelected = p.id == rootPerson.id
                Box(
                    modifier = Modifier
                        .background(if (isSelected) AccentSteel else BgSlate, RoundedCornerShape(6.dp))
                        .border(1.dp, if (isSelected) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
                        .clickable { onSelectRoot(p.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = p.name,
                        color = if (isSelected) BgSlate else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Kompakter visueller Stammbaum (Eltern nebeneinander, darunter Kinder)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSlate, RoundedCornerShape(10.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val levelKeys = levels.keys.toList()
            levelKeys.forEachIndexed { idx, genLevel ->
                val members = levels[genLevel].orEmpty()

                // Knoten dieser Generation nebeneinander
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    members.forEachIndexed { mIdx, member ->
                        val isRoot = member.id == rootPerson.id
                        val roleText = roleRelativeToRoot(member, genLevel)
                        val isPartner = isPartnerRelation(roleText)

                        Column(
                            modifier = Modifier
                                .widthIn(min = 76.dp, max = 122.dp)
                                .background(
                                    if (isRoot) Color(0xFF1E3A5F) else BgSlate,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = if (isRoot) 1.5.dp else 1.dp,
                                    color = when {
                                        isRoot -> AccentSteel
                                        isPartner -> PartnerRose
                                        else -> BorderSlate
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onOpenDetails(member) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = member.name,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                            Text(
                                text = roleText,
                                color = when {
                                    isRoot -> AccentSteel
                                    isPartner -> PartnerRose
                                    else -> TreeGreen
                                },
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp
                            )
                        }

                        // Horizontale Verbindung zwischen Eltern / Ehe-Partnern nebeneinander
                        if (mIdx < members.size - 1) {
                            val nextMember = members[mIdx + 1]
                            val coupled = arePartnersOrCoParents(member.id, nextMember.id)
                            if (coupled) {
                                Text(
                                    text = "──",
                                    color = PartnerRose,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                        }
                    }
                }

                // Vertikale Stammbaum-Verbindungslinie zu den Kindern darunter
                if (idx < levelKeys.size - 1) {
                    val nextMembers = levels[levelKeys[idx + 1]].orEmpty()
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                    ) {
                        val centerX = size.width / 2f
                        drawLine(
                            color = AccentSteel,
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, size.height),
                            strokeWidth = 4f
                        )
                        if (nextMembers.size > 1) {
                            val barHalfWidth = (size.width * 0.25f).coerceAtMost(120f)
                            drawLine(
                                color = AccentSteel,
                                start = Offset(centerX - barHalfWidth, size.height),
                                end = Offset(centerX + barHalfWidth, size.height),
                                strokeWidth = 4f
                            )
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
            .background(if (isPrimary) Color(0xFF0284C7) else Color(0xFF334155), RoundedCornerShape(6.dp))
            .border(1.dp, if (isPrimary) AccentSteel else BorderSlate, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChannelSymbolIcon(channel)
        Text(
            text = labelOverride ?: channel,
            color = TextPrimary,
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
fun CompactButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF334155), RoundedCornerShape(6.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
