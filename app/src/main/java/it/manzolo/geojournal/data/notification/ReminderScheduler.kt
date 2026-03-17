package it.manzolo.geojournal.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(reminder: Reminder) {
        if (!reminder.isActive) return
        val triggerAt = nextTriggerMillis(reminder) ?: return
        val intent = ReminderBroadcastReceiver.buildIntent(context, reminder)
        val pi = PendingIntent.getBroadcast(
            context,
            reminder.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelReminder(reminder: Reminder) {
        val intent = ReminderBroadcastReceiver.buildIntent(context, reminder)
        val pi = PendingIntent.getBroadcast(
            context,
            reminder.notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
    }

    fun rescheduleAll(reminders: List<Reminder>) {
        reminders.forEach { scheduleReminder(it) }
    }

    private fun nextTriggerMillis(reminder: Reminder): Long? {
        val startCal = Calendar.getInstance().apply { timeInMillis = reminder.startDate }
        val now = System.currentTimeMillis()
        val todayCal = Calendar.getInstance()

        val trigger = Calendar.getInstance().apply {
            set(Calendar.MONTH, startCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, startCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Controlla se startDate è oggi (giorno/mese/anno coincidono)
        val isStartDateToday =
            startCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            startCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
            startCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)

        // Per ANNUAL: "oggi" = stesso giorno/mese indipendentemente dall'anno
        val isAnnualToday =
            startCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
            startCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)

        return when (reminder.type) {
            ReminderType.SINGLE, ReminderType.DATE_RANGE -> {
                trigger.set(Calendar.YEAR, startCal.get(Calendar.YEAR))
                when {
                    trigger.timeInMillis > now -> trigger.timeInMillis
                    isStartDateToday -> now + 60_000L  // oggi ma 9:00 già passate → tra 1 minuto
                    else -> null                        // data passata, non schedulare
                }
            }
            ReminderType.ANNUAL_RECURRING -> {
                when {
                    trigger.timeInMillis > now -> trigger.timeInMillis
                    isAnnualToday -> now + 60_000L      // impostato oggi → tra 1 minuto
                    else -> { trigger.add(Calendar.YEAR, 1); trigger.timeInMillis }
                }
            }
        }
    }
}
