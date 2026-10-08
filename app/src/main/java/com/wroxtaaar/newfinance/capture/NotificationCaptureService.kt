package com.wroxtaaar.newfinance.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wroxtaaar.newfinance.data.Source
import com.wroxtaaar.newfinance.engine.TransactionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recent = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!AllowList.allowedPackage(packageName)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = buildNotificationText(
            isGmail = packageName == "com.google.android.gm",
            title = title,
            text = text,
            bigText = bigText,
            infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString)
                .orEmpty(),
            tickerText = sbn.notification.tickerText?.toString().orEmpty()
        )
        if (body.isBlank()) return

        // Gmail/Outlook can emit thousands of unrelated notifications. Keep
        // those apps allowlisted, but only hand mail to the parser when the
        // notification itself resembles a completed financial transaction.
        if (AllowList.isEmailPackage(packageName) &&
            !AllowList.looksLikeEmailTransaction(body)
        ) return

        synchronized(recent) {
            val now = System.currentTimeMillis()
            recent.entries.removeIf { now - it.value > 10 * 60_000 }
            if (recent.put(body, now) != null) return
        }

        scope.launch {
            TransactionEngine(applicationContext).ingest(
                source = Source.NOTIFICATION,
                sourceEventId = "notification:" + sbn.key + ":" + sbn.postTime,
                packageOrSender = packageName,
                body = body,
                eventTime = sbn.postTime
            )
        }
    }

    private fun buildNotificationText(
        isGmail: Boolean,
        title: String,
        text: String,
        bigText: String,
        infoText: String,
        subText: String,
        summaryText: String,
        textLines: List<String>,
        tickerText: String
    ): String {
        val values = linkedSetOf<String>()

        // Gmail's inbox-style notifications can place multiple email previews
        // in EXTRA_TEXT_LINES. Treat that field as a group summary, not as the
        // body of one email, otherwise two different bank emails can get mixed
        // into one transaction.
        listOf(title, bigText, text, infoText, subText, summaryText, tickerText)
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach(values::add)

        if (!isGmail) {
            textLines
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(values::add)
        }

        return values.joinToString("\n")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
