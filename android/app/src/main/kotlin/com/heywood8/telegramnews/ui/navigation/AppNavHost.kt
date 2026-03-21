package com.heywood8.telegramnews.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.ui.MainScreen

@Composable
fun AppNavHost(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle(initialValue = AuthState.Unknown)

    when (authState) {
        is AuthState.LoggedIn -> MainScreen()
        is AuthState.Error -> {
            val msg = (authState as AuthState.Error).message
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Authentication error: $msg",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
