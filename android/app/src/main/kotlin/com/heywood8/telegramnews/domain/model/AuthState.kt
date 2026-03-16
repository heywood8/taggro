package com.heywood8.telegramnews.domain.model

sealed class AuthState {
    object Unknown : AuthState()
    object WaitingForPhone : AuthState()
    object WaitingForCode : AuthState()
    object WaitingForPassword : AuthState()
    object LoggedIn : AuthState()
    object LoggedOut : AuthState()
}
