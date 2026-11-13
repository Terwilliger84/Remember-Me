package de.kontaktchronik.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.model.BirthdayCongratulationTracker
import de.kontaktchronik.app.model.ContactResolver
import de.kontaktchronik.app.model.PersonEntity
import de.kontaktchronik.app.model.TimerUnit
import de.kontaktchronik.app.ui.MainActivity
import de.kontaktchronik.app.widget.RememberMeWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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

    fun openEmail(context: Context, email: String, subject: String = "", body: String = "") {
        val clean = email.trim()
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$clean")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, body)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openSystemShare(context: Context, text: String, chooserTitle: String = "Teilen") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
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

/**
 * Verwaltet Handy-Popup-Benachrichtigungen (Heads-Up Notifications) für Geburtstage.
 * In den Optionen jederzeit aktivierbar / deaktivierbar ("birthday_popup_enabled").
 */
object BirthdayNotificationHelper {
    private const val PREFS_NAME = "remember_me_prefs"
    const val PREF_BIRTHDAY_POPUP_ENABLED = "birthday_popup_enabled"
    const val PREF_BIRTHDAY_POPUP_HOUR = "birthday_popup_hour"
    const val PREF_BIRTHDAY_POPUP_MINUTE = "birthday_popup_minute"
    private const val PREF_ACTIVE_NOTIF_IDS = "active_birthday_notif_ids"
    private const val NOTIFIED_KEY_PREFIX = "notified_bday_ids_"

    const val CHANNEL_BIRTHDAY_POPUP = "remember_me_birthday_popup_v2"
    private const val ALARM_REQUEST_CODE = 840_100
    private const val BIRTHDAY_NOTIF_BASE_ID = 500_000
    private const val TEST_NOTIF_ID = 499_999

    fun isBirthdayPopupEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_BIRTHDAY_POPUP_ENABLED, true)
    }

    fun getNotificationTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(PREF_BIRTHDAY_POPUP_HOUR, 8).coerceIn(0, 23)
        val minute = prefs.getInt(PREF_BIRTHDAY_POPUP_MINUTE, 30).coerceIn(0, 59)
        return hour to minute
    }

    fun setNotificationTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(PREF_BIRTHDAY_POPUP_HOUR, hour.coerceIn(0, 23))
            .putInt(PREF_BIRTHDAY_POPUP_MINUTE, minute.coerceIn(0, 59))
            .apply()
        if (isBirthdayPopupEnabled(context)) {
            scheduleDailyBirthdayAlarm(context)
        }
    }

    fun setBirthdayPopupEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_BIRTHDAY_POPUP_ENABLED, enabled).apply()
        if (enabled) {
            ensureNotificationChannel(context)
            scheduleDailyBirthdayAlarm(context)
            checkAndNotifyTodaysBirthdaysAsync(context)
        } else {
            cancelDailyBirthdayAlarm(context)
            cancelActiveBirthdayNotifications(context)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_BIRTHDAY_POPUP,
            "🎂 Geburtstags-Popup",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Handy-Popup-Benachrichtigung am Geburtstag eines Kontakts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 280, 140, 280)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (soundUri != null) {
                setSound(soundUri, audioAttrs)
            }
        }
        nm.createNotificationChannel(channel)
    }

    fun scheduleDailyBirthdayAlarm(context: Context) {
        if (!isBirthdayPopupEnabled(context)) {
            cancelDailyBirthdayAlarm(context)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (hour, minute) = getNotificationTime(context)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var nextTrigger = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!nextTrigger.isAfter(now)) {
            nextTrigger = nextTrigger.plusDays(1)
        }
        val triggerMillis = nextTrigger.toInstant().toEpochMilli()

        val intent = Intent(context, BirthdayAlarmReceiver::class.java).apply {
            action = BirthdayAlarmReceiver.ACTION_DAILY_BIRTHDAY_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    fun cancelDailyBirthdayAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BirthdayAlarmReceiver::class.java).apply {
            action = BirthdayAlarmReceiver.ACTION_DAILY_BIRTHDAY_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancelBirthdayNotificationForPerson(context: Context, personId: Long) {
        if (personId <= 0L) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = BIRTHDAY_NOTIF_BASE_ID + (personId % 400_000L).toInt()
        nm.cancel(notifId)
    }

    fun cancelActiveBirthdayNotifications(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeIds = prefs.getStringSet(PREF_ACTIVE_NOTIF_IDS, emptySet()).orEmpty()
        activeIds.mapNotNull { it.toIntOrNull() }.forEach { nm.cancel(it) }
        nm.cancel(TEST_NOTIF_ID)
        prefs.edit().remove(PREF_ACTIVE_NOTIF_IDS).apply()
    }

    fun checkAndNotifyTodaysBirthdaysAsync(context: Context) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            checkAndNotifyTodaysBirthdays(appCtx)
        }
    }

    suspend fun checkAndNotifyTodaysBirthdays(context: Context): Int {
        if (!isBirthdayPopupEnabled(context) || !hasNotificationPermission(context)) {
            return 0
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hiddenIds = prefs.getStringSet("hidden_person_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        val today = LocalDate.now()
        val todayKey = "$NOTIFIED_KEY_PREFIX$today"
        // Veraltete Tages-Keys bereinigen
        val staleKeys = prefs.all.keys.filter { it.startsWith(NOTIFIED_KEY_PREFIX) && it != todayKey }
        if (staleKeys.isNotEmpty()) {
            val cleanEditor = prefs.edit()
            staleKeys.forEach { cleanEditor.remove(it) }
            cleanEditor.apply()
        }

        val alreadyNotified = prefs.getStringSet(todayKey, emptySet()).orEmpty().toMutableSet()
        val db = AppDatabase.getInstance(context).dao()
        val persons = db.getAllPersonsOnce()

        var newlySent = 0
        for (person in persons) {
            if (person.isHidden || hiddenIds.contains(person.id)) continue
            val bdayDays = ContactResolver.daysUntilNextBirthday(person.birthdayIso, today)
            if (bdayDays == 0L) {
                if (BirthdayCongratulationTracker.getOngoingRecord(context, person, today) != null) {
                    continue
                }
                val idToken = person.id.toString()
                if (alreadyNotified.add(idToken)) {
                    val ageInfo = ContactResolver.formatUpcomingAge(person.birthdayIso, today)
                    showBirthdayPopupNotification(
                        context = context,
                        person = person,
                        ageInfo = ageInfo,
                        isTest = false
                    )
                    newlySent++
                }
            }
        }
        if (newlySent > 0) {
            prefs.edit().putStringSet(todayKey, alreadyNotified).apply()
        }
        return newlySent
    }

    fun sendTestBirthdayPopup(context: Context, samplePerson: PersonEntity?) {
        if (!hasNotificationPermission(context)) return
        val person = samplePerson ?: PersonEntity(
            id = 0L,
            name = "Max Mustermann",
            birthdayIso = LocalDate.now().minusYears(30).toString()
        )
        val ageInfo = ContactResolver.formatUpcomingAge(person.birthdayIso, LocalDate.now())
        showBirthdayPopupNotification(
            context = context,
            person = person,
            ageInfo = ageInfo,
            isTest = true
        )
    }

    private fun decodeAvatarBitmap(photoBase64: String): Bitmap? {
        val raw = photoBase64.trim()
        if (raw.isBlank() || raw.startsWith("SVG:")) return null
        val payload = raw.substringAfter("base64,", raw)
        return runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun showBirthdayPopupNotification(
        context: Context,
        person: PersonEntity,
        ageInfo: String,
        isTest: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cleanAge = ageInfo.trim()
        val hasAgeNumber = cleanAge.isNotBlank() && !cleanAge.equals("Alter unbekannt", ignoreCase = true)
        val reasonText = if (hasAgeNumber) {
            "🎂 Heute Geburtstag ($cleanAge)"
        } else {
            "🎂 Heute Geburtstag"
        }
        val titleText = if (isTest) {
            "🎂 Geburtstags-Popup (Test): ${person.name}"
        } else {
            "🎂 Heute Geburtstag: ${person.name}"
        }
        val bodyText = if (hasAgeNumber) {
            "${person.name} hat heute Geburtstag ($cleanAge)! 🎉 Tippe hier, um direkt zu gratulieren."
        } else {
            "${person.name} hat heute Geburtstag! 🎉 Tippe hier, um direkt zu gratulieren."
        }

        val notifId = if (isTest) TEST_NOTIF_ID else (BIRTHDAY_NOTIF_BASE_ID + (person.id % 400_000L).toInt())

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (person.id > 0L) {
                putExtra("POPUP_PERSON_ID", person.id)
                putExtra("POPUP_REASON", reasonText)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_BIRTHDAY_POPUP)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText).setBigContentTitle(titleText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "🎉 Jetzt gratulieren",
                pendingIntent
            )

        decodeAvatarBitmap(person.photoBase64)?.let { bmp ->
            builder.setLargeIcon(bmp)
        }

        nm.notify(notifId, builder.build())

        if (!isTest) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val active = prefs.getStringSet(PREF_ACTIVE_NOTIF_IDS, emptySet()).orEmpty() + notifId.toString()
            prefs.edit().putStringSet(PREF_ACTIVE_NOTIF_IDS, active).apply()
        }
    }
}

class BirthdayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (BirthdayNotificationHelper.isBirthdayPopupEnabled(appCtx)) {
                    BirthdayNotificationHelper.checkAndNotifyTodaysBirthdays(appCtx)
                    BirthdayNotificationHelper.scheduleDailyBirthdayAlarm(appCtx)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_BIRTHDAY_CHECK = "de.kontaktchronik.app.ACTION_DAILY_BIRTHDAY_CHECK"
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
        val prefs = applicationContext.getSharedPreferences("remember_me_prefs", Context.MODE_PRIVATE)
        val hiddenIds = prefs.getStringSet("hidden_person_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        // 1. Geburtstags-Popup prüfen (respektiert Option "birthday_popup_enabled" & sendet max. 1x pro Tag/Kontakt)
        if (BirthdayNotificationHelper.isBirthdayPopupEnabled(applicationContext)) {
            BirthdayNotificationHelper.checkAndNotifyTodaysBirthdays(applicationContext)
        }

        for (person in persons) {
            if (person.isHidden || hiddenIds.contains(person.id)) continue

            // 2. Melde-Timer prüfen
            val nextTimer = ContactResolver.nextTimerDate(person, today)
            if (nextTimer != null && !nextTimer.isAfter(today)) {
                val unitDe = runCatching { TimerUnit.valueOf(person.timerUnit).label }.getOrDefault("Tage")
                showPopupNotification(person.id, person.name, "Melde-Timer fällig (${person.timerInterval} $unitDe)")
            }
        }

        // 3. Erinnerungen prüfen (Einmalig, Wöchentlich & frei wählbarer Rhythmus REPEAT:<count>:<unit>:<date>)
        for (ev in events) {
            val person = persons.find { it.id == ev.personId && !it.isHidden && !hiddenIds.contains(it.id) } ?: continue
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
                    if (targetDow != null && targetDow in 1..7) {
                        val currentDow = today.dayOfWeek.value // 1=Mo..7=So
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

        RememberMeWidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    private fun showPopupNotification(personId: Long, title: String, message: String) {
        if (!BirthdayNotificationHelper.hasNotificationPermission(applicationContext)) return
        val channelId = "kontakt_chronik_popup"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Kontakt- & Timer-Erinnerungen",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val fullScreenIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            DailyReminderWorker.schedule(context)
            if (BirthdayNotificationHelper.isBirthdayPopupEnabled(context)) {
                BirthdayNotificationHelper.scheduleDailyBirthdayAlarm(context)
                BirthdayNotificationHelper.checkAndNotifyTodaysBirthdaysAsync(context)
            }
        }
    }
}
