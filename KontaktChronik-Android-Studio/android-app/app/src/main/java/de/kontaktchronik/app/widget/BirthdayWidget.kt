package de.kontaktchronik.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import de.kontaktchronik.app.data.AppDatabase
import de.kontaktchronik.app.model.ContactResolver
import de.kontaktchronik.app.ui.MainActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WidgetRowItem(
    val personId: Long,
    val name: String,
    val subtitle: String,
    val badge: String
)

class BirthdayGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.getInstance(context).dao()
        val persons = dao.getAllPersonsOnce()
        val events = dao.getAllSpecialEventsOnce()
        val today = LocalDate.now()

        val items = mutableListOf<Pair<Long, WidgetRowItem>>()

        for (p in persons) {
            val bdayDays = ContactResolver.daysUntilNextBirthday(p.birthdayIso, today)
            if (bdayDays != null && bdayDays <= 60) {
                items += bdayDays to WidgetRowItem(
                    personId = p.id,
                    name = p.name,
                    subtitle = "Geburtstag (${ContactResolver.formatUpcomingAge(p.birthdayIso, today)})",
                    badge = if (bdayDays == 0L) "Heute" else "in ${bdayDays}T"
                )
            }
            val timerDate = ContactResolver.nextTimerDate(p, today)
            if (timerDate != null) {
                val diff = ChronoUnit.DAYS.between(today, timerDate)
                if (diff <= 14) {
                    items += diff to WidgetRowItem(
                        personId = p.id,
                        name = p.name,
                        subtitle = "Melde-Timer",
                        badge = if (diff <= 0L) "Fällig" else "in ${diff}T"
                    )
                }
            }
        }

        for (ev in events) {
            val d = runCatching { LocalDate.parse(ev.dateIso) }.getOrNull() ?: continue
            val diff = ChronoUnit.DAYS.between(today, d)
            if (diff in 0..45) {
                val person = persons.find { it.id == ev.personId } ?: continue
                items += diff to WidgetRowItem(
                    personId = person.id,
                    name = person.name,
                    subtitle = ev.title,
                    badge = if (diff == 0L) "Heute" else "in ${diff}T"
                )
            }
        }

        val sorted = items.sortedBy { it.first }.take(5).map { it.second }

        provideContent {
            WidgetContent(context, sorted)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, rows: List<WidgetRowItem>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF0F172A)))
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Anstehende Termine",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF8FAFC)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))

            if (rows.isEmpty()) {
                Text(
                    text = "Keine anstehenden Einträge",
                    style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 12.sp)
                )
            } else {
                rows.forEach { item ->
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra("POPUP_PERSON_ID", item.personId)
                        putExtra("POPUP_REASON", item.subtitle)
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(actionStartActivity(launchIntent)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = item.name,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFF8FAFC)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = item.subtitle,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF94A3B8)),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Text(
                            text = item.badge,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFCBD5E1)),
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

class BirthdayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BirthdayGlanceWidget()
}
