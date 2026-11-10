package com.cecapi.app.core.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.assistantDataStore by preferencesDataStore(name = "assistant_prefs")

/**
 * Per-device name the user gave to the assistant. It lives outside Room on purpose:
 * the home screen needs it before anyone has logged in, so there is no user row yet.
 * "hola" always works as the wake word; this name is an extra one on top of it.
 */
@Singleton
class AssistantPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val assistantName: Flow<String> = context.assistantDataStore.data.map { it[KEY_NAME] ?: DEFAULT_NAME }

    suspend fun setAssistantName(name: String) {
        context.assistantDataStore.edit { it[KEY_NAME] = name.trim() }
    }

    private companion object {
        const val DEFAULT_NAME = "Nauren"
        val KEY_NAME = stringPreferencesKey("assistant_name")
    }
}
