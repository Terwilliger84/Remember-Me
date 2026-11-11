package de.kontaktchronik.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.model.ContactResolver
import de.kontaktchronik.app.model.TimerUnit
import de.kontaktchronik.app.ui.MainActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Startet sofortige Kommunikations-Intents für WhatsApp, Anruf, SMS, Telegram, Signal und E-Mail.
 * E-Mail wird nur angeboten und ausgeführt, wenn eine E-Mail-Adresse hinterlegt ist.
 */
object QuickActionDispatcher {

    private fun sanitizePhone(phone: String): String =
        phone.replace(Regex("[^0-9+]"), "")

    fun startPhoneCall(context: Context, phone: String) {
        val clean = sanitizePhone(phone)
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openWhatsApp(context: Context, phone: String, presetText: String = "") {
        val clean = sanitizePhone(phone).removePrefix("+")
        if (clean.isEmpty()) return
        val encoded = Uri.encode(presetText)
        val uri = Uri.parse("https://wa.me/$clean?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSms(context: Context, phone: String, presetText: String = "") {
        val clean = sanitizePhone(phone)
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).apply {
            putExtra("sms_body", presetText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openTelegram(context: Context, phone: String) {
        val clean = sanitizePhone(phone).removePrefix("+")
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSignal(context: Context, phone: String) {
        val clean = sanitizePhone(phone)
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sgnl://signal.me/#p/$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            openSms(context, phone)
        }
    }

    fun openEmail(context: Context, email: String, subject: String = "") {
        val clean = email.trim()
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$clean")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openExtraMessenger(context: Context, channel: String, phone: String, presetText: String = "") {
        when (channel) {
            "Telegram" -> openTelegram(context, phone)
            "Signal" -> openSignal(context, phone)
            else -> {
                val pkg = when (channel) {
                    "Threema" -> "ch.threema.app"
                    "Messenger" -> "com.facebook.orca"
                    "Instagram" -> "com.instagram.android"
                    "Viber" -> "com.viber.voip"
                    else -> null
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, presetText)
                    if (pkg != null) setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(shareIntent) }.onFailure {
                    val fallback = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, presetText)
                        },
                        channel
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    runCatching { context.startActivity(fallback) }
                }
            }
        }
    }
}

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext).dao()
        val persons = db.getAllPersonsOnce()
        val events = db.getAllSpecialEventsOnce()
        val today = LocalDate.now()

        for (person in persons) {
            if (person.isHidden) continue
            // 1. Geburtstag prüfen
            val bdayDays = ContactResolver.daysUntilNextBirthday(person.birthdayIso, today)
            if (bdayDays != null && (bdayDays == 0L || bdayDays == 1L)) {
                val label = if (bdayDays == 0L) "Heute Geburtstag" else "Morgen Geburtstag"
                showPopupNotification(person.id, person.name, "$label (${ContactResolver.formatUpcomingAge(person.birthdayIso, today)})")
            }

            // 2. Melde-Timer prüfen
            val nextTimer = ContactResolver.nextTimerDate(person, today)
            if (nextTimer != null && !nextTimer.isAfter(today)) {
                val unitDe = runCatching { TimerUnit.valueOf(person.timerUnit).label }.getOrDefault("Tage")
                showPopupNotification(person.id, person.name, "Melde-Timer fällig (${person.timerInterval} $unitDe)")
            }
        }

        // 3. Erinnerungen prüfen (Einmalig, Wöchentlich & frei wählbarer Rhythmus REPEAT:<count>:<unit>:<date>)
        for (ev in events) {
            val person = persons.find { it.id == ev.personId && !it.isHidden } ?: continue
            val rawSpec = ev.dateIso.trim()
            val evDate: LocalDate? = when {
                rawSpec.startsWith("REPEAT:") -> {
                    val parts = rawSpec.split(":")
                    if (parts.size >= 4) {
                        val count = parts[1].toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                        val unit = parts[2]
                        var nextD = runCatching { LocalDate.parse(parts[3]) }.getOrNull() ?: today
                        var advanced = false
                        var guard = 0
                        while (nextD.isBefore(today) && guard < 500) {
                            nextD = when (unit) {
                                "WEEKS" -> nextD.plusWeeks(count)
                                "MONTHS" -> nextD.plusMonths(count)
                                "YEARS" -> nextD.plusYears(count)
                                else -> nextD.plusDays(count)
                            }
                            advanced = true
                            guard++
                        }
                        if (advanced) {
                            db.insertSpecialEvent(ev.copy(dateIso = "REPEAT:$count:$unit:$nextD"))
                        }
                        nextD
                    } else null
                }
                rawSpec.startsWith("WEEKLY:") -> {
                    val targetDow = rawSpec.removePrefix("WEEKLY:").toIntOrNull()
                    if (targetDow != null) {
                        val currentDow = today.dayOfWeek.value % 7 // 0=So..6=Sa
                        val delta = ((targetDow - currentDow) + 7) % 7
                        today.plusDays(delta.toLong())
                    } else null
                }
                else -> runCatching { LocalDate.parse(rawSpec) }.getOrNull()
            } ?: continue

            val diff = ChronoUnit.DAYS.between(today, evDate)
            if (diff == 0L || diff == ev.leadDays.toLong()) {
                val whenLabel = if (diff == 0L) "Heute" else "In $diff Tagen"
                showPopupNotification(person.id, person.name, "Erinnerung ($whenLabel): ${ev.title}")
            }
        }

        return Result.success()
    }

    private fun showPopupNotification(personId: Long, title: String, message: String) {
        val channelId = "kontakt_chronik_popup"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Kontakt- & Geburtstagserinnerungen",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val fullScreenIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("POPUP_PERSON_ID", personId)
            putExtra("POPUP_REASON", message)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            personId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(personId.toInt() + 1000, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyReminderWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_contact_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DailyReminderWorker.schedule(context)
        }
    }
}
