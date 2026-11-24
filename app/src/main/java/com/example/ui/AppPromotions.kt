package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** One promoted app: picture, name, a short line and where "Download" leads. */
data class PromoApp(val id: String = "", val title: String = "", val subtitle: String = "", val imageUrl: String = "", val link: String = "", val badge: String = "")

data class PromoConfig(val enabled: Boolean = false, val heading: String = "Apps you may like", val apps: List<PromoApp> = emptyList())

/**
 * Admin-managed app promotions (v5.7). Stored in Firestore `app_config/promotions` — readable by
 * every signed-in user, writable only by the admin (existing security rule for app_config).
 */
object AppPromotions {
    private val _config = MutableStateFlow(PromoConfig())
    val config: StateFlow<PromoConfig> = _config.asStateFlow()
    private var listener: ListenerRegistration? = null
    private fun doc() = FirebaseFirestore.getInstance().collection("app_config").document("promotions")

    fun start() {
        if (listener != null) return
        listener = doc().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val apps = (snap.get("apps") as? List<*>).orEmpty().mapNotNull { raw ->
                (raw as? Map<*, *>)?.let { m ->
                    PromoApp(
                        id = m["id"]?.toString().orEmpty(), title = m["title"]?.toString().orEmpty(), subtitle = m["subtitle"]?.toString().orEmpty(),
                        imageUrl = m["imageUrl"]?.toString().orEmpty(), link = m["link"]?.toString().orEmpty(), badge = m["badge"]?.toString().orEmpty()
                    )
                }
            }.filter { it.title.isNotBlank() && it.link.isNotBlank() }
            _config.value = PromoConfig(snap.getBoolean("enabled") ?: false, snap.getString("heading").orEmpty().ifBlank { "Apps you may like" }, apps)
        }
    }

    fun stop() { listener?.remove(); listener = null; _config.value = PromoConfig() }

    fun save(config: PromoConfig, onDone: (Boolean) -> Unit) {
        doc().set(
            mapOf(
                "enabled" to config.enabled, "heading" to config.heading, "updatedAt" to System.currentTimeMillis(),
                "apps" to config.apps.map { mapOf("id" to it.id, "title" to it.title, "subtitle" to it.subtitle, "imageUrl" to it.imageUrl, "link" to it.link, "badge" to it.badge) }
            ), SetOptions.merge()
        ).addOnSuccessListener { AdminAudit.log("promotions_save", "enabled=${config.enabled} apps=${config.apps.size}"); onDone(true) }
            .addOnFailureListener { onDone(false) }
    }
}

/**
 * Store-style shelf shown in the feed: swipe sideways through the promoted apps. The centre
 * card is full size, neighbours are slightly smaller and dimmed; "Download" opens the link.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromoCarousel(config: PromoConfig, modifier: Modifier = Modifier) {
    if (!config.enabled || config.apps.isEmpty()) return
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { config.apps.size })
    val sheen by rememberInfiniteTransition(label = "promo_sheen").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "sheen")
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = .7f, stiffness = 220f)) }
    Column(
        modifier.fillMaxWidth().graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * 40f }
            .padding(vertical = 6.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(config.heading, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Sponsored", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 44.dp), pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth()) { page ->
            val app = config.apps[page]
            val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { val s = lerp(1f, .9f, distance); scaleX = s; scaleY = s; alpha = lerp(1f, .6f, distance) }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .16f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)), start = Offset(sheen * 300f, 0f), end = Offset(sheen * 300f + 600f, 500f)))
                    .border(1.dp, Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .6f), MaterialTheme.colorScheme.tertiary.copy(alpha = .25f))), RoundedCornerShape(26.dp))
                    .clickable { openInBrowser(context, app.link) }
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6)))), contentAlignment = Alignment.Center) {
                    Text(app.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 36.sp)
                    if (app.imageUrl.startsWith("http")) AsyncImage(app.imageUrl, app.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.badge.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(app.badge.take(10), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(CircleShape).background(Color(0xFFFF6FB5)).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
                Text(app.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.heightIn(min = 32.dp))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF19C37D), Color(0xFF14A3B8)))).clickable { openInBrowser(context, app.link) }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Download", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GalleryDots(config.apps.size, pager.currentPage, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Admin page: switch promotions on/off and manage the list of apps. */
