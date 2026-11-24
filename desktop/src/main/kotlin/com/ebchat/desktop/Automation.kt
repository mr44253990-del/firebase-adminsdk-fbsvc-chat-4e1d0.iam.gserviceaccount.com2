package com.ebchat.desktop

import com.ebchat.desktop.ui.AppearanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * CI demo automation. Activated only when the caller provides:
 *   CONVO_DEMO_EMAIL, CONVO_DEMO_PASSWORD, CONVO_SCREENSHOT_DIR
 *
 * It signs in with the demo account, walks through the screens
 * (login, feed, permissions, appearance modes, chat), captures the
 * screen at every stage, and records a frame every 2 seconds so CI
 * can assemble a screen-recording video of the tour.
 */
object Automation {

    @Volatile
    private var started = false

    fun maybeStart(state: AppState, onChange: () -> Unit) {
        if (started) return
        val email = System.getenv("CONVO_DEMO_EMAIL")?.takeIf { it.isNotBlank() } ?: return
        val password = System.getenv("CONVO_DEMO_PASSWORD")?.takeIf { it.isNotBlank() } ?: return
        val dir = System.getenv("CONVO_SCREENSHOT_DIR")?.takeIf { it.isNotBlank() } ?: return
        started = true

        val out = File(dir).apply { mkdirs() }
        val counter = AtomicInteger(0)
        val robot = Robot()
        val screen = Toolkit.getDefaultToolkit().screenSize
        val rect = Rectangle(0, 0, screen.width, screen.height)

        fun shot() {
            runCatching {
                val name = "shot_%04d.png".format(counter.incrementAndGet())
                ImageIO.write(robot.createScreenCapture(rect), "png", File(out, name))
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Background frame recorder for the video (2 fps) during the whole tour.
                val recorder = launch {
                    while (true) {
                        shot()
                        delay(2000)
                    }
                }

                delay(2500) // splash screen visible
                waitFor(30_000) { state.route == AppState.Route.Login }
                delay(2000)

                state.signIn(email, password, onChange)
                waitFor(120_000) { state.route == AppState.Route.Home }
                delay(9000) // feed fully rendered

                state.go(AppState.Route.Permissions, onChange)
                delay(5000)

                state.go(AppState.Route.Settings, onChange)
                delay(4000)
                state.setAppearance(AppearanceMode.LIGHT, onChange)
                delay(4000)
                state.setAppearance(AppearanceMode.AMOLED, onChange)
                delay(4000)
                state.setAppearance(AppearanceMode.DARK, onChange)
                delay(3000)

                state.go(AppState.Route.Home, onChange)
                delay(5000)

                state.go(AppState.Route.Chat, onChange)
                delay(6000)

                state.go(AppState.Route.Home, onChange)
                delay(3000)

                recorder.cancel()
                delay(800)
            } catch (t: Throwable) {
                runCatching { shot() }
            } finally {
                Runtime.getRuntime().exit(0)
            }
        }
    }

    private suspend fun waitFor(timeoutMs: Long, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !cond()) delay(400)
    }
}
