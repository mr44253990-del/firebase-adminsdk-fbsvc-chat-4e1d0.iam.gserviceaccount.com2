package com.example.data

import android.net.Uri

data class IncomingSharePayload(
    val text: String = "",
    val uris: List<Uri> = emptyList(),
    val mimeType: String = "text/plain"
)
