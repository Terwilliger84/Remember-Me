package de.kontaktchronik.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
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

    fun daysUntilNextBirthday(birthdayIso: String?, today: LocalDate = LocalDate.now()): Long? {
        if (birthdayIso.isNullOrBlank()) return null
        return try {
            val parts = birthdayIso.removePrefix("--").split("-")
            val month = if (parts.size == 3) parts[1].toInt() else parts[0].toInt()
            val day = if (parts.size == 3) parts[2].toInt() else parts[1].toInt()
            var nextBday = LocalDate.of(today.year, month, day)
            if (nextBday.isBefore(today)) {
                nextBday = nextBday.plusYears(1)
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
            var lastBday = LocalDate.of(today.year, month, day)
            if (lastBday.isAfter(today)) {
                lastBday = lastBday.minusYears(1)
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
            val nextYearAge = today.year - birthDate.year +
                if (LocalDate.of(today.year, birthDate.month, birthDate.dayOfMonth).isBefore(today)) 1 else 0
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
            var lastBday = LocalDate.of(today.year, birthDate.month, birthDate.dayOfMonth)
            if (lastBday.isAfter(today)) {
                lastBday = lastBday.minusYears(1)
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
