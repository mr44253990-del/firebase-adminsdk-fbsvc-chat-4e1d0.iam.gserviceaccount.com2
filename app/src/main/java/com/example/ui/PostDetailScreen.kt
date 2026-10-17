package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Post
import com.example.data.User

/**
 * Full post page (Facebook/Instagram style): the post with its comments open, then
 * "More from <author>" and "Related posts" that share its hashtags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(post: Post, viewModel: ChatViewModel, onBack: () -> Unit, onProfile: (User) -> Unit) {
    val allPosts by viewModel.postsState.collectAsState()
    val livePost = allPosts.firstOrNull { it.id == post.id } ?: post
    val moreFromAuthor = remember(allPosts, livePost.senderId) {
        allPosts.filter { it.senderId == livePost.senderId && it.id != livePost.id && !it.isPrivate && !it.isAdminReel }
            .sortedByDescending { it.timestamp }.take(4)
    }
    val related = remember(allPosts, livePost.tags) {
        val tags = livePost.tags.map { it.lowercase().removePrefix("#") }.toSet()
        if (tags.isEmpty()) emptyList() else allPosts.filter { other ->
            other.id != livePost.id && other.senderId != livePost.senderId && !other.isPrivate &&
                other.tags.any { it.lowercase().removePrefix("#") in tags }
        }.sortedByDescending { it.timestamp }.take(4)
    }
    Scaffold(modifier = Modifier.modernBackdrop(), containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = modernTopBarColors(),
            title = { ModernTitle(livePost.senderName.ifBlank { "Convo Post" }, if (livePost.title.isNotBlank()) livePost.title else relativeTimeLabel(livePost.timestamp)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(livePost.id) {
                SocialPostItem(livePost, viewModel, onProfileSelected = onProfile, autoPlayVideo = true, allowInlineVideo = true, startWithComments = true)
            }
            if (moreFromAuthor.isNotEmpty()) {
                item("more_header") {
                    Text("More from ${livePost.senderName.ifBlank { "this author" }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
                }
                items(moreFromAuthor, key = { "more_${it.id}" }) { other ->
                    SocialPostItem(other, viewModel, onProfileSelected = onProfile)
                }
            }
            if (related.isNotEmpty()) {
                item("related_header") {
                    Text("Related posts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
                }
                items(related, key = { "related_${it.id}" }) { other ->
                    SocialPostItem(other, viewModel, onProfileSelected = onProfile)
                }
            }
        }
    }
}
