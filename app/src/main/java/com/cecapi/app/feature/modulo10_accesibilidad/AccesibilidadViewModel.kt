package com.cecapi.app.feature.modulo10_accesibilidad

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pantalla de accesibilidad: enciende el servicio del sistema y administra el perfil de gestos.
 * El estado encendido/apagado se comprueba con el Context desde la pantalla ([AccesibilidadActiva]),
 * porque los servicios del sistema no se consultan sin uno.
 */
@HiltViewModel
class AccesibilidadViewModel @Inject constructor(
    private val repositorio: AccesibilidadRepository,
    private val voiceEngine: VoiceEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val perfiles: StateFlow<List<PerfilAccesibilidadEntity>> = repositorio.observePerfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _talkBackActivo = MutableStateFlow(false)
    val talkBackActivo: StateFlow<Boolean> = _talkBackActivo.asStateFlow()

    init {
        actualizarTalkBack()
        voiceEngine.speak(
            "Accesibilidad. Aquí puedes encender el servicio de CECAPI. Con el servicio encendido, " +
                "el doble volumen abajo lee en voz alta el texto que se ve en pantalla. " +
                "En los ajustes del teléfono, búscame en la lista y actívame.",
        )
    }

    /**
     * Revisa los lectores de pantalla que hablan. Se llama al mostrar la pantalla y cada vez que se
     * vuelve a ella (ON_RESUME), porque el usuario puede activar o desactivar TalkBack y regresar.
     */
    fun actualizarTalkBack() {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val paquetes = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            ?.mapNotNull { info ->
                // `resolveInfo` no siempre viene; el id del servicio también trae el paquete.
                info.resolveInfo?.serviceInfo?.packageName
                    ?: info.id?.substringBefore('/')
            }
            .orEmpty()
        _talkBackActivo.value = DetectorTalkBack.estaActivo(paquetes)
        Log.d(TAG, "Lectores que hablan: $paquetes; TalkBack=${_talkBackActivo.value}")
    }

    /** Dice el estado actual, para no tener que verlo en pantalla. */
    fun anunciarEstado(activo: Boolean) {
        voiceEngine.speak(
            if (activo) {
                "El servicio de CECAPI está encendido. Ya puedo leer la pantalla con el doble volumen abajo."
            } else {
                "El servicio de CECAPI está apagado. Toca abrir ajustes de accesibilidad y actívame en la lista."
            },
        )
    }

    /** Guarda el perfil básico en la base y lo deja activo, si todavía no hay ninguno. */
    fun usarPerfilBasico() {
        viewModelScope.launch {
            repositorio.asegurarPerfilPredeterminado()
            voiceEngine.speak("Listo. Activo el perfil básico de gestos.")
        }
    }

    fun activarPerfil(perfilId: Long, nombre: String) {
        viewModelScope.launch {
            repositorio.activar(perfilId)
            voiceEngine.speak("Listo. Uso el perfil $nombre.")
        }
    }

    fun onMicTapped() = voiceEngine.startListening()

    fun onMicDoubleTap() = voiceEngine.mute()

    private companion object {
        const val TAG = "CecapiAccesibilidad"
    }
}
