package com.heywood8.telegramnews.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heywood8.telegramnews.domain.model.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authState) {
        if (authState == AuthState.LoggedIn) onAuthSuccess()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val showBack = authState == AuthState.WaitingForCode || authState == AuthState.WaitingForPassword

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = viewModel::resetAuth) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                when (authState) {
                    AuthState.WaitingForPhone, AuthState.Unknown, AuthState.LoggedOut -> PhoneStep(
                        onSubmit = viewModel::sendPhoneNumber
                    )
                    AuthState.WaitingForCode -> CodeStep(
                        onSubmit = viewModel::sendCode
                    )
                    AuthState.WaitingForPassword -> PasswordStep(
                        onSubmit = viewModel::sendPassword
                    )
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PhoneStep(onSubmit: (String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    AuthStepLayout(
        title = "Enter your phone number",
        subtitle = "Include country code, e.g. +1 555 000 1234",
        inputLabel = "Phone number",
        value = phone,
        onValueChange = { phone = it },
        keyboardType = KeyboardType.Phone,
        buttonLabel = "Next",
        onSubmit = { onSubmit(phone) },
        enabled = phone.isNotBlank(),
    )
}

@Composable
private fun CodeStep(onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AuthStepLayout(
        title = "Enter the code",
        subtitle = "We sent a code to your Telegram app",
        inputLabel = "Verification code",
        value = code,
        onValueChange = { code = it },
        keyboardType = KeyboardType.Number,
        buttonLabel = "Verify",
        onSubmit = { onSubmit(code) },
        enabled = code.isNotBlank(),
    )
}

@Composable
private fun PasswordStep(onSubmit: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AuthStepLayout(
        title = "Two-step verification",
        subtitle = "Enter your cloud password",
        inputLabel = "Password",
        value = password,
        onValueChange = { password = it },
        keyboardType = KeyboardType.Password,
        buttonLabel = "Continue",
        onSubmit = { onSubmit(password) },
        enabled = password.isNotBlank(),
        isPassword = true,
    )
}

@Composable
private fun AuthStepLayout(
    title: String,
    subtitle: String,
    inputLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    buttonLabel: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
    isPassword: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(inputLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (enabled) onSubmit() }),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(buttonLabel)
        }
    }
}
