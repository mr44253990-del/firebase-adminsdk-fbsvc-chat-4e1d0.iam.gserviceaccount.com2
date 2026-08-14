package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.User
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun QrScannerSettingsCard(users:List<User>,onProfile:(User)->Unit){
 val context=LocalContext.current
 val launcher=rememberLauncherForActivityResult(ScanContract()){result->val value=result.contents.orEmpty();if(value.isNotBlank()){val id=runCatching{Uri.parse(value).pathSegments.lastOrNull()}.getOrNull()?.removePrefix("@")?:value.removePrefix("@").trim();users.find{it.username.equals(id,true)||it.uid==id}?.let(onProfile)?:Toast.makeText(context,"No visible Convo profile found",Toast.LENGTH_LONG).show()}}
 OutlinedCard(onClick={runCatching{launcher.launch(ScanOptions().setPrompt("Scan a Convo profile QR").setBeepEnabled(false).setOrientationLocked(false))}.onFailure{Toast.makeText(context,"Scanner unavailable: ${it.localizedMessage}",Toast.LENGTH_LONG).show()}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.QrCodeScanner,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Scan Convo Contact",fontWeight=FontWeight.ExtraBold);Text("Scan a QR code to open the profile",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)}}
}
