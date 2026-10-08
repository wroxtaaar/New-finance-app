package com.wroxtaaar.newfinance.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wroxtaaar.newfinance.data.Source
import com.wroxtaaar.newfinance.engine.TransactionEngine
import kotlinx.coroutines.*

class NotificationCaptureService:NotificationListenerService(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val recent=HashMap<String,Long>()
 override fun onNotificationPosted(sbn:StatusBarNotification){
  if(!AllowList.allowedPackage(sbn.packageName))return
  if(sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY!=0)return
  val e=sbn.notification.extras
  val title=e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
  val big=e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
  val text=e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
  val body=listOf(title,big.ifBlank{text}).filter{it.isNotBlank()}.joinToString("\n");if(body.isBlank())return
  synchronized(recent){val now=System.currentTimeMillis();recent.entries.removeIf{now-it.value>10*60_000};if(recent.put(body,now)!=null)return}
  scope.launch{TransactionEngine(applicationContext).ingest(Source.NOTIFICATION,"notification:${sbn.key}:${sbn.postTime}",sbn.packageName,body,sbn.postTime)}
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
