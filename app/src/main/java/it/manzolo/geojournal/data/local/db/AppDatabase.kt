package it.manzolo.geojournal.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GeoPointEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun geoPointDao(): GeoPointDao

    companion object {
        const val DATABASE_NAME = "geojournal.db"
    }
}
