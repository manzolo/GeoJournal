package it.manzolo.geojournal.domain.model

import java.util.UUID
import kotlin.math.abs

enum class ReminderType {
    SINGLE,           // Una volta sola
    ANNUAL_RECURRING, // Ogni anno alla stessa data
    DATE_RANGE        // Range di date (es. "dal 15 lug al 20 ago")
}

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val geoPointId: String,
    val title: String,
    val startDate: Long,          // epoch millis
    val endDate: Long? = null,    // solo per DATE_RANGE
    val type: ReminderType = ReminderType.SINGLE,
    val isActive: Boolean = true,
    val notificationId: Int = abs(id.hashCode())
)
