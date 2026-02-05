package com.kandc.acscore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScoreEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 컬럼 추가 (nullable)
        db.execSQL("ALTER TABLE scores ADD COLUMN contentHash TEXT")

        // 조회 성능용 인덱스
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scores_contentHash ON scores(contentHash)")
    }
}