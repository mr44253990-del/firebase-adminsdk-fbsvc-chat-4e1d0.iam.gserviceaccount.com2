package com.example.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

@androidx.media3.common.util.UnstableApi
object VideoPlayerManager {
    private const val CACHE_BYTES = 512L * 1024L * 1024L
    private const val PRELOAD_BYTES = 3L * 1024L * 1024L
    private var cache: SimpleCache? = null
    private var cacheFactory: CacheDataSource.Factory? = null
    private var player: ExoPlayer? = null
    private var owner: String? = null
    private var currentUrl: String? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val preloading = Collections.synchronizedSet(mutableSetOf<String>())

    @Synchronized
    private fun ensure(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val database = StandaloneDatabaseProvider(app)
        val simpleCache = SimpleCache(File(app.cacheDir, "media3_reel_cache"), LeastRecentlyUsedCacheEvictor(CACHE_BYTES), database)
        cache = simpleCache
        val upstream = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(25_000)
        val factory = CacheDataSource.Factory().setCache(simpleCache).setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheFactory = factory
        return ExoPlayer.Builder(app).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().also {
            it.repeatMode = Player.REPEAT_MODE_ONE
            player = it
        }
    }

    @Synchronized
    fun acquire(context: Context, ownerId: String, url: String, play: Boolean, sound: Boolean = true): ExoPlayer {
        val exo = ensure(context)
        owner = ownerId
        if (currentUrl != url) {
            currentUrl = url
            val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
        }
        exo.volume = if (sound) 1f else 0f
        exo.playWhenReady = play
        return exo
    }

    @Synchronized
    fun pause(ownerId: String? = null) {
        if (ownerId == null || owner == ownerId) player?.playWhenReady = false
    }

    @Synchronized
    fun detach(ownerId: String) {
        if (owner == ownerId) {
            player?.playWhenReady = false
            owner = null
        }
    }

    fun preload(context: Context, urls: List<String>) {
        ensure(context)
        urls.filter { it.startsWith("http") }.distinct().forEach { url ->
            if (!preloading.add(url)) return@forEach
            executor.execute {
                try {
                    val dataSource = cacheFactory?.createDataSource() ?: return@execute
                    val spec = DataSpec.Builder().setUri(url).setPosition(0).setLength(PRELOAD_BYTES).build()
                    CacheWriter(dataSource, spec, null, null).cache()
                } catch (_: Exception) {
                } finally {
                    preloading.remove(url)
                }
            }
        }
    }

    @Synchronized
    fun release() {
        player?.release(); player = null; currentUrl = null; owner = null
        cache?.release(); cache = null; cacheFactory = null
    }
}
