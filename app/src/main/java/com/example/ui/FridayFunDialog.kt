package com.example.ui

import android.app.DatePickerDialog
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FridayFunDialog(title: String, body: String, onDone: () -> Unit) {
    val context=LocalContext.current
    var customDob by remember { mutableStateOf("") }
    val birth=remember(customDob){runCatching{SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(customDob)}.getOrNull()}
    val now=remember{Date()}
    val days=if(birth!=null)((now.time-birth.time).coerceAtLeast(0)/86_400_000L) else 0
    val fridays=if(birth!=null){val c=Calendar.getInstance().apply{time=birth};while(c.get(Calendar.DAY_OF_WEEK)!=Calendar.FRIDAY)c.add(Calendar.DAY_OF_MONTH,1);if(c.time.after(now))0 else (((now.time-c.timeInMillis)/86_400_000L)/7+1)}else 0
    val age=if(birth!=null){val b=Calendar.getInstance().apply{time=birth};val n=Calendar.getInstance();var y=n.get(Calendar.YEAR)-b.get(Calendar.YEAR);if(n.get(Calendar.DAY_OF_YEAR)<b.get(Calendar.DAY_OF_YEAR))y--;y.coerceAtLeast(0)}else 0
    val picker=remember{Calendar.getInstance()}
    val dateDialog=DatePickerDialog(context,{_,y,m,d->customDob=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)},picker.get(Calendar.YEAR)-20,picker.get(Calendar.MONTH),picker.get(Calendar.DAY_OF_MONTH))
    dateDialog.datePicker.maxDate=System.currentTimeMillis()
    val spoken="$title. আপনার বয়স $age বছর। জন্মের পর থেকে আনুমানিক $fridays টি শুক্রবার গেছে। $body"
    var tts by remember{mutableStateOf<TextToSpeech?>(null)}
    DisposableEffect(Unit){tts=TextToSpeech(context){if(it==TextToSpeech.SUCCESS)tts?.language=Locale("bn","BD")};onDispose{tts?.shutdown()}}
    AlertDialog(onDismissRequest={},icon={Icon(Icons.Default.Cake,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)},title={Text(title,fontWeight=FontWeight.Black,textAlign=TextAlign.Center)},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("এই হিসাবের জন্য জন্মতারিখ আলাদাভাবে নির্বাচন করুন। Account DOB ব্যবহার করা হবে না।",textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(customDob,{},readOnly=true,label={Text("Custom birth date")},placeholder={Text("YYYY-MM-DD")},trailingIcon={IconButton(onClick={dateDialog.show()}){Icon(Icons.Default.CalendarMonth,"Select date")}},modifier=Modifier.fillMaxWidth())
        if(birth!=null){Text("🎂 বয়স: $age বছর");Text("📅 জীবনের দিন: $days");Text("😄 এখন পর্যন্ত শুক্রবার: $fridays",fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.primary);Text(body,textAlign=TextAlign.Center)}
    }},confirmButton={Button(onClick=onDone){Text(if(birth==null)"Skip" else "ঠিক আছে")}},dismissButton={OutlinedButton(onClick={if(birth!=null)tts?.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"friday")},enabled=birth!=null){Icon(Icons.Default.VolumeUp,null);Spacer(Modifier.width(6.dp));Text("Speak")}},shape=RoundedCornerShape(28.dp))
}
