package de.kontaktchronik.app.model

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class TimerUnit(val label: String) {
    DAYS("Tage"),
    WEEKS("Wochen"),
    MONTHS("Monate"),
    YEARS("Jahre")
}

enum class MessageDirection {
    OUTGOING,
    INCOMING
}

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthdayIso: String?, // Format YYYY-MM-DD oder --MM-DD
    val hasNoOwnPhone: Boolean = false, // z.B. Babys / Kleinkinder / WLAN-Tablet ohne SIM
    val phone: String = "",
    val email: String = "", // Optional; aktiviert Mail-Schnellaktion wenn vorhanden
    val appPeerId: String = "", // App-zu-App Kopplungs-ID (z.B. KC-ANNA-92), funktioniert auch ohne Handynummer
    val notes: String = "",
    val timerEnabled: Boolean = false,
    val timerInterval: Int = 14,
    val timerUnit: String = TimerUnit.DAYS.name,
    val lastContactedIso: String? = null, // YYYY-MM-DD
    val importedFromDeviceContactId: String? = null,
    val isHidden: Boolean = false,
    val photoBase64: String = ""
)

@Entity(tableName = "relations")
data class RelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromPersonId: Long,
    val toPersonId: Long,
    val relationLabel: String, // z.B. "Kind von", "Bruder von", "Cousine von"
    val isFallbackContact: Boolean = true
)

@Entity(tableName = "special_events")
data class SpecialEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val title: String, // z.B. "Umzug", "Operation", "Kommt zu Besuch"
    val dateIso: String, // YYYY-MM-DD
    val leadDays: Int = 1, // Vorwarnung in Tagen
    val note: String = ""
)

@Entity(tableName = "direct_messages")
data class DirectMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long, // Zu welchem Kontakt der Chat gehört
    val targetPeerId: String, // App-ID des tatsächlichen Empfängers/Absenders
    val senderDisplayName: String,
    val direction: String = MessageDirection.OUTGOING.name,
    val text: String,
    val sharedEventTitle: String? = null, // Optional geteilter Sondertermin (App-zu-App)
    val sharedEventDateIso: String? = null,
    val timestampIso: String,
    val isRead: Boolean = true
)

/**
 * Ergebnis der Kontaktkanal-Auflösung.
 * Unterstützt App-zu-App Direkt-Chat (auch ohne Handynummer via appPeerId)
 * sowie Weiterleitung an verknüpfte Bezugspersonen (z.B. Eltern bei Babys) mit explizitem Hinweis.
 */
data class ResolvedContactTarget(
    val targetPersonName: String,
    val phone: String,
    val email: String,
    val appPeerId: String,
    val hasAppDirect: Boolean,
    val isRedirectedToRelative: Boolean,
    val relationDescription: String?,
    val warningNotice: String?
)

object ContactResolver {
    fun resolveTarget(
        person: PersonEntity,
        relationsForPerson: List<Pair<RelationEntity, PersonEntity>>
    ): ResolvedContactTarget {
        val ownPhone = person.phone.trim()
        val ownEmail = person.email.trim()

        // Wenn die Person eine eigene Nummer oder eigene E-Mail besitzt
        if (!person.hasNoOwnPhone && (ownPhone.isNotEmpty() || ownEmail.isNotEmpty())) {
            return ResolvedContactTarget(
                targetPersonName = person.name,
                phone = ownPhone,
                email = ownEmail,
                appPeerId = "",
                hasAppDirect = false,
                isRedirectedToRelative = false,
                relationDescription = null,
                warningNotice = null
            )
        }

        // Suche verknüpfte Bezugsperson mit Telefonnummer oder E-Mail
        val fallbackPair = relationsForPerson.firstOrNull { (rel, target) ->
            rel.isFallbackContact && (target.phone.isNotBlank() || target.email.isNotBlank())
        } ?: relationsForPerson.firstOrNull { (_, target) ->
            target.phone.isNotBlank() || target.email.isNotBlank()
        }

        if (fallbackPair != null) {
            val (rel, relative) = fallbackPair
            return ResolvedContactTarget(
                targetPersonName = relative.name,
                phone = relative.phone.trim(),
                email = relative.email.trim(),
                appPeerId = "",
                hasAppDirect = false,
                isRedirectedToRelative = true,
                relationDescription = "${rel.relationLabel} ${relative.name}",
                warningNotice = "Hinweis: ${person.name} hat keine eigene Nummer. " +
                    "Nachricht wird an ${relative.name} (${rel.relationLabel}) gerichtet."
            )
        }

        return ResolvedContactTarget(
            targetPersonName = person.name,
            phone = "",
            email = ownEmail,
            appPeerId = "",
            hasAppDirect = false,
            isRedirectedToRelative = false,
            relationDescription = null,
            warningNotice = "Hinweis: Keine eigene Telefonnummer/E-Mail und kein erreichbarer verknüpfter Kontakt hinterlegt."
        )
    }

