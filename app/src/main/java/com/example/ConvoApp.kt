package com.example

import android.app.Application
import com.example.call.CallFloatingBubble

/** Process entry point: installs the app-wide visibility tracker used by the floating call bubble. */
class ConvoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallFloatingBubble.install(this)
    }
}
