package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Target of `convochat://admin/...` links (e.g. the Gmail OAuth success page). It only brings the
 * already-open Convo Chat screen back to the front, so the admin lands where they started and the
 * panel re-reads the token the Worker stored on the server.
 */
class DeepLinkReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        finish()
    }
}
