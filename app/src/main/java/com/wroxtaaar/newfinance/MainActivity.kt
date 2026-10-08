package com.wroxtaaar.newfinance

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wroxtaaar.newfinance.data.AppDatabase
import com.wroxtaaar.newfinance.data.TransactionEntity
import com.wroxtaaar.newfinance.sync.SmsBackfillWorker
import java.text.NumberFormat
import java.util.Locale

class MainActivity:ComponentActivity(){
 private val sms=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 override fun onCreate(state:Bundle?){super.onCreate(state);setContent{MaterialTheme{val tx by AppDatabase.get(this).transactions().all().collectAsState(initial=emptyList());Screen(tx,{sms.launch(arrayOf(Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS))},{startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))},{WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<SmsBackfillWorker>().build())})}}}
}
@Composable private fun Screen(tx:List<TransactionEntity>,allowSms:()->Unit,allowNotifications:()->Unit,rescan:()->Unit){Scaffold(topBar={TopAppBar(title={Text("Transaction Detector")})}){pad->LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Capture only",style=MaterialTheme.typography.titleLarge);Text("SMS + payment-app notifications. Unknown formats are retained locally for parser improvements.");Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=allowSms){Text("Allow SMS")};Button(onClick=allowNotifications){Text("Notification access")}};OutlinedButton(onClick=rescan){Text("Rescan SMS inbox")}};item{Text("Detected: ${tx.size}",style=MaterialTheme.typography.titleMedium)};items(tx,key={it.id}){t->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("${if(t.direction=="CREDIT")"+" else "-"}${money(t.amountMinor)}",style=MaterialTheme.typography.titleMedium);Text(t.merchant?:"Unknown merchant");Text("${t.bank?:"Unknown bank"} • ${t.direction} • ${t.confidence}% confidence");Text("Sources: ${t.sources}");Text(t.reference?.let{"Reference: $it"}?:"No reference")}}}}}}}
private fun money(minor:Long)=NumberFormat.getCurrencyInstance(Locale("en","IN")).format(minor/100.0)
