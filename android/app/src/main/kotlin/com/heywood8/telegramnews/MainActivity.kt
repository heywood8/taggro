package com.heywood8.telegramnews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.heywood8.telegramnews.ui.navigation.AppNavHost
import com.heywood8.telegramnews.ui.theme.TelegramNewsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TelegramNewsTheme {
                AppNavHost()
            }
        }
    }
}
