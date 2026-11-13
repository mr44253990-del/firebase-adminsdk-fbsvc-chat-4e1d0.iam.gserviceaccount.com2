package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GroupJoinCallBanner — shown at the top of a group chat while a group call is live.
 * Any member who opens the group sees it; tapping Join connects them to the running room.
 * The host ending the call tears the whole room down (handled in GroupCallEngine), so this
 * banner disappears for everyone at once.
 */
@Composable
fun GroupJoinCallBanner(
    visible: Boolean,
    video: Boolean,
    hostName: String,
    participantCount: Int,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF16A34A).copy(alpha = .30f), Color(0xFF2EE86F).copy(alpha = .18f)), start = Offset.Zero, end = Offset(800f, 120f)))
                .border(1.dp, Color(0xFF2EE86F).copy(alpha = .45f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pulsing live dot.
            Box {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2EE86F).copy(alpha = .25f)))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5BF29A), Color(0xFF16A34A)))).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (video) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(2.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(
                    if (video) "Video call in progress" else "Audio call in progress",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1
                )
                Text(
                    "Hosted by $hostName · $participantCount joined",
                    color = Color.White.copy(alpha = .75f), fontSize = 11.sp, maxLines = 1
                )
            }
            Button(
                onClick = onJoin,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Join", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
