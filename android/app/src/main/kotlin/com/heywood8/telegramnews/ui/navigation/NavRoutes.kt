package com.heywood8.telegramnews.ui.navigation

sealed class NavRoutes(val route: String) {
    object Main : NavRoutes("main")
}

enum class MainTab(val route: String, val label: String) {
    Feed("feed", "Feed"),
    Channels("channels", "Channels"),
    Settings("settings", "Settings"),
}
