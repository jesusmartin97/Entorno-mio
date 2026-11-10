package com.cecapi.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiAssistantScreen
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentReaderScreen
import com.cecapi.app.feature.modulo7_entorno.EnvironmentScreen
import com.cecapi.app.feature.modulo10_accesibilidad.PantallaAccesibilidad
import com.cecapi.app.feature.modulo6_aprendizaje.LearningScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.DashboardScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.EditAccountScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.HomeScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.LoginScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RegisterScreen
import com.cecapi.app.core.ui.WakeScope
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CameraHubScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ChatsScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.GestionScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.PersonalizationScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RankingScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SoporteScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.EmergenciasScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ReportarIncidenteScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SettingsScreen
import com.cecapi.app.feature.modulo5_solicitudes.RequestsScreen
import com.cecapi.app.feature.modulo2_asistentevoz.VoiceAssistantScreen

@Composable
fun CecapiNavGraph(navController: NavHostController = rememberNavController()) {
    val navVoice: NavVoiceViewModel = hiltViewModel()
    DisposableEffect(navController) {
        navVoice.voiceEngine.activeRoute = navController.currentDestination?.route
        var profundidad = navController.currentBackStack.value.size
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            navVoice.voiceEngine.activeRoute = destination.route
            val nueva = controller.currentBackStack.value.size
            navVoice.voiceEngine.stopSpeaking()
            if (nueva < profundidad) {
                val menu = destination.route == CecapiDestinations.DASHBOARD
                navVoice.voiceEngine.speak(if (menu) "Volviendo al menú." else "Volviendo.")
            }
            profundidad = nueva
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = CecapiDestinations.HOME) {
        composable(CecapiDestinations.HOME) {
            HomeScreen(
                onNavigateToLogin = { navController.navigate(CecapiDestinations.LOGIN) },
                onNavigateToRegister = { navController.navigate(CecapiDestinations.REGISTER) },
                onNavigateToModule = { route -> navController.navigate(route) },
                onNavigateToSettings = { navController.navigate(CecapiDestinations.SETTINGS) },
            )
        }
        composable(CecapiDestinations.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(CecapiDestinations.DASHBOARD) {
                        popUpTo(CecapiDestinations.HOME) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(CecapiDestinations.REGISTER) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(CecapiDestinations.REGISTER) {
            WakeScope {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(CecapiDestinations.DASHBOARD) {
                            popUpTo(CecapiDestinations.HOME) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(CecapiDestinations.DASHBOARD) {
            DashboardScreen(
                onModuleClick = { route -> navController.navigate(route) },
                onLoggedOut = {
                    navController.navigate(CecapiDestinations.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(CecapiDestinations.SETTINGS) {
            WakeScope {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(CecapiDestinations.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToAccesibilidad = { navController.navigate(CecapiDestinations.ACCESIBILIDAD) },
                )
            }
        }
        composable(CecapiDestinations.PERSONALIZATION) {
            WakeScope { PersonalizationScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.GESTION) {
            WakeScope { GestionScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.EMERGENCIAS) {
            WakeScope { EmergenciasScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.REPORTAR_INCIDENTE) {
            WakeScope { ReportarIncidenteScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.SOPORTE) {
            WakeScope { SoporteScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.RANKING) {
            WakeScope { RankingScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.EDIT_ACCOUNT) {
            WakeScope { EditAccountScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.CHATS) {
            WakeScope {
                ChatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route -> navController.navigate(route) },
                )
            }
        }
        composable(CecapiDestinations.CAMERA_HUB) {
            WakeScope {
                CameraHubScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route -> navController.navigate(route) },
                )
            }
        }
        composable(CecapiDestinations.VOICE_ASSISTANT) {
            WakeScope {
                VoiceAssistantScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToModule = { route -> navController.navigate(route) },
                )
            }
        }
        composable(CecapiDestinations.AI_ASSISTANT) {
            WakeScope { AiAssistantScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.DOCUMENT_READER) {
            WakeScope {
                DocumentReaderScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route ->
                        // Switching camera mode replaces this screen instead of stacking a second one.
                        navController.popBackStack()
                        navController.navigate(route)
                    },
                )
            }
        }
        composable(CecapiDestinations.REQUESTS) {
            WakeScope { RequestsScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.LEARNING) {
            WakeScope { LearningScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.ENVIRONMENT) {
            WakeScope {
                EnvironmentScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route ->
                        navController.popBackStack()
                        navController.navigate(route)
                    },
                )
            }
        }
        composable(CecapiDestinations.ACCESIBILIDAD) {
            WakeScope { PantallaAccesibilidad(onBack = { navController.popBackStack() }) }
        }
    }
}
