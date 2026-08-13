package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.User
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@Composable
fun ConvoContactCardDialog(user:User,baseUrl:String,onDismiss:()->Unit){
 val context=LocalContext.current;val link=baseUrl.trimEnd('/')+"/"+user.username.removePrefix("@")
 val qr=remember(link){runCatching{val m=MultiFormatWriter().encode(link,BarcodeFormat.QR_CODE,700,700);Bitmap.createBitmap(700,700,Bitmap.Config.ARGB_8888).also{b->for(x in 0 until 700)for(y in 0 until 700)b.setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)}}.getOrNull()}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Convo Contact Card",fontWeight=FontWeight.Black)},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text("${user.name} • @${user.username}");qr?.let{Image(it.asImageBitmap(),"Profile QR",Modifier.fillMaxWidth().aspectRatio(1f))};Text(link,color=MaterialTheme.colorScheme.primary)}},confirmButton={Button(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Connect with ${user.name} on Convo Chat\n$link")},"Share Convo profile"))}){Icon(Icons.Default.Share,null);Spacer(Modifier.width(6.dp));Text("Share")}},dismissButton={OutlinedButton(onClick={val c=context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager;c.setPrimaryClip(ClipData.newPlainText("Convo Link",link))}){Icon(Icons.Default.ContentCopy,null);Spacer(Modifier.width(6.dp));Text("Copy")}},shape=RoundedCornerShape(28.dp))
}
