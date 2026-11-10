package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.ScreenTopBar

/** Qué puede hacer quien mira la ficha sobre esta cuenta. */
internal data class PermisosFicha(
    val puedeValidar: Boolean,
    val puedeEditar: Boolean,
    val puedeEditarDatos: Boolean,
    val puedeDarDeBaja: Boolean,
    val puedeEliminar: Boolean,
    val puedeCambiarContrasena: Boolean,
    val puedeAutorizarBaja: Boolean,
)

/** Ficha de una cuenta: sus datos y las acciones que le toca a quien la mira. */
@Composable
internal fun FichaPersona(
    persona: UsuarioEntity,
    permisos: PermisosFicha,
    rolesAsignables: List<RolUsuario>,
    educadoresDisponibles: () -> List<UsuarioEntity>,
    onBack: () -> Unit,
    onValidar: () -> Unit,
    onRechazar: () -> Unit,
    onRolElegido: (RolUsuario) -> Unit,
    onEducadorElegido: (UsuarioEntity?) -> Unit,
    onCambiarContrasena: (String) -> Unit,
    onAutorizarBaja: () -> Unit,
    onDarDeBaja: () -> Unit,
    onEliminar: () -> Unit,
    onEditarDatos: (String, String, String) -> Unit,
) {
    var editando by remember { mutableStateOf(false) }
    var accionPendiente by remember { mutableStateOf<String?>(null) }
    var nombre by remember(persona.id) { mutableStateOf(persona.nombreCompleto) }
    var apodo by remember(persona.id) { mutableStateOf(persona.apodo.orEmpty()) }
    var correo by remember(persona.id) { mutableStateOf(persona.email.orEmpty()) }
    var nueva by remember(persona.id) { mutableStateOf("") }
    var cambiandoRol by remember(persona.id) { mutableStateOf(false) }
    var rolNuevo by remember(persona.id) { mutableStateOf<RolUsuario?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTopBar(eyebrow = "GESTIÓN", title = persona.nombreCompleto, onBack = onBack, onCommands = {})

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CecapiSurface)
                .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Rol: ${persona.rol.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            if (persona.origen.isNotBlank()) {
                Text("Institución: ${persona.origen}", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            if (persona.apodo != null && persona.apodo.isNotBlank()) {
                Text("Apodo: ${persona.apodo}", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            if (persona.email != null && persona.email.isNotBlank()) {
                Text("Correo: ${persona.email}", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
        }

        // Pregunta de validación: solo cuando hay una solicitud de institución pendiente.
        if (persona.solicitaValidacion && !persona.validado && permisos.puedeValidar) {
            Text("¿VALIDAS ESTA CUENTA? ¿ES PARTE DE LA INSTITUCIÓN?", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigBtn(icon = Icons.Filled.Check, label = "Sí", size = com.cecapi.app.core.ui.BigBtnSize.Standard, half = true, onClick = onValidar, modifier = Modifier.weight(1f))
                BigBtn(icon = Icons.Filled.Close, label = "No", variant = BigBtnVariant.Danger, half = true, onClick = onRechazar, modifier = Modifier.weight(1f))
            }
        }

        if (persona.solicitaBaja && permisos.puedeAutorizarBaja) {
            Text("PIDIÓ DARSE DE BAJA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            BigBtn(icon = Icons.Filled.Close, label = "Confirmar baja", variant = BigBtnVariant.Danger, onClick = onAutorizarBaja)
        }

        if (persona.solicitaCambioEducador && permisos.puedeEditar) {
            Text("PIDIÓ CAMBIAR DE EDUCADOR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
        }

        if (permisos.puedeEditarDatos) {
            if (!editando) {
                BigBtn(icon = Icons.Filled.Edit, label = "Editar cuenta", variant = BigBtnVariant.Accent, onClick = { editando = true })
            } else {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                )
                OutlinedTextField(
                    value = apodo,
                    onValueChange = { apodo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Apodo (opcional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                )
                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Guardar",
                        variant = BigBtnVariant.Accent,
                        half = true,
                        onClick = {
                            onEditarDatos(nombre, apodo, correo)
                            editando = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(icon = Icons.Filled.Close, label = "Cancelar", half = true, onClick = { editando = false }, modifier = Modifier.weight(1f))
                }
            }
        }

        if (permisos.puedeEditar && persona.rol != RolUsuario.ADMINISTRADOR.codigo) {
            if (!cambiandoRol) {
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = "Cambiar rol",
                    onClick = {
                        cambiandoRol = true
                        rolNuevo = null
                    },
                )
            } else {
                Text("NUEVO ROL", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                rolesAsignables.forEach { rol ->
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = rol.codigo.replaceFirstChar { it.uppercase() },
                        variant = if (rolNuevo == rol) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        onClick = { rolNuevo = rol },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Validar rol",
                        variant = BigBtnVariant.Accent,
                        enabled = rolNuevo != null,
                        half = true,
                        onClick = {
                            rolNuevo?.let(onRolElegido)
                            cambiandoRol = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(
                        icon = Icons.Filled.Close,
                        label = "Cancelar",
                        half = true,
                        onClick = { cambiandoRol = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (permisos.puedeEditar && persona.rol == RolUsuario.ALUMNO.codigo) {
            Text("ASIGNAR EDUCADOR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            educadoresDisponibles().forEach { educador ->
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = educador.nombreCompleto,
                    variant = if (persona.educadorId == educador.id) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                    onClick = { onEducadorElegido(educador) },
                )
            }
        }

        if (permisos.puedeCambiarContrasena) {
            Text("CONTRASEÑA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            OutlinedTextField(
                value = nueva,
                onValueChange = { nueva = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nueva contraseña *") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            BigBtn(
                icon = Icons.Filled.Lock,
                label = "Cambiar contraseña",
                enabled = nueva.isNotBlank(),
                onClick = {
                    onCambiarContrasena(nueva)
                    nueva = ""
                },
            )
        }

        if (permisos.puedeDarDeBaja) {
            if (accionPendiente == "baja") {
                BigBtn(icon = Icons.Filled.Close, label = "Confirmar: quitar de la institución", variant = BigBtnVariant.Danger, onClick = { accionPendiente = null; onDarDeBaja() })
                BigBtn(icon = Icons.Filled.Close, label = "Cancelar", onClick = { accionPendiente = null })
            } else {
                BigBtn(icon = Icons.Filled.Close, label = "Dar de baja", variant = BigBtnVariant.Danger, onClick = { accionPendiente = "baja" })
            }
        }

        if (permisos.puedeEliminar) {
            if (accionPendiente == "eliminar") {
                BigBtn(icon = Icons.Filled.Close, label = "Confirmar: eliminar cuenta", variant = BigBtnVariant.Danger, onClick = { accionPendiente = null; onEliminar() })
                BigBtn(icon = Icons.Filled.Close, label = "Cancelar", onClick = { accionPendiente = null })
            } else {
                BigBtn(icon = Icons.Filled.Close, label = "Eliminar cuenta", variant = BigBtnVariant.Danger, onClick = { accionPendiente = "eliminar" })
            }
        }
    }
}
