package com.wroxtaaar.newfinance.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.wroxtaaar.newfinance.data.Source
import com.wroxtaaar.newfinance.engine.TransactionEngine
import kotlinx.coroutines.*

class SmsReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
  val messages=Telephony.Sms.Intents.getMessagesFromIntent(intent)?:return
  val pending=goAsync()
  CoroutineScope(Dispatchers.IO).launch{try{messages.groupBy{it.originatingAddress.orEmpty()}.forEach{(sender,parts)->
   if(!AllowList.allowedSender(sender))return@forEach
   val body=parts.joinToString(""){it.messageBody.orEmpty()};if(body.isBlank())return@forEach
   val time=parts.minOfOrNull{it.timestampMillis}?:System.currentTimeMillis()
   TransactionEngine(context.applicationContext).ingest(Source.SMS,"live:$sender:$time:${body.hashCode()}",sender,body,time)
  }}finally{pending.finish()}}
 }
}
