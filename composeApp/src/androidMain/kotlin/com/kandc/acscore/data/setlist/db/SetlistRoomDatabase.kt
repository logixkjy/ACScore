package com.kandc.acscore.data.setlist.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SetlistEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SetlistRoomDatabase : RoomDatabase() {
    abstract fun setlistDao(): SetlistDao
}