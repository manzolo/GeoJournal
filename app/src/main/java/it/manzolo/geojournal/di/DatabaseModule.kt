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
import it.manzolo.geojournal.data.local.db.PointKmlDao
import it.manzolo.geojournal.data.local.db.ReminderDao
import it.manzolo.geojournal.data.local.db.VisitLogDao
import javax.inject.Singleton

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE geo_points ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `point_kmls` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `geo_point_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `file_path` TEXT NOT NULL,
                `imported_at` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`geo_point_id`) REFERENCES `geo_points`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_point_kmls_geo_point_id` ON `point_kmls`(`geo_point_id`)")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Ricrea la tabella senza audio_url (minSdk 26 non supporta DROP COLUMN)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `geo_points_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `tags` TEXT NOT NULL DEFAULT '',
                `photo_urls` TEXT NOT NULL DEFAULT '',
                `emoji` TEXT NOT NULL DEFAULT '📍',
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `owner_id` TEXT NOT NULL DEFAULT '',
                `is_shared` INTEGER NOT NULL DEFAULT 0,
                `synced_to_firestore` INTEGER NOT NULL DEFAULT 0,
                `rating` INTEGER NOT NULL DEFAULT 0,
                `is_archived` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `geo_points_new`
            SELECT `id`,`title`,`description`,`latitude`,`longitude`,`tags`,`photo_urls`,
                   `emoji`,`created_at`,`updated_at`,`owner_id`,`is_shared`,
                   `synced_to_firestore`,`rating`,`is_archived`
            FROM `geo_points`
        """.trimIndent())
        db.execSQL("DROP TABLE `geo_points`")
        db.execSQL("ALTER TABLE `geo_points_new` RENAME TO `geo_points`")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE geo_points ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE geo_points ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
    }
}

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides fun provideGeoPointDao(db: AppDatabase): GeoPointDao = db.geoPointDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideVisitLogDao(db: AppDatabase): VisitLogDao = db.visitLogDao()
    @Provides fun providePointKmlDao(db: AppDatabase): PointKmlDao = db.pointKmlDao()
}
