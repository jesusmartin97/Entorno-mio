package com.cecapi.app.feature.modulo1_aplicacionprincipal

/**
 * Who can see or manage whom. Pure rules over two accounts' role, institution ([UsuarioEntity.origen]) and
 * teacher link ([UsuarioEntity.educadorId]) — no database access, so a screen or a test can check a case
 * without touching Room. This is the base the Administrador/Directivo/Educador management panels build on
 * later; nothing calls it yet.
 *
 * Jerarquía: administrador (todo, sin límite) > directivo (su institución) > educador (solo sus alumnos,
 * sin ver a otros educadores ni directivos) > alumno / usuario (no administran a nadie).
 */
object RolePermissions {

    /** True when [observador] may see [objetivo] in a management list. */
    fun puedeVer(observador: UsuarioEntity, objetivo: UsuarioEntity): Boolean {
        return when (RolUsuario.fromCodigo(observador.rol)) {
            RolUsuario.ADMINISTRADOR -> true
            RolUsuario.DIRECTIVO -> mismaInstitucion(observador, objetivo)
            RolUsuario.EDUCADOR ->
                RolUsuario.fromCodigo(objetivo.rol) == RolUsuario.ALUMNO &&
                    (objetivo.educadorId == observador.id || (objetivo.solicitaValidacion && mismaInstitucion(observador, objetivo)))
            // Un alumno o un usuario independiente no administran a nadie — un alumno conoce a SU educador
            // por separado (educadorAsignado), no por esta regla de "a quién administro".
            RolUsuario.ALUMNO, RolUsuario.USUARIO -> false
        }
    }

    /** True when [observador] can approve a new alumno/educador for their own institution. */
    fun puedeDarAccesoInstitucional(observador: UsuarioEntity): Boolean =
        RolUsuario.fromCodigo(observador.rol).let { it == RolUsuario.ADMINISTRADOR || it == RolUsuario.DIRECTIVO }

    /** Only an administrador authorizes an independent usuario: institutions never see these accounts at all. */
    fun puedeAutorizarUsuarioIndependiente(observador: UsuarioEntity): Boolean =
        RolUsuario.fromCodigo(observador.rol) == RolUsuario.ADMINISTRADOR

    /**
     * True when [observador] can mark [objetivo] as validado — the chain pedida: administrador valida
     * directivo (y, al no tener límite, cualquiera); directivo valida educador de su institución; educador
     * valida alumno (solo el suyo); administrador valida usuario independiente (nadie más los ve). No
     * hace nada por sí sola — ingresar sigue funcionando sin validar, esto solo decide quién puede
     * marcarlo. Ya validado, no hay nada que hacer.
     */
    fun puedeValidar(observador: UsuarioEntity, objetivo: UsuarioEntity): Boolean {
        if (objetivo.validado || !objetivo.solicitaValidacion) return false
        val solicitud = objetivo.institucionSolicitada ?: return false
        val rolObservador = RolUsuario.fromCodigo(observador.rol)
        val rolObjetivo = RolUsuario.fromCodigo(objetivo.rol)
        return when (rolObservador) {
            RolUsuario.ADMINISTRADOR -> true
            RolUsuario.DIRECTIVO -> rolObjetivo != RolUsuario.ADMINISTRADOR && perteneceA(observador, solicitud)
            RolUsuario.EDUCADOR -> rolObjetivo == RolUsuario.ALUMNO && perteneceA(observador, solicitud)
            RolUsuario.ALUMNO, RolUsuario.USUARIO -> false
        }
    }

    /**
     * A quién puede mandar un reporte cada rol. El usuario no reporta. El administrador ve todo y no envía a nadie.
     */
    fun destinatariosDeReporte(reportante: RolUsuario): List<RolUsuario> = when (reportante) {
        RolUsuario.ALUMNO, RolUsuario.EDUCADOR -> listOf(RolUsuario.EDUCADOR, RolUsuario.DIRECTIVO, RolUsuario.ADMINISTRADOR)
        RolUsuario.DIRECTIVO -> listOf(RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ADMINISTRADOR)
        RolUsuario.ADMINISTRADOR, RolUsuario.USUARIO -> emptyList()
    }

    /**
     * Where to send an incident [reportante] files. With an institution, it goes there (the educador feature
     * resolves a specific person later); an independent usuario has none, so it goes straight to the
     * administradores instead of being lost.
     */
    fun destinoDeIncidencia(reportante: UsuarioEntity): DestinoIncidencia =
        if (reportante.origen.isNotBlank()) DestinoIncidencia.InstitucionDe(reportante.origen) else DestinoIncidencia.Administradores

    private fun perteneceA(observador: UsuarioEntity, institucion: String): Boolean =
        observador.origen.isNotBlank() && observador.origen.equals(institucion, ignoreCase = true)

    private fun mismaInstitucion(observador: UsuarioEntity, objetivo: UsuarioEntity): Boolean =
        perteneceA(observador, objetivo.origen) || objetivo.institucionSolicitada?.let { perteneceA(observador, it) } == true
}

sealed interface DestinoIncidencia {
    data class InstitucionDe(val origen: String) : DestinoIncidencia
    data object Administradores : DestinoIncidencia
}
