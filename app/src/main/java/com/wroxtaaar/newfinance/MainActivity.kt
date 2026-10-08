package com.wroxtaaar.newfinance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wroxtaaar.newfinance.capture.SmsCorpusExporter
import com.wroxtaaar.newfinance.data.AppDatabase
import com.wroxtaaar.newfinance.data.TransactionEntity
import com.wroxtaaar.newfinance.sync.SmsBackfillWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var pendingSmsExport = false
    private var exportStatus by mutableStateOf("")

    private val sms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (pendingSmsExport && result[Manifest.permission.READ_SMS] == true) {
            pendingSmsExport = false
            exportLauncher.launch("sms_corpus.json")
        } else if (pendingSmsExport) {
            pendingSmsExport = false
            exportStatus = "SMS permission is required to export the corpus."
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            exportStatus = "SMS export cancelled."
            return@registerForActivityResult
        }

        exportStatus = "Exporting SMS..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SmsCorpusExporter.export(contentResolver, uri).count
                }
            }

            result.onSuccess { count ->
                exportStatus = "Exported ${count} SMS messages."
            }.onFailure { error ->
                exportStatus = "SMS export failed: ${error.message ?: "unknown error"}"
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent {
            MaterialTheme {
                val tx by AppDatabase.get(this).transactions().all()
                    .collectAsState(initial = emptyList())

                Screen(
                    tx = tx,
                    exportStatus = exportStatus,
                    allowSms = {
                        sms.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    },
                    allowNotifications = {
                        startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    },
                    rescan = {
                        WorkManager.getInstance(this)
                            .enqueue(OneTimeWorkRequestBuilder<SmsBackfillWorker>().build())
                    },
                    exportSms = ::startSmsExport
                )
            }
        }
    }

    private fun startSmsExport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            exportLauncher.launch("sms_corpus.json")
        } else {
            pendingSmsExport = true
            sms.launch(arrayOf(Manifest.permission.READ_SMS))
        }
    }
}

@Composable
private fun Screen(
    tx: List<TransactionEntity>,
    exportStatus: String,
    allowSms: () -> Unit,
    allowNotifications: () -> Unit,
    rescan: () -> Unit,
    exportSms: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Transaction Detector") }) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Capture only", style = MaterialTheme.typography.titleLarge)
                Text(
                    "SMS + payment-app notifications. Unknown formats are retained locally " +
                        "for parser improvements."
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = allowSms) { Text("Allow SMS") }
                    Button(onClick = allowNotifications) { Text("Notification access") }
                }
                OutlinedButton(onClick = rescan) { Text("Rescan SMS inbox") }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = exportSms) {
                    Text("Export all SMS")
                }
                if (exportStatus.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(exportStatus, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "The export is raw JSON and includes all stored SMS, including non-financial messages.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                Text("Detected: ${tx.size}", style = MaterialTheme.typography.titleMedium)
            }

            items(tx, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "${if (t.direction == "CREDIT") "+" else "-"}${money(t.amountMinor)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(t.merchant ?: "Unknown merchant")
                        Text(
                            "${t.bank ?: "Unknown bank"} • ${t.direction} • " +
                                "${t.confidence}% confidence"
                        )
                        Text("Sources: ${t.sources}")
                        Text(t.reference?.let { "Reference: $it" } ?: "No reference")
                    }
                }
            }
        }
    }
}

private fun money(minor: Long) =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(minor / 100.0)