    private fun safeBirthdayInYear(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = Month.of(month).length(Year.isLeap(year.toLong()))
        return LocalDate.of(year, month, day.coerceIn(1, maxDay))
    }

    fun isBirthdayToday(birthdayIso: String?, today: LocalDate = LocalDate.now()): Boolean =
        daysUntilNextBirthday(birthdayIso, today) == 0L

    fun daysUntilNextBirthday(birthdayIso: String?, today: LocalDate = LocalDate.now()): Long? {
        if (birthdayIso.isNullOrBlank()) return null
        return try {
            val parts = birthdayIso.removePrefix("--").split("-")
            val month = if (parts.size == 3) parts[1].toInt() else parts[0].toInt()
            val day = if (parts.size == 3) parts[2].toInt() else parts[1].toInt()
            var nextBday = safeBirthdayInYear(today.year, month, day)
            if (nextBday.isBefore(today)) {
                nextBday = safeBirthdayInYear(today.year + 1, month, day)
            }
            ChronoUnit.DAYS.between(today, nextBday)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gibt die vergangenen Tage seit dem letzten Geburtstag als negativen Wert zurück (z. B. -5 für vor 5 Tagen).
     */
    fun negativeDaysSinceLastBirthday(birthdayIso: String?, today: LocalDate = LocalDate.now()): Long? {
        if (birthdayIso.isNullOrBlank()) return null
        return try {
            val parts = birthdayIso.removePrefix("--").split("-")
            val month = if (parts.size == 3) parts[1].toInt() else parts[0].toInt()
            val day = if (parts.size == 3) parts[2].toInt() else parts[1].toInt()
            var lastBday = safeBirthdayInYear(today.year, month, day)
            if (lastBday.isAfter(today)) {
                lastBday = safeBirthdayInYear(today.year - 1, month, day)
            }
            ChronoUnit.DAYS.between(today, lastBday) // <= 0 (z. B. -5)
        } catch (e: Exception) {
            null
        }
    }

    fun formatUpcomingAge(birthdayIso: String?, today: LocalDate = LocalDate.now()): String {
        if (birthdayIso.isNullOrBlank() || birthdayIso.startsWith("--")) return "Geburtstag"
        return try {
            val birthDate = LocalDate.parse(birthdayIso)
            val totalMonths = ChronoUnit.MONTHS.between(birthDate, today)
            val bdayThisYear = safeBirthdayInYear(today.year, birthDate.monthValue, birthDate.dayOfMonth)
            val nextYearAge = today.year - birthDate.year +
                if (bdayThisYear.isBefore(today)) 1 else 0
            if (totalMonths == 0L && nextYearAge == 0) {
                "Neugeboren (0 Mon.)"
            } else if (totalMonths in 0..23) {
                "$totalMonths Mon. (wird ${maxOf(1, nextYearAge)})"
            } else {
                "wird $nextYearAge"
            }
        } catch (e: Exception) {
            "Geburtstag"
        }
    }

    fun formatPassedAge(birthdayIso: String?, today: LocalDate = LocalDate.now()): String {
        if (birthdayIso.isNullOrBlank() || birthdayIso.startsWith("--")) return "hatte Geburtstag"
        return try {
            val birthDate = LocalDate.parse(birthdayIso)
            var lastBday = safeBirthdayInYear(today.year, birthDate.monthValue, birthDate.dayOfMonth)
            if (lastBday.isAfter(today)) {
                lastBday = safeBirthdayInYear(today.year - 1, birthDate.monthValue, birthDate.dayOfMonth)
            }
            val passedAge = (lastBday.year - birthDate.year).coerceAtLeast(0)
            "wurde $passedAge"
        } catch (e: Exception) {
            "hatte Geburtstag"
        }
    }

    fun nextTimerDate(person: PersonEntity, today: LocalDate = LocalDate.now()): LocalDate? {
        if (!person.timerEnabled || person.timerInterval <= 0) return null
        val base = person.lastContactedIso?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: today
        return when (runCatching { TimerUnit.valueOf(person.timerUnit) }.getOrDefault(TimerUnit.DAYS)) {
            TimerUnit.DAYS -> base.plusDays(person.timerInterval.toLong())
            TimerUnit.WEEKS -> base.plusWeeks(person.timerInterval.toLong())
            TimerUnit.MONTHS -> base.plusMonths(person.timerInterval.toLong())
            TimerUnit.YEARS -> base.plusYears(person.timerInterval.toLong())
        }
    }
}

data class BirthdayCongratulationRecord(
    val personId: Long,
    val year: Int,
    val dateIso: String,
    val channel: String,
    val timeHm: String
)

/**
 * Speichert kanalübergreifend (WhatsApp, SMS, Anruf, Mail, Telegram, Signal, Teilen, Manuell usw.),
 * ob einer Person am laufenden aktuellen Geburtstag bereits gratuliert wurde.
 */
object BirthdayCongratulationTracker {
    private const val PREFS_NAME = "remember_me_prefs"
    private const val PREF_KEY = "birthday_congratulated_records_v1"

    fun loadAllRecords(context: Context): Map<Long, BirthdayCongratulationRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val result = mutableMapOf<Long, BirthdayCongratulationRecord>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val pid = k.toLongOrNull() ?: continue
                val item = obj.optJSONObject(k) ?: continue
                val year = item.optInt("year", 0)
                if (year <= 0) continue
                result[pid] = BirthdayCongratulationRecord(
                    personId = pid,
                    year = year,
                    dateIso = item.optString("dateIso", ""),
                    channel = item.optString("channel", "App").ifBlank { "App" },
                    timeHm = item.optString("timeHm", "")
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun saveAllRecords(context: Context, records: Map<Long, BirthdayCongratulationRecord>) {
        val minKeepYear = LocalDate.now().year - 1
        val obj = JSONObject()
        for ((pid, rec) in records) {
            if (rec.year < minKeepYear) continue
            obj.put(
                pid.toString(),
                JSONObject().apply {
                    put("year", rec.year)
                    put("dateIso", rec.dateIso)
                    put("channel", rec.channel)
                    put("timeHm", rec.timeHm)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, obj.toString())
            .apply()
    }

    fun isOngoingBirthday(birthdayIso: String?, today: LocalDate = LocalDate.now()): Boolean =
        ContactResolver.isBirthdayToday(birthdayIso, today)

    fun getOngoingRecord(
        context: Context,
        person: PersonEntity,
        today: LocalDate = LocalDate.now()
    ): BirthdayCongratulationRecord? {
        if (!isOngoingBirthday(person.birthdayIso, today)) return null
        val rec = loadAllRecords(context)[person.id] ?: return null
        return if (rec.year == today.year) rec else null
    }

    fun markCongratulated(
        context: Context,
        personId: Long,
        channel: String,
        today: LocalDate = LocalDate.now(),
        timeHm: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    ): BirthdayCongratulationRecord {
        val current = loadAllRecords(context).toMutableMap()
        val rec = BirthdayCongratulationRecord(
            personId = personId,
            year = today.year,
            dateIso = today.toString(),
            channel = channel.trim().ifBlank { "App" },
            timeHm = timeHm
        )
        current[personId] = rec
        saveAllRecords(context, current)
        return rec
    }

    fun clearCongratulated(context: Context, personId: Long) {
        val current = loadAllRecords(context).toMutableMap()
        if (current.remove(personId) != null) {
            saveAllRecords(context, current)
        }
    }

    fun mergeRecords(context: Context, primaryId: Long, secondaryIds: Set<Long>) {
        if (secondaryIds.isEmpty()) return
        val current = loadAllRecords(context).toMutableMap()
        if (!current.containsKey(primaryId)) {
            val fromSec = secondaryIds.firstNotNullOfOrNull { current[it] }
            if (fromSec != null) {
                current[primaryId] = fromSec.copy(personId = primaryId)
            }
        }
        secondaryIds.forEach { current.remove(it) }
        saveAllRecords(context, current)
    }

    fun exportToJsonObject(context: Context): JSONObject {
        val obj = JSONObject()
        for ((pid, rec) in loadAllRecords(context)) {
            obj.put(
                pid.toString(),
                JSONObject().apply {
                    put("year", rec.year)
                    put("dateIso", rec.dateIso)
                    put("channel", rec.channel)
                    put("timeHm", rec.timeHm)
                }
            )
        }
        return obj
    }

    fun importFromJsonObject(
        context: Context,
        json: JSONObject?,
        idRemap: Map<Long, Long> = emptyMap()
    ) {
        if (json == null) return
        val current = loadAllRecords(context).toMutableMap()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val oldPid = k.toLongOrNull() ?: continue
            val targetPid = idRemap[oldPid] ?: oldPid
            val item = json.optJSONObject(k) ?: continue
            val year = item.optInt("year", 0)
            if (year <= 0) continue
            current[targetPid] = BirthdayCongratulationRecord(
                personId = targetPid,
                year = year,
                dateIso = item.optString("dateIso", ""),
                channel = item.optString("channel", "App").ifBlank { "App" },
                timeHm = item.optString("timeHm", "")
            )
        }
        saveAllRecords(context, current)
    }
}
