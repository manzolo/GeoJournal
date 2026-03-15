package it.manzolo.geojournal.domain.model

import java.util.Date
import java.util.UUID

enum class ReminderType {
    SINGLE,           // Una volta sola
    ANNUAL_RECURRING, // Ogni anno alla stessa data
    DATE_RANGE        // Range di date
}

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val geoPointId: String,
    val title: String,
    val date: Date,
    val type: ReminderType = ReminderType.SINGLE,
    val endDate: Date? = null,  // solo per DATE_RANGE
    val isActive: Boolean = true,
    val notificationId: Int = (Math.random() * Int.MAX_VALUE).toInt()
)
