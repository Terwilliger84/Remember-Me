package de.kontaktchronik.app.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.kontaktchronik.app.data.KontaktChronikDao
import de.kontaktchronik.app.model.DirectMessageEntity
import de.kontaktchronik.app.model.MessageDirection
import de.kontaktchronik.app.model.PersonEntity
import de.kontaktchronik.app.model.ResolvedContactTarget
import de.kontaktchronik.app.model.SpecialEventEntity
import de.kontaktchronik.app.reminders.QuickActionDispatcher
import de.kontaktchronik.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Verwaltet die App-zu-App-Direktnachrichten, Code-/QR-Kopplung (auch ohne Handynummer),
 * das direkte Teilen von Sonderterminen (z. B. "Komme am 15.11. zu Besuch") sowie
 * den automatischen Fallback auf WhatsApp/SMS, falls das Gegenüber die App noch nicht gekoppelt hat.
 */
class DirectMessagingService(
    private val context: Context,
    private val dao: KontaktChronikDao
) {

    fun getOwnPairingCode(): String {
        val prefs = context.getSharedPreferences("kontakt_chronik_prefs", Context.MODE_PRIVATE)
        var code = prefs.getString("own_pairing_code", null)
        if (code == null) {
            val randomSuffix = (1000..9999).random()
            code = "KC-ME-$randomSuffix"
            prefs.edit().putString("own_pairing_code", code).apply()
        }
        return code
    }

    fun buildInviteDeepLink(ownCode: String = getOwnPairingCode()): String {
        return "kontaktchronik://pair?code=$ownCode"
    }

    /**
     * Koppelt einen Kontakt über dessen persönlichen App-Code (funktioniert auch auf
     * Kinder-Tablets oder bei Großeltern ohne eigene Handynummer).
     */
    suspend fun pairPersonWithCode(person: PersonEntity, peerCode: String) {
        dao.updatePerson(person.copy(appPeerId = peerCode.trim().uppercase()))
    }

    /**
     * Sendet eine Nachricht bevorzugt direkt von App zu App.
     * Falls der Kontakt (oder dessen verknüpfte Bezugsperson) noch keine App-Kopplung hat,
     * greift automatisch der Fallback auf WhatsApp oder SMS.
     */
    suspend fun sendDirectOrFallback(
        person: PersonEntity,
        resolved: ResolvedContactTarget,
        text: String,
        sharedEventTitle: String? = null,
        sharedEventDateIso: String? = null
    ): Boolean {
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        if (resolved.hasAppDirect) {
            dao.insertDirectMessage(
                DirectMessageEntity(
                    personId = person.id,
                    targetPeerId = resolved.appPeerId,
                    senderDisplayName = "Ich",
                    direction = MessageDirection.OUTGOING.name,
                    text = text,
                    sharedEventTitle = sharedEventTitle,
                    sharedEventDateIso = sharedEventDateIso,
                    timestampIso = nowStr,
                    isRead = true
                )
            )
            dao.updatePerson(person.copy(lastContactedIso = LocalDate.now().toString()))
            return true
        }

        // Automatischer Fallback auf WhatsApp / SMS wenn keine App-ID hinterlegt ist
        val composedPayload = if (sharedEventTitle != null && sharedEventDateIso != null) {
            "$text [Termin: $sharedEventTitle am $sharedEventDateIso]"
        } else {
            text
        }
        if (resolved.phone.isNotBlank()) {
            QuickActionDispatcher.openWhatsApp(context, resolved.phone, composedPayload)
        }
        return false
    }

    /**
     * Verarbeitet eine eingehende App-zu-App-Nachricht (inkl. optional geteiltem Sondertermin)
     * und lässt sofort das Kontakt-Pop-up mit Schnell-Antwort aufploppen.
     */
    suspend fun onIncomingPeerMessage(
        senderPerson: PersonEntity,
        text: String,
        sharedEventTitle: String? = null,
        sharedEventDateIso: String? = null
    ) {
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        dao.insertDirectMessage(
            DirectMessageEntity(
                personId = senderPerson.id,
                targetPeerId = senderPerson.appPeerId.ifBlank { "KC-PEER" },
                senderDisplayName = senderPerson.name,
                direction = MessageDirection.INCOMING.name,
                text = text,
                sharedEventTitle = sharedEventTitle,
                sharedEventDateIso = sharedEventDateIso,
                timestampIso = nowStr,
                isRead = false
            )
        )

        if (!sharedEventTitle.isNullOrBlank() && !sharedEventDateIso.isNullOrBlank()) {
            dao.insertSpecialEvent(
                SpecialEventEntity(
                    personId = senderPerson.id,
                    title = sharedEventTitle,
                    dateIso = sharedEventDateIso,
                    leadDays = 1,
                    note = "Per App-Direktnachricht empfangen"
                )
            )
        }

        triggerIncomingMessagePopup(senderPerson, text, sharedEventTitle, sharedEventDateIso)
    }

    private fun triggerIncomingMessagePopup(
        sender: PersonEntity,
        text: String,
        sharedEventTitle: String?,
        sharedEventDateIso: String?
    ) {
        val channelId = "kontakt_chronik_direct_msg"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                "App-zu-App Direktnachrichten",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val reason = if (sharedEventTitle != null) {
            "App-Direkt: $text (Termin: $sharedEventTitle am $sharedEventDateIso)"
        } else {
            "App-Direkt: $text"
        }

        val popupIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("POPUP_PERSON_ID", sender.id)
            putExtra("POPUP_REASON", reason)
        }
        val pi = PendingIntent.getActivity(
            context,
            sender.id.toInt() + 5000,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Nachricht von ${sender.name}")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(sender.id.toInt() + 5000, notification)
    }
}
