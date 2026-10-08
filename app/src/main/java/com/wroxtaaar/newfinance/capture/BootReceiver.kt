package com.wroxtaaar.newfinance.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wroxtaaar.newfinance.sync.SmsBackfillWorker

class BootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action==Intent.ACTION_BOOT_COMPLETED)WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SmsBackfillWorker>().build())}}
