package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiWarning
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.cecapi.app.core.voice.VoiceState

@Composable
fun GestionScreen(
    onBack: () -> Unit,
    viewModel: GestionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val esEducador = state.rolObservador == RolUsuario.EDUCADOR
    val puedeEditar = viewModel.puedeEditar()
    // La persona que se está editando tiene su propia pantalla, en vez de abrirse inline en la
    // tarjeta: validar, cambiar rol y asignar educador juntos podían llenar la tarjeta de chips chicos.
    val personaEnEdicion = state.personas.firstOrNull { it.id == state.expandidoId }
    // Si hay pendientes, la gestión se abre primero con ellos; "Ver toda la gestión" lleva a la lista normal.
    var pendientesVistos by remember { mutableStateOf(false) }
    var abrirPendientes by remember { mutableStateOf(false) }
    var vista by remember { mutableStateOf(GestionVista.Menu) }
    var institucionElegida by remember { mutableStateOf("") }
    var rolElegido by remember { mutableStateOf<RolUsuario?>(null) }
    val personasPendientes = state.personas.filter { it.solicitaValidacion && !it.validado }
    val incidenciasAbiertas = state.incidencias.filter { !it.resuelta }
    val mostrarPendientes = personaEnEdicion == null &&
        (abrirPendientes || (!pendientesVistos && (personasPendientes.isNotEmpty() || incidenciasAbiertas.isNotEmpty())))
    val alertasDe: (UsuarioEntity) -> Set<AlertaPersona> = { persona ->
        buildSet {
            if (persona.solicitaValidacion && !persona.validado) add(AlertaPersona.PorValidar)
            if (state.incidencias.any { it.reportanteId == persona.id && !it.resuelta }) add(AlertaPersona.Soporte)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (personaEnEdicion != null) {
            FichaPersona(
                persona = personaEnEdicion,
                permisos = PermisosFicha(
                    puedeValidar = viewModel.puedeValidar(personaEnEdicion),
                    puedeEditar = puedeEditar,
                    puedeEditarDatos = viewModel.puedeEditarCuenta(personaEnEdicion),
                    puedeDarDeBaja = viewModel.puedeDarDeBaja(personaEnEdicion),
                    puedeEliminar = viewModel.puedeEliminarCuenta(personaEnEdicion),
                    puedeCambiarContrasena = viewModel.puedeCambiarContrasenaDe(personaEnEdicion),
                    puedeAutorizarBaja = viewModel.puedeAutorizarBajaDe(personaEnEdicion),
                ),
                rolesAsignables = viewModel.rolesAsignables(),
                educadoresDisponibles = { viewModel.educadoresPara(personaEnEdicion) },
                onBack = { viewModel.onPersonaTocada(personaEnEdicion) },
                onValidar = { viewModel.onValidar(personaEnEdicion) },
                onRechazar = { viewModel.onRechazarSolicitud(personaEnEdicion) },
                onRolElegido = { rol -> viewModel.onRolElegido(personaEnEdicion, rol) },
                onEducadorElegido = { educador -> viewModel.onEducadorElegido(personaEnEdicion, educador) },
                onCambiarContrasena = { nueva -> viewModel.onCambiarContrasena(personaEnEdicion, nueva) },
                onAutorizarBaja = { viewModel.onAutorizarBajaDe(personaEnEdicion) },
                onDarDeBaja = { viewModel.onDarDeBaja(personaEnEdicion) },
                onEliminar = { viewModel.onEliminarCuenta(personaEnEdicion) },
                onEditarDatos = { nombre, apodo, correo -> viewModel.onEditarCuenta(personaEnEdicion, nombre, apodo, correo) },
            )
        } else if (mostrarPendientes) {
            PendientesPanel(
                personas = personasPendientes,
                incidencias = incidenciasAbiertas,
                alertasDe = alertasDe,
                onBack = {
                    abrirPendientes = false
                    pendientesVistos = true
                },
                onPersonaTocada = viewModel::onPersonaTocada,
                onResolverIncidencia = viewModel::onResolverIncidencia,
                onVerTodo = {
                    abrirPendientes = false
                    pendientesVistos = true
                },
            )
        } else if (vista == GestionVista.Menu) {
            ScreenTopBar(
                eyebrow = "GESTIÓN",
                title = when (state.rolObservador) {
                    RolUsuario.ADMINISTRADOR -> "Sin institución · ve todas"
                    RolUsuario.EDUCADOR -> "Mis alumnos"
                    else -> "Mi institución"
                },
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )
            BigBtn(
                icon = Icons.Filled.Check,
                label = "Validar",
                size = BigBtnSize.Standard,
                onClick = { vista = GestionVista.Validar },
            )
            BigBtn(
                icon = Icons.Filled.Person,
                label = "Por roles",
                size = BigBtnSize.Standard,
                onClick = { vista = GestionVista.PorRoles },
            )
            if (state.rolObservador == RolUsuario.ADMINISTRADOR || state.rolObservador == RolUsuario.DIRECTIVO) {
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = "Asignar educador",
                    size = BigBtnSize.Standard,
                    onClick = { vista = GestionVista.Asignacion },
                )
            }
            BigBtn(
                icon = Icons.Filled.Close,
                label = "Bajas o cambio de educador",
                size = BigBtnSize.Standard,
                onClick = { vista = GestionVista.Bajas },
            )
        } else if (vista == GestionVista.Validar) {
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Validar", onBack = { vista = GestionVista.Menu }, onCommands = {})
            if (state.rolObservador == RolUsuario.ADMINISTRADOR) {
                BigBtn(
                    icon = Icons.Filled.Check,
                    label = "Cuentas nuevas",
                    size = BigBtnSize.Standard,
                    onClick = { vista = GestionVista.Cuentas },
                )
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = "Instituciones",
                    size = BigBtnSize.Standard,
                    onClick = { vista = GestionVista.Instituciones },
                )
            } else {
                if (personasPendientes.isEmpty()) {
                    Text("No hay cuentas pendientes de validar.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
                }
                personasPendientes.forEach { persona ->
                    PersonaCard(
                        persona = persona,
                        alertas = alertasDe(persona),
                        mostrarOrigen = false,
                        editable = true,
                        onClick = { viewModel.onPersonaTocada(persona) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else if (vista == GestionVista.Instituciones) {
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Instituciones", onBack = { vista = GestionVista.Validar }, onCommands = {})
            val instituciones = state.personas
                .flatMap { listOfNotNull(it.origen.takeIf { o -> o.isNotBlank() }, it.institucionSolicitada) }
                .distinct()
            if (instituciones.isEmpty()) {
                Text("No hay instituciones con cuentas.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            instituciones.forEach { institucion ->
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = institucion,
                    size = BigBtnSize.Standard,
                    onClick = {
                        institucionElegida = institucion
                        vista = GestionVista.InstitucionDetalle
                    },
                )
            }
        } else if (vista == GestionVista.InstitucionDetalle) {
            ScreenTopBar(eyebrow = "GESTIÓN", title = institucionElegida, onBack = { vista = GestionVista.Instituciones }, onCommands = {})
            val cuentas = personasPendientes.filter { it.institucionSolicitada.equals(institucionElegida, ignoreCase = true) }
            if (cuentas.isEmpty()) {
                Text("No hay cuentas pendientes en esta institución.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            cuentas.forEach { persona ->
                PersonaCard(
                    persona = persona,
                    alertas = alertasDe(persona),
                    mostrarOrigen = false,
                    editable = true,
                    onClick = { viewModel.onPersonaTocada(persona) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (vista == GestionVista.PorRoles) {
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Por roles", onBack = { vista = GestionVista.Menu }, onCommands = {})
            rolesVisibles(state.rolObservador).forEach { rol ->
                val cantidad = state.personas.count { it.rol == rol.codigo }
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = "${etiquetaRol(rol)} ($cantidad)",
                    size = BigBtnSize.Standard,
                    onClick = {
                        rolElegido = rol
                        vista = GestionVista.RolDetalle
                    },
                )
            }
        } else if (vista == GestionVista.RolDetalle) {
            val rol = rolElegido ?: RolUsuario.USUARIO
            ScreenTopBar(eyebrow = "GESTIÓN", title = etiquetaRol(rol), onBack = { vista = GestionVista.PorRoles }, onCommands = {})
            val personasDelRol = state.personas.filter { it.rol == rol.codigo }
            if (personasDelRol.isEmpty()) {
                Text("No hay personas con este rol.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            personasDelRol.forEach { persona ->
                PersonaCard(
                    persona = persona,
                    alertas = alertasDe(persona),
                    mostrarOrigen = state.rolObservador == RolUsuario.ADMINISTRADOR,
                    editable = true,
                    onClick = { viewModel.onPersonaTocada(persona) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (vista == GestionVista.Bajas) {
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Bajas o cambio de educador", onBack = { vista = GestionVista.Menu }, onCommands = {})
            val bajas = state.personas.filter { it.solicitaBaja || it.solicitaCambioEducador }
            if (bajas.isEmpty()) {
                Text("No hay bajas pedidas.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            bajas.forEach { persona ->
                PersonaCard(
                    persona = persona,
                    alertas = alertasDe(persona),
                    mostrarOrigen = false,
                    editable = true,
                    onClick = { viewModel.onPersonaTocada(persona) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (vista == GestionVista.Cuentas) {
            val cuentas by viewModel.cuentasPorRevisar.collectAsState()
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Validar cuentas", onBack = { vista = GestionVista.Menu }, onCommands = {})
            if (cuentas.isEmpty()) {
                Text("No hay cuentas nuevas por revisar.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            cuentas.forEach { cuenta ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiSurface)
                        .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(cuenta.nombreCompleto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Cuenta nueva, como usuario regular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Confirmar",
                        size = BigBtnSize.Secondary,
                        onClick = { viewModel.onConfirmarCuenta(cuenta) },
                    )
                    BigBtn(
                        icon = Icons.Filled.Close,
                        label = "No la conozco",
                        variant = BigBtnVariant.Danger,
                        size = BigBtnSize.Secondary,
                        onClick = { viewModel.onNoConozcoCuenta(cuenta) },
                    )
                }
            }
        } else if (vista == GestionVista.Asignacion) {
            val sinEducador = viewModel.alumnosSinEducador()
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Asignar educador", onBack = { vista = GestionVista.Menu }, onCommands = {})
            if (sinEducador.isEmpty()) {
                Text("Todos los alumnos validados ya tienen educador.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            sinEducador.forEach { alumno ->
                PersonaCard(
                    persona = alumno,
                    alertas = emptySet(),
                    mostrarOrigen = false,
                    editable = true,
                    onClick = { viewModel.onPersonaTocada(alumno) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            ScreenTopBar(
                eyebrow = "GESTIÓN",
                title = when (state.rolObservador) {
                    RolUsuario.ADMINISTRADOR -> "Sin institución · ve todas"
                    RolUsuario.EDUCADOR -> "Mis alumnos"
                    else -> "Mi institución"
                },
                onBack = { vista = GestionVista.Menu },
                onCommands = viewModel::onCommandsRequested,
            )

            if (state.incidencias.isNotEmpty()) {
                Text("REPORTES RECIBIDOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "MX")) }
                state.incidencias.forEach { incidencia ->
                    IncidenciaCard(incidencia = incidencia, fecha = formatter.format(Date(incidencia.fechaReporte)), onResolver = { viewModel.onResolverIncidencia(incidencia) })
                }
            }

            Text(
                if (esEducador) "MIS ALUMNOS" else "PERSONAS DE LA INSTITUCIÓN",
                style = MaterialTheme.typography.labelLarge,
                color = CecapiTextMuted,
            )
            if (state.personas.isEmpty()) {
                Text(
                    if (esEducador) "Todavía no tienes alumnos asignados." else "No hay nadie que mostrar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                // Dos por fila para que la lista quepa sin tanto scroll.
                state.personas.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        fila.forEach { persona ->
                            PersonaCard(
                                persona = persona,
                                alertas = alertasDe(persona),
                                mostrarOrigen = state.rolObservador == RolUsuario.ADMINISTRADOR,
                                editable = puedeEditar || viewModel.puedeValidar(persona),
                                onClick = { viewModel.onPersonaTocada(persona) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di cuántas personas, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private enum class GestionVista { Menu, Roles, Cuentas, Asignacion, Validar, Instituciones, InstitucionDetalle, PorRoles, RolDetalle, Bajas }

/** Qué espera de alguien: su validación, o un reporte suyo sin resolver. El color lo dice sin texto. */
private enum class AlertaPersona { PorValidar, Soporte }

private val AzulSoporte = Color(0xFF5AC8FA)

/** Borde de la tarjeta: neutro si no hay nada, un color si hay una alerta, y degradado si hay varias. */
private fun bordeDe(alertas: Set<AlertaPersona>, intensidad: Float): Brush {
    val colores = alertas.map { (if (it == AlertaPersona.PorValidar) CecapiWarning else AzulSoporte).copy(alpha = intensidad) }
    return when (colores.size) {
        0 -> SolidColor(CecapiBorder)
        1 -> SolidColor(colores.first())
        else -> Brush.horizontalGradient(colores)
    }
}

/** Lo que espera una decisión: personas por validar y reportes sin resolver. Se muestra antes que la lista normal. */
@Composable
private fun PendientesPanel(
    personas: List<UsuarioEntity>,
    incidencias: List<IncidenciaEntity>,
    alertasDe: (UsuarioEntity) -> Set<AlertaPersona>,
    onBack: () -> Unit,
    onPersonaTocada: (UsuarioEntity) -> Unit,
    onResolverIncidencia: (IncidenciaEntity) -> Unit,
    onVerTodo: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "MX")) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTopBar(eyebrow = "GESTIÓN", title = "Pendientes", onBack = onBack, onCommands = {})

        if (personas.isNotEmpty()) {
            Text("PENDIENTES DE VALIDAR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            personas.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { persona ->
                        PersonaCard(
                            persona = persona,
                            alertas = alertasDe(persona),
                            mostrarOrigen = false,
                            editable = true,
                            onClick = { onPersonaTocada(persona) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                }
            }
        }

        if (incidencias.isNotEmpty()) {
            Text("REPORTES RECIBIDOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            incidencias.forEach { incidencia ->
                IncidenciaCard(
                    incidencia = incidencia,
                    fecha = formatter.format(Date(incidencia.fechaReporte)),
                    onResolver = { onResolverIncidencia(incidencia) },
                )
            }
        }

        BigBtn(
            icon = Icons.Filled.Person,
            label = "Ver toda la gestión",
            size = BigBtnSize.Standard,
            onClick = onVerTodo,
        )
    }
}

/** Pantalla propia para validar, cambiar rol y asignar educador. */
@Composable
private fun PersonaCard(
    persona: UsuarioEntity,
    alertas: Set<AlertaPersona>,
    mostrarOrigen: Boolean,
    editable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val latido by rememberInfiniteTransition(label = "alerta").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
        label = "intensidad",
    )
    val intensidad = if (alertas.isEmpty()) 1f else latido
    val rolLegible = persona.rol.replaceFirstChar { it.uppercase() }
    val detalle = buildString {
        append(rolLegible)
        if (mostrarOrigen && persona.origen.isNotBlank()) append(" · ${persona.origen}")
    }
    val help = if (editable) {
        "$rolLegible ${persona.nombreCompleto}. Toca para cambiarle el rol."
    } else {
        "$rolLegible ${persona.nombreCompleto}."
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(CecapiSurface)
            .border(2.dp, bordeDe(alertas, intensidad), shape)
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
    ) {
        if (persona.solicitaValidacion && !persona.validado) {
            Text(
                "PENDIENTE",
                style = MaterialTheme.typography.labelSmall,
                color = CecapiTextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CecapiTextMuted.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            persona.nombreCompleto,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = if (persona.solicitaValidacion && !persona.validado) 6.dp else 0.dp),
        )
        Text(detalle, style = MaterialTheme.typography.bodySmall, color = CecapiTextMuted)
    }
}

/** Un reporte de Ayuda y soporte, visible solo para quien debe atenderlo (administrador o directivo). */
@Composable
private fun IncidenciaCard(incidencia: IncidenciaEntity, fecha: String, onResolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(incidencia.mensaje, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(fecha, style = MaterialTheme.typography.labelSmall, color = CecapiTextMuted)
        if (incidencia.resuelta) {
            Text("RESUELTO", style = MaterialTheme.typography.labelMedium, color = CecapiSuccess)
        } else {
            BigBtn(icon = Icons.Filled.Check, label = "Marcar resuelto", half = true, onClick = onResolver)
        }
    }
}

/** Roles que ve cada quien, de su jerarquía hacia abajo. */
private fun rolesVisibles(observador: RolUsuario?): List<RolUsuario> = when (observador) {
    RolUsuario.ADMINISTRADOR -> listOf(RolUsuario.ADMINISTRADOR, RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ALUMNO, RolUsuario.USUARIO)
    RolUsuario.DIRECTIVO -> listOf(RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ALUMNO, RolUsuario.USUARIO)
    RolUsuario.EDUCADOR -> listOf(RolUsuario.ALUMNO)
    else -> emptyList()
}

private fun etiquetaRol(rol: RolUsuario): String = when (rol) {
    RolUsuario.ADMINISTRADOR -> "Administradores"
    RolUsuario.DIRECTIVO -> "Directivos"
    RolUsuario.EDUCADOR -> "Educadores"
    RolUsuario.ALUMNO -> "Alumnos"
    RolUsuario.USUARIO -> "Usuarios"
}
