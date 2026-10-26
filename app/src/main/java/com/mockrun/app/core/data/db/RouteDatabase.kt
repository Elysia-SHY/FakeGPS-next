package com.mockrun.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RouteEntity::class, TrackEntity::class],
    version = 3,
    exportSchema = false
)
abstract class RouteDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun trackDao(): TrackDao

    companion object {
        /**
         * 1 → 2：路线增加运行方式与来源轨迹。
         *
         * 绝不能只升 version 不写迁移——AppModule 曾经用 fallbackToDestructiveMigration()，
         * 那样用户所有路线会被静默清空。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routes ADD COLUMN mode TEXT NOT NULL DEFAULT 'ONE_WAY'")
                db.execSQL("ALTER TABLE routes ADD COLUMN sourceTrackId INTEGER")
            }
        }

        /** 2 → 3：新增录制轨迹表。列顺序与类型必须与 [TrackEntity] 生成的 schema 完全一致。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `pointsJson` TEXT NOT NULL,
                        `distanceMeters` REAL NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `avgSpeedKmh` REAL NOT NULL,
                        `avgCadenceSpm` INTEGER NOT NULL,
                        `stepCount` INTEGER NOT NULL,
                        `pointCount` INTEGER NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `isClosed` INTEGER NOT NULL DEFAULT 0,
                        `hasAltitude` INTEGER NOT NULL DEFAULT 0,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
