package com.heywood8.telegramnews.domain.model

sealed class AuthState {
    object Unknown : AuthState()
    object LoggedIn : AuthState()
    object LoggedOut : AuthState()
    data class Error(val message: String) : AuthState()
}
