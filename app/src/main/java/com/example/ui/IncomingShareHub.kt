package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.IncomingSharePayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingShareHub(payload: IncomingSharePayload,viewModel:ChatViewModel,onCreatePost:()->Unit,onDismiss:()->Unit){
 val context=LocalContext.current;val users by viewModel.usersState.collectAsState();var mode by remember{mutableStateOf("home")};var uploading by remember{mutableStateOf(false)};var progress by remember{mutableIntStateOf(0)}
 ModalBottomSheet(onDismissRequest=onDismiss,shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp)){
  Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Share,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(9.dp));Column{Text("Share with Convo",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text(payload.mimeType,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   if(payload.text.isNotBlank())Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.55f)){Text(payload.text.take(300),Modifier.padding(12.dp))}
   if(uploading){LinearProgressIndicator(progress={progress/100f},modifier=Modifier.fillMaxWidth());Text("Uploading • $progress%")}
   else if(mode=="home"){
    ShareChoice(Icons.Default.AutoStories,"Story","Photo or video story"){
      val uri=payload.uris.firstOrNull();val mime=uri?.let{context.contentResolver.getType(it)}?:payload.mimeType
      if(uri==null||(!mime.startsWith("image/")&&!mime.startsWith("video/"))){Toast.makeText(context,"Only photo/video can be a story",Toast.LENGTH_LONG).show()}else{uploading=true;viewModel.uploadUriToSupabase(uri,"shared_story_${System.currentTimeMillis()}",mime,-1,{p,_->progress=p},{url->viewModel.uploadStory(payload.text.take(300),if(mime.startsWith("image/"))url else "",if(mime.startsWith("video/"))url else ""){uploading=false;Toast.makeText(context,"Story published",Toast.LENGTH_SHORT).show();onDismiss()}},{uploading=false;Toast.makeText(context,it,Toast.LENGTH_LONG).show()})}
    }
    ShareChoice(Icons.Default.Chat,"Chat","Choose a person and send") { mode="chat" }
    ShareChoice(Icons.Default.DynamicFeed,"Post","Open Post Studio with this content") { viewModel.prepareExternalShare(payload);onDismiss();onCreatePost() }
   }else{
    TextButton(onClick={mode="home"}){Icon(Icons.Default.ArrowBack,null);Spacer(Modifier.width(5.dp));Text("Share options")}
    LazyColumn(Modifier.heightIn(max=430.dp)){items(users,key={it.uid}){person->ListItem(headlineContent={Text(person.name,fontWeight=FontWeight.Bold)},supportingContent={Text("@${person.username}")},leadingContent={AsyncImage(person.profileImageUrl.ifBlank{null},person.name,Modifier.size(44.dp).clip(CircleShape))},modifier=Modifier.clip(RoundedCornerShape(18.dp)).clickable{viewModel.sendExternalShareToUser(person,payload){_,msg->Toast.makeText(context,msg,Toast.LENGTH_LONG).show()};onDismiss()})}}
   }
  }
 }
}

@Composable private fun ShareChoice(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,body:String,onClick:()->Unit){Surface(onClick=onClick,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.58f)){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(10.dp))};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.ExtraBold);Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)}}}
