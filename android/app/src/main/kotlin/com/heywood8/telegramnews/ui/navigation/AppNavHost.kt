package com.heywood8.telegramnews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.ui.MainScreen
import com.heywood8.telegramnews.ui.auth.AuthScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle(initialValue = AuthState.Unknown)

    LaunchedEffect(authState) {
        when (authState) {
            AuthState.LoggedIn -> navController.navigate(NavRoutes.Main.route) {
                popUpTo(NavRoutes.Auth.route) { inclusive = true }
            }
            AuthState.LoggedOut,
            AuthState.WaitingForPhone -> navController.navigate(NavRoutes.Auth.route) {
                popUpTo(NavRoutes.Main.route) { inclusive = true }
            }
            else -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Auth.route,
    ) {
        composable(NavRoutes.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(NavRoutes.Main.route) {
                        popUpTo(NavRoutes.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(NavRoutes.Main.route) {
            MainScreen()
        }
    }
}
