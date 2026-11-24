package com.ebchat.desktop.platform

/**
 * OS permission model for the desktop client.
 *
 * Android requests runtime permissions (POST_NOTIFICATIONS, RECORD_AUDIO, CAMERA…).
 * Desktop operating systems work differently, so Convo Desktop converts each
 * Android permission into its native desktop equivalent and explains where the
 * user grants it. The app never needs dangerous permissions before login.
 */
data class DesktopPermission(
    val id: String,
    val title: String,
    val androidEquivalent: String,
    val howToGrant: String,
    val requiredFor: String,
    val status: PermissionStatus
)

enum class PermissionStatus { GRANTED, NOT_REQUIRED, MANUAL }

object PermissionCenter {

    fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("windows")
    fun isLinux(): Boolean = System.getProperty("os.name", "").lowercase().contains("linux")
    fun arch(): String = System.getProperty("os.arch", "unknown")

    fun permissions(): List<DesktopPermission> {
        val onWindows = isWindows()
        return listOf(
            DesktopPermission(
                id = "mic",
                title = "Microphone (voice notes & calls)",
                androidEquivalent = "android.permission.RECORD_AUDIO",
                howToGrant = if (onWindows)
                    "Windows Settings → Privacy & security → Microphone → allow desktop apps"
                else
                    "PulseAudio/PipeWire: no per-app prompt; the desktop session controls mic access",
                requiredFor = "Voice messages, 1:1 calls",
                status = PermissionStatus.GRANTED
            ),
            DesktopPermission(
                id = "notifications",
                title = "Desktop notifications",
                androidEquivalent = "android.permission.POST_NOTIFICATIONS",
                howToGrant = if (onWindows)
                    "Windows Settings → System → Notifications → enable Convo Chat"
                else
                    "xdg-desktop-portal notification service (notify-send); most desktops allow by default",
                requiredFor = "New message and call alerts",
                status = PermissionStatus.GRANTED
            ),
            DesktopPermission(
                id = "files",
                title = "File access (chat attachments, backups)",
                androidEquivalent = "storage permissions",
                howToGrant = if (onWindows)
                    "No permission prompt; Files selected through the native Windows file picker only"
                else
                    "GTK/portal file chooser; sandboxed access limited to the chosen file",
                requiredFor = "Sending files, chat backup export",
                status = PermissionStatus.GRANTED
            ),
            DesktopPermission(
                id = "autostart",
                title = "Start with the system",
                androidEquivalent = "RECEIVE_BOOT_COMPLETED",
                howToGrant = if (onWindows)
                    "Task Manager → Startup apps (after an installer option)"
                else
                    "XDG autostart: ~/.config/autostart/convochat.desktop",
                requiredFor = "Staying online for presence",
                status = PermissionStatus.MANUAL
            ),
            DesktopPermission(
                id = "run_in_background",
                title = "Background execution (Doze equivalent)",
                androidEquivalent = "battery / wake locks",
                howToGrant = if (onWindows)
                    "Not restricted on desktop; closing the window keeps presence if you enable the tray"
                else
                    "Not restricted on desktop; systemd user session keeps the process alive",
                requiredFor = "Presence, incoming-call listening",
                status = PermissionStatus.NOT_REQUIRED
            )
        )
    }
}
