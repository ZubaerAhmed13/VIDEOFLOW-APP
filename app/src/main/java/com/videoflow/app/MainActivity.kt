package com.videoflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.videoflow.app.ui.HomeViewModel
import com.videoflow.app.ui.screens.DeviceCapabilityScreen
import com.videoflow.app.ui.screens.DiagnosticsScreen
import com.videoflow.app.ui.screens.EditorScreen
import com.videoflow.app.ui.screens.HomeScreen
import com.videoflow.app.ui.screens.SettingsScreen
import com.videoflow.app.ui.screens.Step2ProjectScreen
import com.videoflow.app.ui.theme.VideoFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VideoFlowTheme { VideoFlowNavigation() } }
    }
}

@Composable
private fun VideoFlowNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm: HomeViewModel = hiltViewModel()
            HomeScreen(
                onOpen = { nav.navigate("project/$it") },
                onSettings = { nav.navigate("settings") },
                vm = vm
            )
        }
        composable(
            route = "project/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            Step2ProjectScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigate("editor/$id") },
                vm = hiltViewModel()
            )
        }
        composable(
            route = "editor/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            EditorScreen(
                id = requireNotNull(entry.arguments?.getString("id")),
                onBack = { nav.popBackStack() },
                vm = hiltViewModel()
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onDevice = { nav.navigate("device") },
                onDiagnostics = { nav.navigate("diagnostics") }
            )
        }
        composable("device") {
            DeviceCapabilityScreen(onBack = { nav.popBackStack() }, vm = hiltViewModel())
        }
        composable("diagnostics") {
            DiagnosticsScreen(onBack = { nav.popBackStack() }, vm = hiltViewModel())
        }
    }
}
