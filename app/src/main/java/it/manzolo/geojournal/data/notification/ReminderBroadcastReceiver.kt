package it.manzolo.geojournal.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.worker.RescheduleWorker
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ReminderBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<RescheduleWorker>().build()
                )
            }
            ACTION_REMINDER_FIRE -> {
                val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TITLE) ?: return
                val type = intent.getStringExtra(EXTRA_TYPE) ?: return
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
                val endDate = intent.getLongExtra(EXTRA_END_DATE, -1L).takeIf { it > 0 }
                val geoPointId = intent.getStringExtra(EXTRA_GEO_POINT_ID)

                showNotification(context, notifId, title, type, endDate, geoPointId)

                if (type == ReminderType.ANNUAL_RECURRING.name) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val reminder = reminderRepository.getActiveReminders()
                                .firstOrNull { it.id == reminderId }
                            reminder?.let { scheduler.scheduleReminder(it) }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        notifId: Int,
        title: String,
        type: String,
        endDate: Long?,
        geoPointId: String?
    ) {
        val dateFormat = SimpleDateFormat("d MMM", Locale.ITALIAN)
        val body = when {
            type == ReminderType.DATE_RANGE.name && endDate != null ->
                "Periodo fino al ${dateFormat.format(Date(endDate))}"
            type == ReminderType.ANNUAL_RECURRING.name -> "Promemoria annuale"
            else -> "Promemoria"
        }

        val contentIntent = if (geoPointId != null) {
            val openIntent = Intent(context, Class.forName("it.manzolo.geojournal.MainActivity")).apply {
                action = ACTION_OPEN_POINT
                putExtra(EXTRA_GEO_POINT_ID, geoPointId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context, notifId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        contentIntent?.let { builder.setContentIntent(it) }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())
    }

    companion object {
        const val ACTION_REMINDER_FIRE = "it.manzolo.geojournal.REMINDER_FIRE"
        const val ACTION_OPEN_POINT = "it.manzolo.geojournal.OPEN_POINT"
        const val CHANNEL_ID = "reminders"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_NOTIF_ID = "notif_id"
        private const val EXTRA_END_DATE = "end_date"
        const val EXTRA_GEO_POINT_ID = "geo_point_id"

        fun buildIntent(context: Context, reminder: Reminder): Intent =
            Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_REMINDER_FIRE
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_TYPE, reminder.type.name)
                putExtra(EXTRA_NOTIF_ID, reminder.notificationId)
                putExtra(EXTRA_GEO_POINT_ID, reminder.geoPointId)
                reminder.endDate?.let { putExtra(EXTRA_END_DATE, it) }
            }
    }
}
