package com.videoflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.videoflow.app.ui.product.PreferencesViewModel
import com.videoflow.app.ui.screens.AboutScreen
import com.videoflow.app.ui.screens.DeviceCapabilityScreen
import com.videoflow.app.ui.screens.DiagnosticsScreen
import com.videoflow.app.ui.screens.EditorScreen
import com.videoflow.app.ui.screens.PrivacyScreen
import com.videoflow.app.ui.screens.ProductExportScreen
import com.videoflow.app.ui.screens.ProductHomeScreen
import com.videoflow.app.ui.screens.ProductOnboardingScreen
import com.videoflow.app.ui.screens.ProductSettingsScreen
import com.videoflow.app.ui.screens.Step2ProjectScreen
import com.videoflow.app.ui.theme.VideoFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val preferencesViewModel: PreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by preferencesViewModel.state.collectAsState()
            VideoFlowTheme(appearance = preferences.appearance) {
                VideoFlowNavigation(preferencesViewModel)
            }
        }
    }
}

@Composable
private fun VideoFlowNavigation(preferencesViewModel: PreferencesViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "onboarding") {
        composable("onboarding") {
            val preferences by preferencesViewModel.state.collectAsState()
            LaunchedEffect(preferences.onboardingComplete) {
                if (preferences.onboardingComplete) {
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                }
            }
            if (!preferences.onboardingComplete) {
                ProductOnboardingScreen(
                    onComplete = { },
                    onSkip = { },
                    markComplete = true,
                    preferencesViewModel = preferencesViewModel
                )
            }
        }
        composable("home") {
            ProductHomeScreen(
                onOpen = { nav.navigate("editor/$it") },
                onProjectDetails = { nav.navigate("project/$it") },
                onSettings = { nav.navigate("settings") },
                vm = hiltViewModel()
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
                onExport = { nav.navigate("export/$id") },
                vm = hiltViewModel()
            )
        }
        composable(
            route = "editor/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            EditorScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onExport = { nav.navigate("export/$id") },
                vm = hiltViewModel()
            )
        }
        composable(
            route = "export/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            ProductExportScreen(
                id = requireNotNull(entry.arguments?.getString("id")),
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
                vm = hiltViewModel()
            )
        }
        composable("settings") {
            ProductSettingsScreen(
                onBack = { nav.popBackStack() },
                onDevice = { nav.navigate("device") },
                onDiagnostics = { nav.navigate("diagnostics") },
                onPrivacy = { nav.navigate("privacy") },
                onAbout = { nav.navigate("about") },
                onIntroduction = { nav.navigate("introduction") },
                preferencesViewModel = preferencesViewModel
            )
        }
        composable("privacy") { PrivacyScreen(onBack = { nav.popBackStack() }) }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        composable("introduction") {
            ProductOnboardingScreen(
                onComplete = { nav.popBackStack() },
                onSkip = { nav.popBackStack() },
                markComplete = false,
                preferencesViewModel = preferencesViewModel
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
