package com.kandc.acscore.data.local

import android.content.Context
import androidx.room.Room

object DbProvider {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "acscore.db"
            )
                // MVP: 스키마 바뀌면 초기화 허용(지침 반영)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
}