package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer

@Composable
fun QrScannerSettingsCard(users:List<User>,onProfile:(User)->Unit){
 val context=LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
  uri?:return@rememberLauncherForActivityResult
  val value=runCatching{val bmp=context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?:error("Image unreadable");val pixels=IntArray(bmp.width*bmp.height);bmp.getPixels(pixels,0,bmp.width,0,0,bmp.width,bmp.height);MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width,bmp.height,pixels)))).text}.getOrNull()
  if(value.isNullOrBlank())Toast.makeText(context,"No QR code found in this image",Toast.LENGTH_LONG).show()else{val id=runCatching{Uri.parse(value).pathSegments.lastOrNull()}.getOrNull()?.removePrefix("@")?:value.removePrefix("@").trim();users.find{it.username.equals(id,true)||it.uid==id}?.let(onProfile)?:Toast.makeText(context,"No visible Convo profile found",Toast.LENGTH_LONG).show()}
 }
 OutlinedCard(onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.QrCodeScanner,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Open Convo QR",fontWeight=FontWeight.ExtraBold);Text("Select a QR screenshot/photo to open the profile",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)}}
}
