package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.User

/** Friends the signed-in user and [person] have in common (works for friends and non-friends). */
fun mutualFriendsOf(person: User, myFriendIds: Set<String>, users: List<User>): List<User> {
    if (myFriendIds.isEmpty() || person.friends.isEmpty()) return emptyList()
    val ids = person.friends.filter { it in myFriendIds && it != person.uid }.toSet()
    if (ids.isEmpty()) return emptyList()
    val known = users.filter { it.uid in ids }.distinctBy { it.uid }
    // Keep the count right even when some mutual friends are not loaded locally.
    return known + List((ids.size - known.size).coerceAtLeast(0)) { User(uid = "unknown_$it") }
}

/** Overlapping avatars + "N mutual friends · A, B". Shows nothing when there are none. */
@Composable
fun MutualFriendsLine(person: User, myFriendIds: Set<String>, users: List<User>, modifier: Modifier = Modifier, fallback: String? = null) {
    val mutual = remember(person.uid, person.friends, myFriendIds, users.size) { mutualFriendsOf(person, myFriendIds, users) }
    if (mutual.isEmpty()) {
        if (fallback != null) Text(fallback, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, modifier = modifier)
        return
    }
    val named = mutual.filter { it.name.isNotBlank() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val shown = named.take(3)
        if (shown.isNotEmpty()) Box(Modifier.width((16 + 12 * (shown.size - 1)).dp).height(16.dp)) {
            shown.forEachIndexed { i, u ->
                Box(
                    Modifier.offset(x = (12 * i).dp).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(u.name.take(1).uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (u.profileImageUrl.isNotBlank()) AsyncImage(u.profileImageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                }
            }
        }
        if (shown.isNotEmpty()) Spacer(Modifier.width(6.dp))
        val names = named.take(2).joinToString(", ") { it.name.substringBefore(' ') }
        Text(
            "${mutual.size} mutual friend${if (mutual.size == 1) "" else "s"}" + if (names.isNotBlank()) " · $names" else "",
            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
