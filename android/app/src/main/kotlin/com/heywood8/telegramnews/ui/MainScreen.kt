package com.heywood8.telegramnews.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.heywood8.telegramnews.ui.channels.ChannelListScreen
import com.heywood8.telegramnews.ui.feed.FeedScreen
import com.heywood8.telegramnews.ui.navigation.MainTab
import com.heywood8.telegramnews.ui.settings.SettingsScreen

private data class TabItem(val tab: MainTab, val icon: ImageVector, val label: String)

private val tabs = listOf(
    TabItem(MainTab.Feed, Icons.Default.Notifications, "Feed"),
    TabItem(MainTab.Channels, Icons.Default.List, "Channels"),
    TabItem(MainTab.Settings, Icons.Default.Settings, "Settings"),
)

@Composable
fun MainScreen() {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Feed) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { item ->
                item(
                    selected = currentTab == item.tab,
                    onClick = { currentTab = item.tab },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        }
    ) {
        when (currentTab) {
            MainTab.Feed -> FeedScreen()
            MainTab.Channels -> ChannelListScreen()
            MainTab.Settings -> SettingsScreen()
        }
    }
}
