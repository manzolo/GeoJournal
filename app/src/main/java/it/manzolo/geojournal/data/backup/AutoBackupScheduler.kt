package it.manzolo.geojournal.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AutoBackupScheduler"

/**
 * Schedula il backup giornaliero alle 02:00 tramite [AlarmManager.setExactAndAllowWhileIdle].
 *
 * Perché non [androidx.work.PeriodicWorkRequest]: su Android moderno il bucket App Standby
 * e Doze deep ritardano o saltano i periodic work quando l'app resta in background per
 * qualche giorno, specialmente con constraint multipli (charging+network). L'alarm esatto
 * con flag AllowWhileIdle sveglia il device anche in Doze ed è il pattern standard per
 * job giornalieri affidabili (stesso meccanismo usato da [ReminderScheduler]).
 *
 * Flusso: alarm → [AutoBackupBroadcastReceiver] → [AutoBackupWorker] (OneTimeWork con
 * constraint di sola rete) → ri-schedula il prossimo alarm.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ACTION_AUTO_BACKUP_FIRE = "it.manzolo.geojournal.AUTO_BACKUP_FIRE"
        private const val REQUEST_CODE = 1001
        private const val BACKUP_HOUR = 2
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun buildPendingIntent(flags: Int): PendingIntent? {
        val intent = Intent(context, AutoBackupBroadcastReceiver::class.java).apply {
            action = ACTION_AUTO_BACKUP_FIRE
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerMillis(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, BACKUP_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    fun schedule() {
        val pi = buildPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = nextTriggerMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "canScheduleExactAlarms=false, fallback a setAndAllowWhileIdle")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.d(TAG, "Auto-backup alarm schedulato per $triggerAt")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException su setExactAndAllowWhileIdle, fallback inexact", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun reschedule() {
        cancel()
        schedule()
    }

    fun cancel() {
        val pi = buildPendingIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }
}
