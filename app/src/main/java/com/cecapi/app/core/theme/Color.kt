package com.cecapi.app.core.theme

import androidx.compose.ui.graphics.Color

// Fixed dark, high-contrast palette taken from the CECAPI Figma Make prototype
// (const C in App.tsx). The app intentionally does NOT follow system light/dark
// mode: for low-vision users, contrast consistency matters more than platform
// convention, so the palette is pinned regardless of device theme.
val CecapiBackground = Color(0xFF121212)
val CecapiSurface = Color(0xFF1E1E1E)
val CecapiSurfaceElevated = Color(0xFF2A2A2A)
val CecapiBorder = Color(0xFF3A3A3A)

// El azul es el color predominante de la app (decisión de Pp) — el Figma usa dorado, pero
// se queda solo en Clasificación (Sections.Ranking), no como acento general.
val CecapiAccent = Color(0xFF38BDF8)

val CecapiTextPrimary = Color(0xFFFFFFFF)
val CecapiTextMuted = Color(0xFFC7C7C7)

val CecapiSuccess = Color(0xFF30D158)
val CecapiError = Color(0xFFFF453A)
val CecapiWarning = Color(0xFFF59E0B)

// Module accent colors used for dashboard cards (icons/borders), one per module
// so users who retain some vision can tell modules apart at a glance. Taken from
// Figma's SECTIONS map — muted/desaturated on purpose, per Pp's rule that only the
// icon box gets color and nothing in the app is fully saturated.
val ModuleVoiceAccent = Color(0xFF7FA8E0) // SECTIONS.chats
val ModuleLearningAccent = Color(0xFFA99BE0) // SECTIONS.activities
val ModuleDocumentsAccent = Color(0xFFE3B95B) // SECTIONS.documents
val ModuleCameraAccent = Color(0xFF7CC49A) // SECTIONS.camera
val ModuleAiAccent = Color(0xFF60A5FA) // not in Figma's SECTIONS map; kept as-is
val ModuleEnvironmentAccent = Color(0xFF7CC49A) // SECTIONS.camera (same hub as el lector de documentos)

/** One Figma SECTIONS entry: the icon tint and the icon badge's own muted background. */
data class ModuleSection(val color: Color, val bg: Color)

// Exact pairs from Figma's `const SECTIONS` in App.tsx — used so icon badges get the
// same muted background the prototype uses, not just an alpha-blended guess of it.
object Sections {
    val Camera = ModuleSection(Color(0xFF7CC49A), Color(0xFF2C3731))
    val Documents = ModuleSection(Color(0xFFE3B95B), Color(0xFF3C3527))
    val Activities = ModuleSection(Color(0xFFA99BE0), Color(0xFF33313B))
    val Chats = ModuleSection(Color(0xFF7FA8E0), Color(0xFF2D333B))
    val Personalization = ModuleSection(Color(0xFFD49BC0), Color(0xFF393136))
    val Settings = ModuleSection(Color(0xFFA0A4AB), Color(0xFF2E2E2F))
    val Ranking = ModuleSection(Color(0xFFFFD60A), Color(0xFF403A1C))
    val Emergency = ModuleSection(Color(0xFFFF6B5E), Color(0xFF3D2523))
    val Reportar = ModuleSection(Color(0xFFF59E0B), Color(0xFF332518))
    val Ayuda = ModuleSection(Color(0xFF38BDF8), Color(0xFF0E2A3A))
}
