package com.ebchat.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
    val onChange: () -> Unit = { tick++ }

    LaunchedEffect(Unit) { state.start(onChange) }
    LaunchedEffect(Unit) { Automation.maybeStart(state, onChange) }

    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
        title = "Convo Chat — Desktop (Windows & Linux)"
    ) {
        window.minimumSize = Dimension(960, 640)
        ConvoDesktopTheme(state.appearance) {
            key(tick) {
                when (state.route) {
                    AppState.Route.Splash -> SplashScreen()
                    AppState.Route.Login -> LoginScreen(state, onChange)
                    AppState.Route.Home -> HomeScreen(state, onChange)
                    AppState.Route.Chat -> ChatScreen(state, onChange)
                    AppState.Route.Settings -> SettingsScreen(state, onChange)
                    AppState.Route.Permissions -> PermissionsScreen(state, onChange)
                }
            }
        }
    }
}
