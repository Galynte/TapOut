/*
 * Copyright 2025-2026 Galynte
 *
 * This file is part of TapOut, a fork of TapBlok by cajdata.
 * Original: https://github.com/cajdata/TapBlok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.galynte.tapblok

import android.app.PendingIntent
import android.content.Intent
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.galynte.tapblok.ui.theme.TapOutTheme
import java.io.IOException

class NfcWriteActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var ndefMessage: NdefMessage? = null
    private val writeOutcome = mutableStateOf<NfcWriteOutcome?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // For now, we'll hardcode the "work" profile. Later, this can be dynamic.
        val profilePayload = "work"
        ndefMessage = createNdefMessage(profilePayload)

        setContent {
            TapOutTheme {
                NfcWriteScreen(
                    outcome = writeOutcome.value,
                    onOutcomeDismissed = { outcome ->
                        // Keep a successful result until this screen is gone. Clearing it
                        // first would let a tag already in flight replace that success.
                        if (outcome.closesScreenWhenAcknowledged()) {
                            finish()
                        } else {
                            writeOutcome.value = null
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch when the activity is not in the foreground
        nfcAdapter?.disableForegroundDispatch(this)
    }

    /**
     * Foreground dispatch stays on only while another tag may still be written.
     * After a successful write the dialog stays up, so dispatch is turned off
     * and a later tag cannot replace that result.
     */
    private fun syncNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        if (!acceptsAnotherNfcTag(writeOutcome.value)) {
            adapter.disableForegroundDispatch(this)
            return
        }
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    // This method is called when an NFC tag is detected while the activity is in the foreground
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        val message = ndefMessage
        if (tag == null || message == null) return
        val attempt = attemptNfcWrite(writeOutcome.value) {
            writeNdefMessageToTag(message, tag)
        }
        // Success is already showing. Leave it there, and do not write this tag.
        if (!attempt.wroteTag) return
        val outcome = attempt.outcome ?: return
        if (outcome != NfcWriteOutcome.Success) {
            Log.w("NfcWriteActivity", "NFC tag was not written: $outcome")
        }
        writeOutcome.value = outcome
        if (!acceptsAnotherNfcTag(outcome)) {
            syncNfcForegroundDispatch()
        }
    }

    private fun createNdefMessage(payload: String): NdefMessage {
        val mimeType = "application/vnd.com.galynte.tapblok"
        val mimeRecord = NdefRecord.createMime(mimeType, payload.toByteArray(Charsets.UTF_8))
        return NdefMessage(arrayOf(mimeRecord))
    }

    private fun writeNdefMessageToTag(message: NdefMessage, tag: Tag): NfcWriteOutcome {
        val ndef = Ndef.get(tag) ?: return NfcWriteOutcome.NotNdef
        return ndef.use { tech ->
            try {
                tech.connect()
                performNfcWrite(
                    tagInfo = NfcTagInfo(isWritable = tech.isWritable, maxSize = tech.maxSize),
                    messageSize = message.toByteArray().size,
                    write = { tech.writeNdefMessage(message) },
                )
            } catch (e: IOException) {
                Log.e("NfcWriteActivity", "Error writing NFC tag", e)
                NfcWriteOutcome.Failed
            }
        }
    }
}

internal enum class NfcWriteOutcome {
    Success,
    ReadOnly,
    NotNdef,
    TooSmall,
    Failed,
}

internal data class NfcTagInfo(
    val isWritable: Boolean,
    val maxSize: Int,
)

internal data class NfcWriteAlert(
    val title: String,
    val message: String,
)

/**
 * Writes [messageSize] bytes when [tagInfo] describes a writable NDEF tag.
 * [write] is not called when the tag cannot accept the message.
 */
internal fun performNfcWrite(
    tagInfo: NfcTagInfo?,
    messageSize: Int,
    write: () -> Unit,
): NfcWriteOutcome {
    if (tagInfo == null) return NfcWriteOutcome.NotNdef
    if (!tagInfo.isWritable) return NfcWriteOutcome.ReadOnly
    if (tagInfo.maxSize < messageSize) return NfcWriteOutcome.TooSmall
    return try {
        write()
        NfcWriteOutcome.Success
    } catch (e: IOException) {
        NfcWriteOutcome.Failed
    } catch (e: FormatException) {
        NfcWriteOutcome.Failed
    }
}

/** Success leaves this screen once the user acknowledges it. Any other result stays so another tag can be tried. */
internal fun NfcWriteOutcome.closesScreenWhenAcknowledged(): Boolean = this == NfcWriteOutcome.Success

/**
 * True while a later tag may still be written.
 * Success stays on screen until the user acknowledges it. Until then this is
 * false: foreground dispatch is off, and a follow-up tag is ignored so it
 * cannot replace the success result with a failure.
 */
internal fun acceptsAnotherNfcTag(current: NfcWriteOutcome?): Boolean =
    current?.closesScreenWhenAcknowledged() != true

internal data class NfcWriteAttempt(
    val outcome: NfcWriteOutcome?,
    val wroteTag: Boolean,
)

/**
 * Writes [write] unless a successful result is already showing.
 * That result is kept, so a later failure cannot take its place.
 */
internal fun attemptNfcWrite(
    current: NfcWriteOutcome?,
    write: () -> NfcWriteOutcome,
): NfcWriteAttempt {
    if (!acceptsAnotherNfcTag(current)) {
        return NfcWriteAttempt(outcome = current, wroteTag = false)
    }
    return NfcWriteAttempt(outcome = write(), wroteTag = true)
}

internal fun NfcWriteOutcome.alert(): NfcWriteAlert = when (this) {
    NfcWriteOutcome.Success -> NfcWriteAlert(
        title = "Tag written",
        message = "This NFC tag was written successfully.",
    )
    NfcWriteOutcome.ReadOnly -> NfcWriteAlert(
        title = "Tag is read-only",
        message = "Nothing was written. This tag is read-only. A writable NFC tag must be used.",
    )
    NfcWriteOutcome.NotNdef -> NfcWriteAlert(
        title = "Tag can't be written",
        message = "Nothing was written. This tag can't accept TapOut data. A writable NFC tag must be used.",
    )
    NfcWriteOutcome.TooSmall -> NfcWriteAlert(
        title = "Tag is too small",
        message = "Nothing was written. This tag doesn't have enough space. Try a larger writable NFC tag.",
    )
    NfcWriteOutcome.Failed -> NfcWriteAlert(
        title = "Write failed",
        message = "Nothing was written. Hold a writable NFC tag to the phone and try again.",
    )
}

@Composable
internal fun NfcWriteScreen(
    outcome: NfcWriteOutcome?,
    onOutcomeDismissed: (NfcWriteOutcome) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                text = "Ready to Write\nHold tag to phone",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
    if (outcome != null) {
        val alert = outcome.alert()
        AlertDialog(
            onDismissRequest = { onOutcomeDismissed(outcome) },
            title = { Text(alert.title) },
            text = { Text(alert.message) },
            confirmButton = {
                TextButton(onClick = { onOutcomeDismissed(outcome) }) {
                    Text("OK")
                }
            },
        )
    }
}