@Composable
fun AdminPromotionsPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by AppPromotions.config.collectAsState()
    LaunchedEffect(Unit) { AppPromotions.start() }
    var enabled by remember(live) { mutableStateOf(live.enabled) }
    var heading by remember(live) { mutableStateOf(live.heading) }
    var apps by remember(live) { mutableStateOf(live.apps) }
    var editing by remember { mutableStateOf<PromoApp?>(null) }
    var uploading by remember { mutableStateOf(false) }

    editing?.let { current ->
        var title by remember(current.id) { mutableStateOf(current.title) }
        var subtitle by remember(current.id) { mutableStateOf(current.subtitle) }
        var link by remember(current.id) { mutableStateOf(current.link) }
        var badge by remember(current.id) { mutableStateOf(current.badge) }
        var image by remember(current.id) { mutableStateOf(current.imageUrl) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                uploading = true
                scope.launch {
                    val bytes = runCatching { compressChatImage(context, uri, hd = false) }.getOrNull()
                    if (bytes == null) { uploading = false; Toast.makeText(context, "Could not read that picture", Toast.LENGTH_SHORT).show() }
                    else viewModel.uploadFileToSupabase("chat_images", "promo_${System.currentTimeMillis()}.jpg", bytes, "image/jpeg",
                        onSuccess = { url -> image = url; uploading = false }, onFailure = { uploading = false; Toast.makeText(context, "Upload failed: $it", Toast.LENGTH_LONG).show() })
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!uploading) editing = null },
            title = { Text(if (apps.any { it.id == current.id }) "Edit app" else "Add app", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = !uploading) { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                            if (image.startsWith("http")) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                            if (uploading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Tap the square to choose the app picture.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(title, { title = it.take(40) }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subtitle, { subtitle = it.take(90) }, label = { Text("Short description") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(link, { link = it.trim().take(300) }, label = { Text("Download link (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            uploading = true
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri).use { it?.readBytes() } }.getOrNull() }
                                if (bytes == null) { uploading = false; Toast.makeText(context, "Could not read that APK", Toast.LENGTH_SHORT).show() }
                                else viewModel.uploadFileToSupabase("chat_files", "promo_${System.currentTimeMillis()}.apk", bytes, "application/vnd.android.package-archive",
                                    onSuccess = { url -> link = url; uploading = false; Toast.makeText(context, "APK uploaded — download link set", Toast.LENGTH_SHORT).show() },
                                    onFailure = { uploading = false; Toast.makeText(context, "Upload failed: $it", Toast.LENGTH_LONG).show() })
                            }
                        }
                    }
                    OutlinedButton(enabled = !uploading, onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (uploading) "Uploading APK…" else "Upload APK here")
                    }
                    OutlinedTextField(badge, { badge = it.take(10) }, label = { Text("Badge (optional: NEW, FREE…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank() && link.isNotBlank() && !uploading, onClick = {
                    val cleanLink = if (link.startsWith("http")) link else "https://$link"
                    val updated = current.copy(title = title.trim(), subtitle = subtitle.trim(), link = cleanLink, badge = badge.trim(), imageUrl = image)
                    apps = if (apps.any { it.id == current.id }) apps.map { if (it.id == current.id) updated else it } else apps + updated
                    editing = null
                }) { Text("Done") }
            },
            dismissButton = { TextButton(enabled = !uploading, onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF19C37D), Color(0xFF14A3B8), Color(0xFF6C5CE7)) else listOf(Color(0xFF475569), Color(0xFF334155)))).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App promotions", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(if (enabled) "ON — users see the shelf while scrolling the feed" else "OFF — hidden from every user", color = Color.White.copy(alpha = .88f), fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        OutlinedTextField(heading, { heading = it.take(40) }, label = { Text("Shelf title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        apps.forEachIndexed { index, app ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                    Text(app.title.take(1).uppercase(), fontWeight = FontWeight.Bold)
                    if (app.imageUrl.startsWith("http")) AsyncImage(app.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.link, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index > 0) IconButton(onClick = { apps = apps.toMutableList().also { val x = it.removeAt(index); it.add(index - 1, x) } }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.ArrowUpward, "Move up", Modifier.size(17.dp)) }
                IconButton(onClick = { editing = app }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "Edit", Modifier.size(17.dp)) }
                IconButton(onClick = { apps = apps.filterNot { it.id == app.id } }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "Remove", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedButton(enabled = apps.size < 12, onClick = { editing = PromoApp(id = "app_${System.currentTimeMillis()}") }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add an app (${apps.size}/12)")
        }
        if (apps.isNotEmpty()) {
            Text("Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            PromoCarousel(PromoConfig(true, heading.ifBlank { "Apps you may like" }, apps))
        }
        Button(onClick = {
            AppPromotions.save(PromoConfig(enabled, heading.trim().ifBlank { "Apps you may like" }, apps)) { ok ->
                Toast.makeText(context, if (ok) (if (enabled) "Saved — promotions are ON" else "Saved — promotions are OFF") else "Could not save (admin only)", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
    }
}
