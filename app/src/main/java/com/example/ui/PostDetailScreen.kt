package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.Post
import com.example.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(post: Post, viewModel: ChatViewModel, onBack: () -> Unit, onProfile: (User) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Convo Post") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp)) {
            item(post.id) { SocialPostItem(post, viewModel, onProfileSelected = onProfile, autoPlayVideo = true, allowInlineVideo = true) }
        }
    }
}
