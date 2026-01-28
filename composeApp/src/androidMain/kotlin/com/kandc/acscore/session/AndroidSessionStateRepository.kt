package com.kandc.acscore.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val Context.dataStore by preferencesDataStore(name = "acscore_session")

class AndroidSessionStateRepository(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }
) : SessionStateRepository {

    private val KEY_LAST_PICKER = stringPreferencesKey("last_picker_context")

    override suspend fun getLastPickerContext(): LastPickerContext? {
        val prefs = context.dataStore.data.first()
        val raw = prefs[KEY_LAST_PICKER] ?: return null
        return runCatching { json.decodeFromString<LastPickerContext>(raw) }.getOrNull()
    }

    override suspend fun setLastPickerContext(ctx: LastPickerContext) {
        val raw = json.encodeToString(ctx)
        context.dataStore.edit { it[KEY_LAST_PICKER] = raw }
    }
}