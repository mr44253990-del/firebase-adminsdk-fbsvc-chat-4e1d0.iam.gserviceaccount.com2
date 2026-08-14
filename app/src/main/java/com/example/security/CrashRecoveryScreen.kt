package com.example.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CrashRecoveryScreen(report:String,onRetry:()->Unit){
 val context=LocalContext.current
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.BugReport,null,Modifier.size(62.dp),tint=MaterialTheme.colorScheme.error);Spacer(Modifier.height(12.dp));Text("Convo recovered a crash",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("Copy this report before retrying so the exact runtime problem can be fixed.",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(12.dp));Card(shape=RoundedCornerShape(18.dp)){Text(report,Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()).padding(12.dp),style=MaterialTheme.typography.bodySmall)};Spacer(Modifier.height(12.dp));Button(onClick={val c=context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager;c.setPrimaryClip(ClipData.newPlainText("Convo crash",report))},Modifier.fillMaxWidth()){Text("Copy crash report")};OutlinedButton(onClick=onRetry,Modifier.fillMaxWidth()){Text("Clear report and try normally")}}}
}
