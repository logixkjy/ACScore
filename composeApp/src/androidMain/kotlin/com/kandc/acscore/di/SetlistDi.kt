package com.kandc.acscore.di

import android.content.Context
import androidx.room.Room
import com.kandc.acscore.data.setlist.SetlistRoomRepository
import com.kandc.acscore.data.setlist.db.SetlistRoomDatabase
import com.kandc.acscore.shared.domain.repository.SetlistRepository
import com.kandc.acscore.shared.domain.usecase.CreateSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.DeleteSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.ObserveSetlistsUseCase
import kotlinx.serialization.json.Json

object SetlistDi {

    @Volatile private var db: SetlistRoomDatabase? = null

    fun provideRepository(context: Context): SetlistRepository {
        val appContext = context.applicationContext
        val database = db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                appContext,
                SetlistRoomDatabase::class.java,
                "acscore_setlists.db"
            ).build().also { db = it }
        }

        return SetlistRoomRepository(
            dao = database.setlistDao(),
            json = Json { ignoreUnknownKeys = true }
        )
    }

    fun provideUseCases(repo: SetlistRepository): UseCases =
        UseCases(
            observeSetlists = ObserveSetlistsUseCase(repo),
            createSetlist = CreateSetlistUseCase(repo),
            deleteSetlist = DeleteSetlistUseCase(repo)
        )

    data class UseCases(
        val observeSetlists: ObserveSetlistsUseCase,
        val createSetlist: CreateSetlistUseCase,
        val deleteSetlist: DeleteSetlistUseCase,
    )
}