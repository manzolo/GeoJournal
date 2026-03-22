package it.manzolo.geojournal.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderScheduler"

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Permesso SCHEDULE_EXACT_ALARM non concesso → fallback a setAndAllowWhileIdle
            Log.w(TAG, "canScheduleExactAlarms=false, usando setAndAllowWhileIdle (inexact)")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                Log.d(TAG, "Alarm esatto schedulato per reminder=${reminder.id} triggerAt=$triggerAt")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException su setExactAndAllowWhileIdle, fallback inexact", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
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

        // Usa ora/minuto salvati nel startDate; fallback a 9:00 per reminder vecchi (00:00)
        val storedHour = startCal.get(Calendar.HOUR_OF_DAY)
        val storedMinute = startCal.get(Calendar.MINUTE)
        val triggerHour = if (storedHour == 0 && storedMinute == 0) 9 else storedHour
        val triggerMinute = if (storedHour == 0 && storedMinute == 0) 0 else storedMinute

        val trigger = Calendar.getInstance().apply {
            set(Calendar.MONTH, startCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, startCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, triggerHour)
            set(Calendar.MINUTE, triggerMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (reminder.type) {
            ReminderType.SINGLE, ReminderType.DATE_RANGE -> {
                trigger.set(Calendar.YEAR, startCal.get(Calendar.YEAR))
                if (trigger.timeInMillis > now) trigger.timeInMillis else null
            }
            ReminderType.ANNUAL_RECURRING -> {
                // Se il trigger è già passato, programma per l'anno prossimo
                // (non usare +60s per evitare loop infinito dopo lo scatto)
                if (trigger.timeInMillis > now) trigger.timeInMillis
                else { trigger.add(Calendar.YEAR, 1); trigger.timeInMillis }
            }
        }
    }
}
