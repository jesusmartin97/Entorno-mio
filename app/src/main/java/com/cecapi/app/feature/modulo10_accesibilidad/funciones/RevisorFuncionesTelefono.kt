package com.cecapi.app.feature.modulo10_accesibilidad.funciones

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mide qué funciones del teléfono están disponibles y, si falta alguna, lo dice en voz alta (Fase 3).
 *
 * Solo avisa de lo que falta: si el teléfono tiene la función, no se dice nada de ella. La decisión de
 * qué falta se toma en [ChequeoFunciones] (código puro y probado); aquí solo se mide el teléfono.
 *
 * Todavía no lo llama ninguna pantalla: no hay ruta de navegación a una pantalla nuestra (esa parte es
 * de Equipo 1) ni se decidió dónde se revisa. Queda pendiente para Pp / Equipo 1 (ver reporte).
 */
@Singleton
class RevisorFuncionesTelefono @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val voiceEngine: VoiceEngine,
) {

    /** Mide el teléfono y devuelve qué funciones están disponibles. No dice nada por voz. */
    fun medir(): EstadoFunciones {
        val paquetes = contexto.packageManager
        val sensores = contexto.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return EstadoFunciones(
            tieneCamara = paquetes.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            tieneFlash = paquetes.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
            tieneMicrofono = paquetes.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            tieneReconocimientoVoz = SpeechRecognizer.isRecognitionAvailable(contexto),
            // Un motor de voz instalado; el que esté elegido se comprueba al arrancar el motor (VoiceEngine).
            tieneMotorVoz = hayMotorDeVoz(),
            tieneVibracion = tieneVibracion(),
            // "Sensores" = al menos el de movimiento, que es el que usa la app.
            tieneSensores = sensores?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            tieneLuzAmbiental = sensores?.getDefaultSensor(Sensor.TYPE_LIGHT) != null,
            tieneUbicacion = paquetes.hasSystemFeature(PackageManager.FEATURE_LOCATION),
            versionAdecuada = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
        )
    }

    /**
     * Mide el teléfono y, **solo si falta alguna función**, lo avisa por voz. Devuelve las funciones
     * que faltan (vacío si el teléfono tiene todo).
     */
    fun revisarYAvisar(): List<FuncionTelefono> {
        val faltan = ChequeoFunciones.faltantes(medir())
        ChequeoFunciones.aviso(faltan)?.let(voiceEngine::speak)
        return faltan
    }

    /** True si el teléfono tiene al menos un motor de voz instalado. */
    private fun hayMotorDeVoz(): Boolean {
        @Suppress("DEPRECATION")
        val motores = contexto.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0,
        )
        return motores.isNotEmpty()
    }

    private fun tieneVibracion(): Boolean {
        val vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (contexto.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (contexto.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        }
        return vibrador?.hasVibrator() == true
    }
}
