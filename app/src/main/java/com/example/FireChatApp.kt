package com.example

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class – configures aggressive offline caching so the app behaves
 * like Facebook / Instagram: content stays visible offline and refreshes when
 * the connection returns.
 *
 *  - Realtime Database persistence  -> chats & presence cached on disk
 *  - Firestore persistence          -> users, posts, stories, notifications cached
 *  - Coil memory + disk cache       -> images / avatars stay visible offline
 */
class FireChatApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Enable Realtime Database disk persistence (must run before any DB usage)
        try {
            FirebaseDatabase.getInstance(
                "https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app"
            ).setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w("FireChatApp", "RTDB persistence: ${e.message}")
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            } catch (ex: Exception) {
                Log.w("FireChatApp", "RTDB persistence fallback: ${ex.message}")
            }
        }

        // Firestore offline cache (256 MB) – keeps feed available offline
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setCacheSizeBytes(256L * 1024L * 1024L)
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (e: Exception) {
            Log.w("FireChatApp", "Firestore settings: ${e.message}")
        }
    }

    /** Shared Coil image loader with generous caches -> buttery smooth scrolling. */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
