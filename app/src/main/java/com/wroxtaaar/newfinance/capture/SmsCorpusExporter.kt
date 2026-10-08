package com.wroxtaaar.newfinance.capture

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter

object SmsCorpusExporter {
    data class Result(val count: Int)

    fun export(resolver: ContentResolver, destination: Uri): Result {
        var count = 0
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SEEN,
                Telephony.Sms.BODY
            ),
            null,
            null,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            resolver.openOutputStream(destination)?.use { output ->
                BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                    writer.write("[\n")
                    var first = true
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                            .put("id", cursor.getString(0))
                            .put("threadId", cursor.getString(1))
                            .put("sender", cursor.getString(2))
                            .put("timestampMillis", cursor.getLong(3))
                            .put("type", cursor.getInt(4))
                            .put("read", cursor.getInt(5) != 0)
                            .put("seen", cursor.getInt(6) != 0)
                            .put("body", cursor.getString(7) ?: "")

                        if (!first) writer.write(",\n")
                        writer.write(row.toString())
                        first = false
                        count++
                    }
                    writer.write("\n]\n")
                }
            } ?: error("Unable to open export destination")
        } ?: error("Unable to read SMS database")
        return Result(count)
    }
}
