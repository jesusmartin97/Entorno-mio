package com.cecapi.app.core.voice

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceSettingsStore by preferencesDataStore(name = "device_settings")

/**
 * Preferences of this phone: how the assistant sounds, how it addresses the person, whether it
 * beeps and vibrates. They are per device, not per account, so they work on the home screen before
 * anyone signs in and the person does not have to set them up again after every login.
 * Constructing this class (the activity injects it) also applies the saved values to the engine.
 */
@Singleton
class DeviceSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val intentFallback: IntentFallback,
) {
    private val data get() = context.deviceSettingsStore.data

    // Voice
    val speechRate: Flow<Float> = data.map { it[KEY_RATE] ?: DEFAULT_RATE }
    val pitch: Flow<Float> = data.map { it[KEY_PITCH] ?: 1.0f }

    /** Name of the chosen TTS voice; empty means automatic. */
    val voiceName: Flow<String> = data.map { it[KEY_VOICE].orEmpty() }

    // How the assistant talks to the person
    val addressStyle: Flow<AddressStyle> = data.map { prefs ->
        AddressStyle.entries.firstOrNull { it.name == prefs[KEY_ADDRESS] } ?: AddressStyle.TU
    }

    /** What the person wants to be called; empty means use the name from their account. */
    val preferredName: Flow<String> = data.map { it[KEY_PREFERRED_NAME].orEmpty() }

    // Sounds and vibration
    val soundCues: Flow<Boolean> = data.map { it[KEY_SOUND] ?: true }
    val vibrationCues: Flow<Boolean> = data.map { it[KEY_VIBRATION] ?: true }

    /** 1 soft, 2 normal, 3 strong. */
    val vibrationLevel: Flow<Int> = data.map { it[KEY_VIBRATION_LEVEL] ?: 2 }

    val simpleMode: Flow<Boolean> = data.map { it[KEY_SIMPLE] ?: true }

    /** A short "Nueva notificación de X" heads-up (never the content). On by default; the person can turn it off. */
    val announceNotifications: Flow<Boolean> = data.map { it[KEY_ANNOUNCE] ?: true }

    /** Keep the assistant listening for "hola" after the app is closed (needs a visible notification). Off by default. */
    val backgroundListening: Flow<Boolean> = data.map { it[KEY_BACKGROUND] ?: false }

    /** Also answer to the word "hola", not only to the assistant's name. Off by default: adults set the name first. */
    val holaEnabled: Flow<Boolean> = data.map { it[KEY_HOLA] ?: false }

    /** Everything on screen is drawn black; the person works by voice. Off by default, and always has an exit. */
    val blackScreen: Flow<Boolean> = data.map { it[KEY_BLACK_SCREEN] ?: false }

    /** Screen brightness held at the minimum while the app is open, ignoring automatic brightness. */
    val minBrightness: Flow<Boolean> = data.map { it[KEY_MIN_BRIGHTNESS] ?: false }

    /** Whether phrases the app does not understand may be sent to the AI server. Off by default. */
    val aiEnabled: Flow<Boolean> = data.map { it[KEY_AI] ?: false }

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            combine(speechRate, soundCues, vibrationCues, vibrationLevel) { rate, sound, vibration, level ->
                arrayOf<Any>(rate, sound, vibration, level)
            }.collect { values ->
                voiceEngine.applyVoiceSettings(values[0] as Float, 1.0f)
                cues.soundEnabled = values[1] as Boolean
                cues.vibrationEnabled = values[2] as Boolean
                cues.vibrationLevel = values[3] as Int
            }
        }
        scope.launch { combine(voiceName, pitch) { name, p -> name to p }.collect { (name, p) -> voiceEngine.applyVoiceProfile(name, p) } }
        scope.launch { addressStyle.collect { voiceEngine.addressStyle = it } }
        scope.launch { aiEnabled.collect { intentFallback.enabled = it } }
    }

    suspend fun setSpeechRate(rate: Float) = context.deviceSettingsStore.edit { it[KEY_RATE] = rate.coerceIn(0.5f, 2.0f) }

    suspend fun setPitch(value: Float) = context.deviceSettingsStore.edit { it[KEY_PITCH] = value.coerceIn(0.5f, 2.0f) }

    suspend fun setVoiceName(name: String) = context.deviceSettingsStore.edit { it[KEY_VOICE] = name }

    suspend fun setAddressStyle(style: AddressStyle) = context.deviceSettingsStore.edit { it[KEY_ADDRESS] = style.name }

    suspend fun setPreferredName(name: String) = context.deviceSettingsStore.edit { it[KEY_PREFERRED_NAME] = name.trim() }

    suspend fun setSoundCues(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_SOUND] = enabled }

    suspend fun setVibrationCues(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_VIBRATION] = enabled }

    suspend fun setVibrationLevel(level: Int) = context.deviceSettingsStore.edit { it[KEY_VIBRATION_LEVEL] = level.coerceIn(1, 3) }

    suspend fun setSimpleMode(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_SIMPLE] = enabled }

    suspend fun setAnnounceNotifications(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_ANNOUNCE] = enabled }

    suspend fun setBackgroundListening(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_BACKGROUND] = enabled }

    suspend fun setHolaEnabled(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_HOLA] = enabled }

    suspend fun setBlackScreen(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_BLACK_SCREEN] = enabled }

    suspend fun setMinBrightness(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_MIN_BRIGHTNESS] = enabled }

    suspend fun setAiEnabled(enabled: Boolean) = context.deviceSettingsStore.edit { it[KEY_AI] = enabled }

    /** Applies a ready-made [VoiceProfile]: speed, pitch and how the assistant addresses the person. */
    suspend fun applyProfile(profile: VoiceProfile) {
        setSpeechRate(profile.rate)
        setPitch(profile.pitch)
        setAddressStyle(profile.address)
    }

    private companion object {
        const val DEFAULT_RATE = 1.0f
        val KEY_RATE = floatPreferencesKey("speech_rate")
        val KEY_PITCH = floatPreferencesKey("pitch")
        val KEY_VOICE = stringPreferencesKey("voice_name")
        val KEY_ADDRESS = stringPreferencesKey("address_style")
        val KEY_PREFERRED_NAME = stringPreferencesKey("preferred_name")
        val KEY_SOUND = booleanPreferencesKey("sound_cues")
        val KEY_VIBRATION = booleanPreferencesKey("vibration_cues")
        val KEY_VIBRATION_LEVEL = intPreferencesKey("vibration_level")
        val KEY_SIMPLE = booleanPreferencesKey("simple_mode")
        val KEY_ANNOUNCE = booleanPreferencesKey("announce_notifications")
        val KEY_BACKGROUND = booleanPreferencesKey("background_listening")
        val KEY_HOLA = booleanPreferencesKey("hola_enabled")
        val KEY_BLACK_SCREEN = booleanPreferencesKey("black_screen")
        val KEY_MIN_BRIGHTNESS = booleanPreferencesKey("min_brightness")
        val KEY_AI = booleanPreferencesKey("ai_enabled")
    }
}
