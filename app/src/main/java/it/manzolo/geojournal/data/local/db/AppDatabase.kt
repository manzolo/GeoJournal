package it.manzolo.geojournal.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GeoPointEntity::class, ReminderEntity::class, VisitLogEntity::class, PointKmlEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun geoPointDao(): GeoPointDao
    abstract fun reminderDao(): ReminderDao
    abstract fun visitLogDao(): VisitLogDao
    abstract fun pointKmlDao(): PointKmlDao

    companion object {
        const val DATABASE_NAME = "geojournal.db"
    }
}
