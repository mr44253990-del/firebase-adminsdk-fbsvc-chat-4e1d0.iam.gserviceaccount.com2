package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.Post
import com.example.data.Story
import com.example.data.User
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Home feed — stories on top (Instagram rings), posts below (Facebook style):
 * likes, comments, views, time-ago, privacy, auto-playing videos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    onOpenProfile: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNotes: () -> Unit
) {
    val currentUser by viewModel.currentUserState.collectAsState()
    val posts by feedViewModel.posts.collectAsState()
    val stories by feedViewModel.stories.collectAsState()
    val feedLoading by feedViewModel.feedLoading.collectAsState()
    val uploading by feedViewModel.uploading.collectAsState()
    val unreadNotifications by feedViewModel.unreadNotifications.collectAsState()

    var showCreatePost by remember { mutableStateOf(false) }
    var commentsPost by remember { mutableStateOf<Post?>(null) }
    var storyToView by remember { mutableStateOf<List<Story>?>(null) }
    var storyStartIndex by remember { mutableStateOf(0) }
    var showAddStory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { feedViewModel.start() }

    val myUid = currentUser?.uid ?: ""
    val myStories = stories.filter { it.authorId == myUid }
    val otherStories = stories.filter { it.authorId != myUid }
        .groupBy { it.authorId }
        .values.toList()

    GlassBackground(bubbleCount = 9) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- Top bar ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientText("FireChat", MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenNotes) {
                    Icon(Icons.Default.NoteAlt, contentDescription = "Notes", tint = Color.White)
                }
                Box {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
                    }
                    CountBadge(
                        count = unreadNotifications,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)
                    )
                }
            }

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                // ---- Stories rail ----
                item(key = "stories") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "add_story") {
                            AddStoryCircle(
                                name = currentUser?.name ?: "You",
                                photoUrl = currentUser?.photoUrl ?: "",
                                hasOwnStory = myStories.isNotEmpty(),
                                onClick = {
                                    if (myStories.isNotEmpty()) {
                                        storyToView = myStories
                                        storyStartIndex = 0
                                    } else showAddStory = true
                                },
                                onLongAdd = { showAddStory = true }
                            )
                        }
                        items(otherStories.size, key = { "sg_$it" }) { idx ->
                            val group = otherStories[idx]
                            val first = group.first()
                            val seen = group.all { it.viewers.containsKey(myUid) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                UserAvatar(
                                    name = first.authorName,
                                    photoUrl = first.authorPhoto,
                                    size = 62.dp,
                                    hasStory = true,
                                    storySeen = seen,
                                    onClick = {
                                        storyToView = group
                                        storyStartIndex = 0
                                    }
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    first.authorName.split(" ").firstOrNull() ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ---- Create post card ----
                item(key = "create") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        onClick = { showCreatePost = true }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = currentUser?.name ?: "",
                                photoUrl = currentUser?.photoUrl ?: "",
                                size = 42.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "What's on your mind?",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = LocalPalette.current.secondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---- Feed ----
                if (feedLoading) {
                    items(3, key = { "shimmer_$it" }) {
                        FeedShimmer()
                    }
                } else if (posts.isEmpty()) {
                    item(key = "empty") {
                        EmptyState("🌟", "No posts yet", "Be the first to share something awesome!")
                    }
                } else {
                    items(posts, key = { it.postId }) { post ->
                        val visibleFraction = remember(post.postId) {
                            derivedStateOf {
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == post.postId } ?: return@derivedStateOf 0f
                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                val viewportEnd = listState.layoutInfo.viewportEndOffset
                                val visible = minOf(info.offset + info.size, viewportEnd) -
                                        maxOf(info.offset, viewportStart)
                                (visible.toFloat() / info.size).coerceIn(0f, 1f)
                            }
                        }
                        PostCard(
                            post = post,
                            myUid = myUid,
                            isMostVisible = visibleFraction.value > 0.65f,
                            onLike = { feedViewModel.toggleLike(post, currentUser) },
                            onComment = {
                                commentsPost = post
                                feedViewModel.listenComments(post.postId)
                            },
                            onAuthorClick = { onOpenProfile(post.authorId) },
                            onView = { feedViewModel.incrementView(post) },
                            onEdit = { text, vis -> feedViewModel.editPost(post, text, vis) },
                            onDelete = { feedViewModel.deletePost(post) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    // ---- Create post sheet ----
    if (showCreatePost) {
        CreatePostSheet(
            uploading = uploading,
            onDismiss = { showCreatePost = false },
            onPost = { text, uri, isVideo, visibility ->
                feedViewModel.createPost(text, uri, isVideo, visibility, currentUser) { ok ->
                    if (ok) showCreatePost = false
                }
            }
        )
    }

    // ---- Add story sheet ----
    if (showAddStory) {
        AddStorySheet(
            uploading = uploading,
            onDismiss = { showAddStory = false },
            onAdd = { uri, isVideo, text ->
                feedViewModel.addStory(uri, isVideo, text, currentUser) { ok ->
                    if (ok) showAddStory = false
                }
            }
        )
    }

    // ---- Story viewer ----
    storyToView?.let { group ->
        if (group.isNotEmpty()) {
            StoryViewerDialog(
                stories = group,
                startIndex = storyStartIndex,
                myUid = myUid,
                onDismiss = { storyToView = null },
                onViewed = { feedViewModel.markStoryViewed(it) },
                onReact = { story, emoji -> feedViewModel.reactToStory(story, emoji, currentUser) },
                onDelete = { feedViewModel.deleteStory(it) }
            )
        }
    }

    // ---- Comments sheet ----
    commentsPost?.let { post ->
        CommentsSheet(
            post = post,
            feedViewModel = feedViewModel,
            currentUser = currentUser,
            onDismiss = { commentsPost = null }
        )
    }
}

@Composable
private fun AddStoryCircle(
    name: String, photoUrl: String, hasOwnStory: Boolean,
    onClick: () -> Unit, onLongAdd: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            UserAvatar(
                name = name, photoUrl = photoUrl, size = 62.dp,
                hasStory = hasOwnStory, storySeen = false, onClick = onClick
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(LocalPalette.current.primary)
                    .border(2.dp, Color(0xFF0B0B18), CircleShape)
                    .clickable { onLongAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add story", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Your story", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun FeedShimmer() {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(46.dp), CircleShape)
                Spacer(Modifier.width(10.dp))
                Column {
                    ShimmerBox(Modifier.width(140.dp).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(Modifier.width(80.dp).height(10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(20.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun PostCard(
    post: Post,
    myUid: String,
    isMostVisible: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onAuthorClick: () -> Unit,
    onView: () -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    val liked = post.likes.containsKey(myUid)
    val isMine = post.authorId == myUid

    // count a view once the card is mostly visible
    LaunchedEffect(isMostVisible) {
        if (isMostVisible) { delay(700); onView() }
    }

    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Column(Modifier.padding(bottom = 6.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = post.authorName, photoUrl = post.authorPhoto,
                    size = 44.dp, onClick = onAuthorClick
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { onAuthorClick() }) {
                    Text(post.authorName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeAgo(post.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(" · ", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Icon(
                            if (post.visibility == "public") Icons.Default.Public else Icons.Default.Lock,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        if (post.edited) {
                            Text(" · edited", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                if (isMine) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Color(0xFF1B1B32)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit post", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White) },
                            onClick = { menuOpen = false; editOpen = true }
                        )
                        DropdownMenuItem(
                            text = { Text(if (post.visibility == "public") "Make private" else "Make public", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color.White) },
                            onClick = {
                                menuOpen = false
                                onEdit(post.text, if (post.visibility == "public") "private" else "public")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete everywhere", color = UnreadRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = UnreadRed) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            // Text
            if (post.text.isNotBlank()) {
                Text(
                    post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // Media
            if (post.mediaUrl.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .heightIn(max = 420.dp)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    if (post.mediaType == "video") {
                        AutoPlayVideo(
                            url = post.mediaUrl,
                            active = isMostVisible,
                            modifier = Modifier.fillMaxWidth().height(320.dp)
                        )
                    } else {
                        AsyncImage(
                            model = post.mediaUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }

            // Stats row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (post.likeCount > 0) {
                    Text("❤️ ${post.likeCount}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.width(12.dp))
                }
                if (post.commentCount > 0) {
                    Text("${post.commentCount} comments", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Visibility, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${post.viewCount}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }

            Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 14.dp))

            // Actions
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                val likeScale by animateFloatAsState(if (liked) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "like")
                TextButton(onClick = onLike, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (liked) Color(0xFFFF3B5C) else TextSecondary,
                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Like", color = if (liked) Color(0xFFFF3B5C) else TextSecondary)
                }
                TextButton(onClick = onComment, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ModeComment, contentDescription = "Comment", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Comment", color = TextSecondary)
                }
                TextButton(onClick = onComment, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", color = TextSecondary)
                }
            }
        }
    }

    if (editOpen) {
        EditPostDialog(post = post, onDismiss = { editOpen = false }, onSave = onEdit)
    }
}

@Composable
private fun EditPostDialog(post: Post, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var text by remember { mutableStateOf(post.text) }
    var visibility by remember { mutableStateOf(post.visibility) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151528),
        title = { Text("Edit post", color = Color.White) },
        text = {
            Column {
                GlassField(value = text, onValueChange = { text = it }, label = "Post text", singleLine = false)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("🌍 Public", selected = visibility == "public") { visibility = "public" }
                    GlassChip("🔒 Private", selected = visibility == "private") { visibility = "private" }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, visibility); onDismiss() }) {
                Text("Save", color = LocalPalette.current.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostSheet(
    uploading: Boolean,
    onDismiss: () -> Unit,
    onPost: (String, Uri?, Boolean, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("public") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        mediaUri = uri; isVideo = false
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        mediaUri = uri; isVideo = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14142B),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientText("Create post", MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (uploading) PulsingDots(color = LocalPalette.current.primary)
            }
            Spacer(Modifier.height(14.dp))
            GlassField(
                value = text, onValueChange = { text = it },
                label = "What's on your mind?", singleLine = false,
                modifier = Modifier.heightIn(min = 100.dp)
            )
            Spacer(Modifier.height(12.dp))

            mediaUri?.let { uri ->
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp))) {
                    AsyncImage(
                        model = uri, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { mediaUri = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    ) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    if (isVideo) {
                        Icon(
                            Icons.Default.PlayCircle, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassChip("📷 Photo") {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                GlassChip("🎬 Video") {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("🌍 Public", selected = visibility == "public") { visibility = "public" }
                GlassChip("🔒 Only me", selected = visibility == "private") { visibility = "private" }
            }
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "Share post",
                loading = uploading,
                enabled = text.isNotBlank() || mediaUri != null,
                onClick = { onPost(text.trim(), mediaUri, isVideo, visibility) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStorySheet(
    uploading: Boolean,
    onDismiss: () -> Unit,
    onAdd: (Uri?, Boolean, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        mediaUri = uri
        isVideo = false
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        mediaUri = uri
        isVideo = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14142B),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            GradientText("Add to story", MaterialTheme.typography.titleLarge)
            Text("Stories disappear automatically after 12 hours", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            mediaUri?.let { uri ->
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))) {
                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (isVideo) Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(52.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassChip("📷 Photo") { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                GlassChip("🎬 Video") { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            }
            Spacer(Modifier.height(12.dp))
            GlassField(value = text, onValueChange = { text = it }, label = "Add a caption (optional)")
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "Share story", loading = uploading,
                enabled = mediaUri != null || text.isNotBlank(),
                onClick = { onAdd(mediaUri, isVideo, text.trim()) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Full-screen story viewer with auto-advance progress + quick reactions. */
@Composable
fun StoryViewerDialog(
    stories: List<Story>,
    startIndex: Int,
    myUid: String,
    onDismiss: () -> Unit,
    onViewed: (Story) -> Unit,
    onReact: (Story, String) -> Unit,
    onDelete: (Story) -> Unit
) {
    var index by remember { mutableStateOf(startIndex.coerceIn(0, stories.size - 1)) }
    val story = stories.getOrNull(index) ?: run { onDismiss(); return }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(index) {
        progress = 0f
        onViewed(story)
        val duration = if (story.mediaType == "video") 15000L else 5000L
        val step = 50L
        while (progress < 1f) {
            delay(step)
            progress += step.toFloat() / duration
        }
        if (index < stories.size - 1) index++ else onDismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable {
                if (index < stories.size - 1) index++ else onDismiss()
            }
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            // progress bars
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.forEachIndexed { i, _ ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        val fill = when {
                            i < index -> 1f
                            i == index -> progress
                            else -> 0f
                        }
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
                    }
                }
            }
            // header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(name = story.authorName, photoUrl = story.authorPhoto, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(story.authorName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(timeAgo(story.createdAt), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (story.authorId == myUid) {
                    Text("${story.viewers.size} views", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { onDelete(story); onDismiss() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = UnreadRed)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // content
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (story.mediaType) {
                    "video" -> AutoPlayVideo(url = story.mediaUrl, active = true, modifier = Modifier.fillMaxSize())
                    "image" -> AsyncImage(
                        model = story.mediaUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                    )
                    else -> Box(Modifier.padding(30.dp)) {
                        GradientText(story.text, MaterialTheme.typography.displaySmall)
                    }
                }
                if (story.text.isNotBlank() && story.mediaType != "text") {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(story.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // reaction bar
            Row(
                Modifier.fillMaxWidth().padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("❤️", "😂", "😮", "😢", "👍", "🔥").forEach { emoji ->
                    val mine = story.reactions[myUid] == emoji
                    Text(
                        emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (mine) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable(enabled = story.authorId != myUid) { onReact(story, emoji) }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    post: Post,
    feedViewModel: FeedViewModel,
    currentUser: User?,
    onDismiss: () -> Unit
) {
    val comments by feedViewModel.comments.collectAsState()
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14142B),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(Modifier.fillMaxHeight(0.75f).padding(18.dp).navigationBarsPadding()) {
            GradientText("Comments", MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (comments.isEmpty()) {
                    item { EmptyState("💬", "No comments yet", "Start the conversation") }
                }
                items(comments, key = { it.commentId }) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        UserAvatar(name = c.authorName, photoUrl = c.authorPhoto, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(GlassWhite)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(c.authorName, style = MaterialTheme.typography.labelLarge, color = LocalPalette.current.secondary)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(timeAgo(c.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassField(
                    value = text, onValueChange = { text = it },
                    label = "Write a comment…", modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(LocalPalette.current.bubbleMine))
                        .clickable {
                            if (text.isNotBlank()) {
                                feedViewModel.addComment(post, text.trim(), currentUser)
                                text = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
