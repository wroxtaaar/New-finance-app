package com.wroxtaaar.newfinance.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wroxtaaar.newfinance.capture.AllowList
import com.wroxtaaar.newfinance.data.Source
import com.wroxtaaar.newfinance.engine.TransactionEngine

class SmsBackfillWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result{
  if(applicationContext.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)return Result.failure()
  applicationContext.contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf(Telephony.Sms._ID,Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE),null,null,"${Telephony.Sms.DATE} DESC")?.use{c->
   val i=c.getColumnIndexOrThrow(Telephony.Sms._ID);val a=c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);val b=c.getColumnIndexOrThrow(Telephony.Sms.BODY);val d=c.getColumnIndexOrThrow(Telephony.Sms.DATE)
   while(c.moveToNext()){val sender=c.getString(a).orEmpty();if(!AllowList.allowedSender(sender))continue;TransactionEngine(applicationContext).ingest(Source.SMS,"inbox:${c.getLong(i)}",sender,c.getString(b).orEmpty(),c.getLong(d))}
  }
  return Result.success()
 }
}
