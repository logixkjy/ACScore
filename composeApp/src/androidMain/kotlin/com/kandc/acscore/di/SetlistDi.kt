package com.kandc.acscore.di

import android.content.Context
import androidx.room.Room
import com.kandc.acscore.data.setlist.SetlistRoomRepository
import com.kandc.acscore.data.setlist.db.SetlistRoomDatabase
import com.kandc.acscore.shared.domain.repository.SetlistRepository
import com.kandc.acscore.shared.domain.usecase.CreateSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.DeleteSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.ObserveSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.ObserveSetlistsUseCase
import com.kandc.acscore.shared.domain.usecase.UpdateSetlistItemsUseCase
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
            observeSetlist = ObserveSetlistUseCase(repo),
            createSetlist = CreateSetlistUseCase(repo),
            deleteSetlist = DeleteSetlistUseCase(repo),
            updateItems = UpdateSetlistItemsUseCase(repo),
        )

    data class UseCases(
        val observeSetlists: ObserveSetlistsUseCase,
        val observeSetlist: ObserveSetlistUseCase,
        val createSetlist: CreateSetlistUseCase,
        val deleteSetlist: DeleteSetlistUseCase,
        val updateItems: UpdateSetlistItemsUseCase,
    )
}