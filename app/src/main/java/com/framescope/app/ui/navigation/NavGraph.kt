package com.framescope.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.framescope.app.ui.screens.AboutScreen
import com.framescope.app.ui.screens.AppearanceScreen
import com.framescope.app.ui.screens.DashboardScreen
import com.framescope.app.ui.screens.OnboardingScreen
import com.framescope.app.ui.screens.OverlayCustomizationScreen
import com.framescope.app.ui.screens.PermissionsScreen
import com.framescope.app.ui.screens.SplashScreen
import com.framescope.app.ui.screens.thermal.ThermalDiagnosticsScreen
import com.framescope.app.ui.screens.performance.PerformanceScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object Appearance : Screen("appearance")
    object OverlayCustomization : Screen("overlay_customization")
    object Permissions : Screen("permissions")
    object About : Screen("about")
    object Performance : Screen("performance")
    object ThermalDiagnostics : Screen("thermal_diagnostics")
}

@Composable
fun FrameScopeNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) { popUpTo(0) } },
                onNavigateToDashboard = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = { navController.navigate(Screen.Dashboard.route) { popUpTo(0) } }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
                onNavigateToOverlayCustomization = { navController.navigate(Screen.OverlayCustomization.route) },
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToPerformance = { navController.navigate(Screen.Performance.route) },
                onNavigateToThermalDiagnostics = { navController.navigate(Screen.ThermalDiagnostics.route) }
            )
        }
        composable(Screen.Appearance.route) {
            AppearanceScreen(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.OverlayCustomization.route) {
            OverlayCustomizationScreen(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.Performance.route) {
            PerformanceScreen(onNavigateBack = { navController.safePopBackStack() })
        }
        composable(Screen.ThermalDiagnostics.route) {
            ThermalDiagnosticsScreen(onNavigateBack = { navController.safePopBackStack() })
        }
    }
}

private fun NavHostController.safePopBackStack() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}
