package com.ebchat.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.ebchat.desktop.ui.ChatScreen
import com.ebchat.desktop.ui.ConvoDesktopTheme
import com.ebchat.desktop.ui.HomeScreen
import com.ebchat.desktop.ui.LoginScreen
import com.ebchat.desktop.ui.PermissionsScreen
import com.ebchat.desktop.ui.SettingsScreen
import com.ebchat.desktop.ui.SplashScreen
import java.awt.Dimension

fun main() = application {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val state = remember { AppState(scope) }

    LaunchedEffect(Unit) { state.start { tick++ } }

    Window(
        onCloseRequest = { exitApplication() },
        title = "Convo Chat — Desktop (Windows & Linux)",
        width = 1100.dp,
        height = 720.dp
    ) {
        window.minimumSize = Dimension(960, 640)
        ConvoDesktopTheme(state.appearance) {
            when (state.route) {
                AppState.Route.Splash -> SplashScreen()
                AppState.Route.Login -> LoginScreen(state) { tick++ }
                AppState.Route.Home -> HomeScreen(state) { tick++ }
                AppState.Route.Chat -> ChatScreen(state) { tick++ }
                AppState.Route.Settings -> SettingsScreen(state) { tick++ }
                AppState.Route.Permissions -> PermissionsScreen(state) { tick++ }
            }
        }
    }
}
