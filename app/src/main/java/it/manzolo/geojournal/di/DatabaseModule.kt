package it.manzolo.geojournal.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.manzolo.geojournal.data.local.db.AppDatabase
import it.manzolo.geojournal.data.local.db.GeoPointDao
import it.manzolo.geojournal.data.local.db.ReminderDao
import it.manzolo.geojournal.data.local.db.VisitLogDao
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `geo_point_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `start_date` INTEGER NOT NULL,
                `end_date` INTEGER,
                `type` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL DEFAULT 1,
                `notification_id` INTEGER NOT NULL,
                FOREIGN KEY(`geo_point_id`) REFERENCES `geo_points`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_geo_point_id` ON `reminders`(`geo_point_id`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `visit_logs` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `geo_point_id` TEXT NOT NULL,
                `visited_at` INTEGER NOT NULL,
                `note` TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(`geo_point_id`) REFERENCES `geo_points`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_logs_geo_point_id` ON `visit_logs`(`geo_point_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_logs_visited_at` ON `visit_logs`(`visited_at`)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideGeoPointDao(db: AppDatabase): GeoPointDao = db.geoPointDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideVisitLogDao(db: AppDatabase): VisitLogDao = db.visitLogDao()
}
