package net.mildhope.perixd.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mildhope.perixd.R
import net.mildhope.perixd.sync.Pairing
import net.mildhope.perixd.sync.Sync
import net.mildhope.perixd.sync.SyncState
import java.time.Instant
import java.time.ZoneId

/** The "Sync between devices" settings rows. */
fun LazyListScope.syncSection(state: SyncState?, onDialog: (String) -> Unit) {
    item { SectionTitle(stringResource(R.string.section_sync)) }
    if (state == null) {
        item { ValueRow(stringResource(R.string.sync_setup), "", { onDialog("sync-setup") }, subtitle = stringResource(R.string.sync_setup_hint)) }
        item { ValueRow(stringResource(R.string.sync_join), "", { onDialog("sync-join") }, subtitle = stringResource(R.string.sync_join_hint)) }
        return
    }
    item {
        val context = LocalContext.current
        val host = runCatching { java.net.URI(state.server).host }.getOrNull() ?: state.server
        val status = when {
            state.lastError != null -> stringResource(R.string.sync_error, state.lastError)
            state.lastSync != null -> stringResource(R.string.sync_last, Labels.time(context, Instant.ofEpochMilli(state.lastSync).atZone(ZoneId.systemDefault()).toLocalTime()))
            else -> stringResource(R.string.sync_never)
        }
        ValueRow(stringResource(R.string.sync_on, host), status, { Sync.request(context) }, subtitle = stringResource(if (state.fcm != null) R.string.sync_push_on else R.string.sync_push_off))
    }
    item { ValueRow(stringResource(R.string.sync_add_device), "", { onDialog("sync-qr") }, subtitle = stringResource(R.string.sync_add_device_hint)) }
    item { ValueRow(stringResource(R.string.sync_stop), "", { onDialog("sync-stop") }) }
}

@Composable
fun SyncDialogs(dialog: String?, state: SyncState?, close: () -> Unit, joinLink: String? = null) {
    when (dialog) {
        "sync-setup" -> SetupDialog(close)
        "sync-join" -> JoinDialog(joinLink, close)
        "sync-qr" -> state?.let { QrDialog(it.pairing.link(), close) }
        "sync-stop" -> StopDialog(close)
    }
}

/** Runs a blocking sync call off the main thread, showing a spinner and a message if it fails. */
@Composable
private fun rememberWork(onDone: () -> Unit): Pair<Boolean, (suspend () -> Unit) -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.sync_failed)
    return busy to { work ->
        busy = true
        scope.launch {
            val error = runCatching { withContext(Dispatchers.IO) { work() } }.exceptionOrNull()
            busy = false
            if (error == null) onDone() else Toast.makeText(context, failed.format(error.message ?: error.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun SetupDialog(close: () -> Unit) {
    val context = LocalContext.current
    var server by rememberSaveable { mutableStateOf(Sync.DEFAULT_SERVER) }
    val (busy, work) = rememberWork(close)
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(stringResource(R.string.sync_setup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_setup_text))
                OutlinedTextField(server, { server = it }, label = { Text(stringResource(R.string.sync_server)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = { TextButton(enabled = !busy && server.startsWith("http"), onClick = { work { Sync.create(context, server) } }) { Text(stringResource(R.string.sync_start)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun JoinDialog(initial: String?, close: () -> Unit) {
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    val pairing = Pairing.parse(link)
    val (busy, work) = rememberWork(close)
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(stringResource(R.string.sync_join)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pairing != null) {
                    Text(stringResource(R.string.sync_join_confirm, runCatching { java.net.URI(pairing.server).host }.getOrNull() ?: pairing.server))
                } else {
                    Text(stringResource(R.string.sync_join_text))
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                        GmsBarcodeScanning.getClient(context, options).startScan().addOnSuccessListener { link = it.rawValue.orEmpty() }
                    }) { Text(stringResource(R.string.sync_scan)) }
                    OutlinedTextField(link, { link = it }, label = { Text(stringResource(R.string.sync_paste)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        isError = link.isNotBlank(), supportingText = { if (link.isNotBlank()) Text(stringResource(R.string.sync_bad_link)) })
                }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = { TextButton(enabled = !busy && pairing != null, onClick = { work { Sync.join(context, pairing!!) } }) { Text(stringResource(R.string.sync_join_button)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun QrDialog(link: String, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.sync_qr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(link, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(stringResource(R.string.sync_qr_text), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun StopDialog(close: () -> Unit) {
    val context = LocalContext.current
    var wipe by rememberSaveable { mutableStateOf(false) }
    val (busy, work) = rememberWork(close)
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(stringResource(R.string.sync_stop)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_stop_text))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(wipe, { wipe = it }, enabled = !busy)
                    Text(stringResource(R.string.sync_wipe))
                }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { work { Sync.leave(context, wipe) } }) {
                Text(stringResource(R.string.sync_stop_button), color = if (wipe) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Black on white whatever the theme, so every camera can read it. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }
    Canvas(modifier.background(Color.White).padding(8.dp)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
