package it.manzolo.geojournal.data.tracking

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.kml.KmlWriter
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var trackingManager: TrackingManager
    @Inject lateinit var kmlRepository: PointKmlRepository

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_START = "it.manzolo.geojournal.tracking.START"
        const val ACTION_STOP = "it.manzolo.geojournal.tracking.STOP"
        const val EXTRA_GEO_POINT_ID = "geo_point_id"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "tracking"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val geoPointId: String? = intent.getStringExtra(EXTRA_GEO_POINT_ID)
                startForegroundNotification()
                trackingManager.startTracking(geoPointId)
                startLocationUpdates()
            }
            ACTION_STOP -> stopAndSave()
        }
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trackingManager.addCoordinate(loc.latitude, loc.longitude)
                    updateNotificationText()
                }
            }
        }
        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopAndSave() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null

        val (geoPointId, coords) = trackingManager.stopTrackingAndCollect()
        scope.launch {
            if (coords.size >= 2) {
                val name = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
                val content = KmlWriter.buildTrackKml(name, coords)
                if (content != null) {
                    if (geoPointId != null) {
                        runCatching { kmlRepository.saveKml(geoPointId, "$name.kml", content) }
                    } else {
                        trackingManager.setPendingTrackResult(
                            PendingTrackResult(
                                kmlContent = content,
                                pointCount = coords.size,
                                name = name,
                                firstCoord = coords.firstOrNull(),
                                lastCoord = coords.lastOrNull()
                            )
                        )
                    }
                }
            }
            stopSelf()
        }
    }

    private fun buildStopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.tracking_notif_title))
        .setContentText(text)
        .setOngoing(true)
        .addAction(R.drawable.ic_notification, getString(R.string.tracking_action_stop), buildStopPendingIntent())
        .build()

    private fun startForegroundNotification() {
        val notification = buildNotification(getString(R.string.tracking_notif_text))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationText() {
        val count = trackingManager.state.value.pointCount
        val text = getString(R.string.tracking_point_count, count)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopAndSave()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
