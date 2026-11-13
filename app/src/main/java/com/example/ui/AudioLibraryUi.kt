package com.example.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AudioLibraryApi
import com.example.data.AudioStreamPlayer
import com.example.data.AudioTrack
import com.example.data.FlagshipConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun sourceLabel(source: String) = when (source) { "audius" -> "Audius"; "itunes" -> "iTunes"; "radio" -> "Radio"; else -> "Audio" }
private fun sourceColors(source: String) = when (source) {
    "audius" -> listOf(Color(0xFFCC0FE0), Color(0xFF7E1BCC))
    "itunes" -> listOf(Color(0xFFFF5E62), Color(0xFFFF2D55))
    "radio" -> listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
    else -> listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6))
}

/** Catalogues the admin has switched on (empty when the audio library is off). */
fun enabledAudioSources(config: FlagshipConfig): List<String> = if (!config.audioLibraryEnabled) emptyList() else buildList {
    if (config.audioAudiusEnabled) add("audius")
    if (config.audioItunesEnabled) add("itunes")
    if (config.audioRadioEnabled) add("radio")
}

/** Animated equaliser bars shown while a track is playing. */
@Composable
fun EqualizerBars(color: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "eq")
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(520, 380, 640, 450).forEach { duration ->
            val h by motion.animateFloat(.25f, 1f, infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar")
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) h else .3f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

/**
 * Audio library picker: one tab per catalogue the admin enabled, search with trending results
 * when the box is empty, tap a row to preview it and "Use" to attach it.
 */
@Composable
fun AudioLibrarySheet(config: FlagshipConfig, onPick: (AudioTrack) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sources = remember(config) { enabledAudioSources(config) }
    var source by remember(sources) { mutableStateOf(sources.firstOrNull().orEmpty()) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val playing by AudioStreamPlayer.playingUrl.collectAsState()
    val loading by AudioStreamPlayer.loading.collectAsState()
    LaunchedEffect(source, submitted) {
        if (source.isBlank()) return@LaunchedEffect
        busy = true; failed = false
        val found = runCatching { AudioLibraryApi.search(source, submitted, config) }.getOrDefault(emptyList())
        results = found; busy = false; failed = found.isEmpty()
    }
    // Search as you type (after a short pause).
    LaunchedEffect(query) { delay(650); if (query.trim() != submitted) submitted = query.trim() }
    DisposableEffect(Unit) { onDispose { AudioStreamPlayer.stop() } }
    val spin by rememberInfiniteTransition(label = "disc").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.95f).fillMaxHeight(.88f), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(sourceColors(source) + Color(0xFF1B1548))).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).graphicsLayer { rotationZ = if (playing.isNotBlank()) spin else 0f }.clip(CircleShape).background(Color.Black.copy(alpha = .35f)).border(2.dp, Color.White.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Album, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Audio library", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
                            Text("Songs, independent artists and live radio", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    }
                }
                if (sources.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("The audio library is turned off by the admin.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    return@Column
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { s ->
                        val on = s == source
                        Row(
                            Modifier.weight(1f).clip(CircleShape)
                                .background(if (on) Brush.horizontalGradient(sourceColors(s)) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
                                .clickable { if (!on) { AudioStreamPlayer.stop(); results = emptyList(); source = s } }.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(when (s) { "radio" -> Icons.Default.Radio; "itunes" -> Icons.Default.MusicNote; else -> Icons.Default.GraphicEq }, null, tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(sourceLabel(s), color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                    placeholder = { Text(when (source) { "radio" -> "Search stations (empty = popular in Bangladesh)"; "itunes" -> "Search songs or artists"; else -> "Search Audius (empty = trending)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
                    shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                Text(
                    when (source) { "itunes" -> "30-second previews from the iTunes catalogue"; "radio" -> "Live stations from Radio Browser — they keep playing until stopped"; else -> "Full tracks from independent artists on Audius" },
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                when {
                    busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Nothing found. Try other words, or check the connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results, key = { it.id }) { track ->
                            val isPlaying = playing == track.url
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .background(if (isPlaying) Brush.horizontalGradient(sourceColors(source).map { it.copy(alpha = .22f) }) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))))
                                    .clickable { AudioStreamPlayer.toggle(track.url) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(sourceColors(source))), contentAlignment = Alignment.Center) {
                                    Icon(if (source == "radio") Icons.Default.Radio else Icons.Default.MusicNote, null, tint = Color.White)
                                    if (track.artwork.startsWith("http")) AsyncImage(track.artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                                    if (isPlaying) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else EqualizerBars(Color.White, true)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        track.artist.ifBlank { sourceLabel(source) } + if (track.durationSec > 0 && source == "audius") "  ·  %d:%02d".format(track.durationSec / 60, track.durationSec % 60) else "",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { AudioStreamPlayer.toggle(track.url) }) { Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, if (isPlaying) "Stop" else "Preview", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
                                Button(
                                    onClick = { AudioStreamPlayer.stop(); onPick(track); Toast.makeText(context, "Added: ${track.title}", Toast.LENGTH_SHORT).show(); onDismiss() },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), shape = CircleShape
                                ) { Text("Use", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact player card for an attached track (composer preview, feed posts, story viewer). */
@Composable
fun AudioTrackChip(title: String, artist: String, artwork: String, url: String, source: String, modifier: Modifier = Modifier, light: Boolean = false, onRemove: (() -> Unit)? = null) {
    val playing by AudioStreamPlayer.playingUrl.collectAsState()
    val loading by AudioStreamPlayer.loading.collectAsState()
    val isPlaying = playing == url
    val spin by rememberInfiniteTransition(label = "chip_disc").animateFloat(0f, 360f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "spin")
    val ink = if (light) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(sourceColors(source).map { it.copy(alpha = if (light) .55f else .18f) }))
            .border(1.dp, sourceColors(source).first().copy(alpha = .5f), RoundedCornerShape(20.dp))
            .clickable { AudioStreamPlayer.toggle(url) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).graphicsLayer { rotationZ = if (isPlaying && !loading) spin else 0f }.clip(CircleShape).background(Brush.linearGradient(sourceColors(source))), contentAlignment = Alignment.Center) {
            Icon(if (source == "radio") Icons.Default.Radio else Icons.Default.MusicNote, null, tint = Color.White)
            if (artwork.startsWith("http")) AsyncImage(artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = .6f)))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { "Audio" }, color = ink, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(artist, sourceLabel(source)).filter { it.isNotBlank() }.joinToString("  ·  "), color = ink.copy(alpha = .7f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isPlaying) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else EqualizerBars(sourceColors(source).first(), true); Spacer(Modifier.width(8.dp)) }
        Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, if (isPlaying) "Stop" else "Play", tint = if (light) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        if (onRemove != null) IconButton(onClick = { AudioStreamPlayer.stop(); onRemove() }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Remove", tint = ink.copy(alpha = .8f), modifier = Modifier.size(18.dp)) }
    }
}

/** Admin page: master switch, catalogues, identity settings, save and a live test. */
@Composable
fun AdminAudioLibraryPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by viewModel.flagshipConfig.collectAsState()
    var enabled by remember(config.updatedAt) { mutableStateOf(config.audioLibraryEnabled) }
    var audius by remember(config.updatedAt) { mutableStateOf(config.audioAudiusEnabled) }
    var itunes by remember(config.updatedAt) { mutableStateOf(config.audioItunesEnabled) }
    var radio by remember(config.updatedAt) { mutableStateOf(config.audioRadioEnabled) }
    var appName by remember(config.updatedAt) { mutableStateOf(config.audioAudiusAppName) }
    var host by remember(config.updatedAt) { mutableStateOf(config.audioAudiusHost) }
    var country by remember(config.updatedAt) { mutableStateOf(config.audioItunesCountry) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    fun draft() = config.copy(
        audioLibraryEnabled = enabled, audioAudiusEnabled = audius, audioItunesEnabled = itunes, audioRadioEnabled = radio,
        audioAudiusAppName = appName.trim().ifBlank { "ConvoChat" }, audioAudiusHost = host.trim().ifBlank { "https://discoveryprovider.audius.co" },
        audioItunesCountry = country.trim().uppercase().take(2).ifBlank { "US" }
    )
    if (showPreview) AudioLibrarySheet(draft().copy(audioLibraryEnabled = true), onPick = {}, onDismiss = { showPreview = false })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF7E1BCC), Color(0xFFFF2D55), Color(0xFF0083B0)) else listOf(Color(0xFF475569), Color(0xFF334155))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Audio library", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(if (enabled) "ON — music is available in posts and stories for everyone" else "OFF — hidden from every user", color = Color.White.copy(alpha = .88f), fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = { on -> enabled = on; if (on && !audius && !itunes && !radio) { audius = true; itunes = true; radio = true } })
        }
        Text("Catalogues (all follow the master switch)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        listOf(
            Triple("Audius", "Full tracks by independent artists · needs only an app name", "audius"),
            Triple("iTunes Search", "30-second previews of commercial songs · no key", "itunes"),
            Triple("Radio Browser", "Live radio stations worldwide · no key", "radio")
        ).forEach { (title, sub, key) ->
            val value = when (key) { "audius" -> audius; "itunes" -> itunes; else -> radio }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(sourceColors(key))), contentAlignment = Alignment.Center) { Icon(when (key) { "radio" -> Icons.Default.Radio; "itunes" -> Icons.Default.MusicNote; else -> Icons.Default.GraphicEq }, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp); Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = value && enabled, enabled = enabled, onCheckedChange = { when (key) { "audius" -> audius = it; "itunes" -> itunes = it; else -> radio = it } })
            }
        }
        Text("API configuration", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        OutlinedTextField(appName, { appName = it.take(40) }, label = { Text("Audius app name") }, supportingText = { Text("Sent as app_name with every Audius request.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(host, { host = it.take(120) }, label = { Text("Audius API host") }, supportingText = { Text("Default: https://discoveryprovider.audius.co") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(country, { country = it.filter(Char::isLetter).take(2).uppercase() }, label = { Text("iTunes Store country (2 letters)") }, supportingText = { Text("US, GB, IN, BD … decides which songs are found.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val d = draft()
                viewModel.updateFlagshipFields(mapOf(
                    "audioLibraryEnabled" to d.audioLibraryEnabled, "audioAudiusEnabled" to d.audioAudiusEnabled, "audioItunesEnabled" to d.audioItunesEnabled,
                    "audioRadioEnabled" to d.audioRadioEnabled, "audioAudiusAppName" to d.audioAudiusAppName, "audioAudiusHost" to d.audioAudiusHost, "audioItunesCountry" to d.audioItunesCountry
                )) { ok -> Toast.makeText(context, if (ok) (if (d.audioLibraryEnabled) "Saved — audio library is ON for everyone" else "Saved — audio library is OFF for everyone") else "Could not save", Toast.LENGTH_LONG).show() }
            }, modifier = Modifier.weight(1f), shape = CircleShape) { Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
            OutlinedButton(enabled = !testing, onClick = {
                testing = true; testResult = ""
                scope.launch {
                    val counts = runCatching { AudioLibraryApi.test(draft()) }.getOrDefault(emptyMap())
                    testResult = if (counts.isEmpty()) "Test failed — no connection?" else counts.entries.joinToString("\n") { (name, n) -> (if (n > 0) "✅ " else "❌ ") + "$name: $n results" }
                    testing = false
                }
            }, modifier = Modifier.weight(1f), shape = CircleShape) { if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test APIs") }
        }
        OutlinedButton(onClick = { showPreview = true }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Icon(Icons.Default.Headphones, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Preview the library") }
        if (testResult.isNotBlank()) Text(testResult, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(12.dp))
        Text("No secret keys are needed for these three services. Tracks are streamed from the provider; only the track details are stored with a post or story.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
