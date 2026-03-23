package it.manzolo.geojournal.fakes

import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReminderRepository : ReminderRepository {

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())

    override fun observeByGeoPointId(geoPointId: String): Flow<List<Reminder>> =
        _reminders.map { list -> list.filter { it.geoPointId == geoPointId } }

    override fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<Reminder>> =
        _reminders.map { list ->
            list.filter { it.startDate in startEpoch..endEpoch }
        }

    override fun observeAllActive(): Flow<List<Reminder>> =
        _reminders.map { list -> list.filter { it.isActive } }

    override suspend fun getAll(): List<Reminder> = _reminders.value

    override suspend fun getActiveReminders(): List<Reminder> =
        _reminders.value.filter { it.isActive }

    override suspend fun save(reminder: Reminder) {
        val current = _reminders.value.toMutableList()
        val idx = current.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) current[idx] = reminder else current.add(reminder)
        _reminders.value = current
    }

    override suspend fun delete(reminder: Reminder) {
        _reminders.value = _reminders.value.filter { it.id != reminder.id }
    }

    override suspend fun deleteByGeoPointId(geoPointId: String) {
        _reminders.value = _reminders.value.filter { it.geoPointId != geoPointId }
    }

    override suspend fun pullFromFirestore(): Int = 0
}
