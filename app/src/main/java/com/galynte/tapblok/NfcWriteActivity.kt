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
                    onOutcomeDismissed = { _ ->
                        // Keep the result until this screen is gone. Clearing it first
                        // would let a tag already in flight replace the alert. OK on
                        // success and on every failure returns to Settings.
                        finish()
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
        // Required once this screen is no longer resumed. The success dialog does
        // not pause the activity, so a tag still in the field stays here until then.
        nfcAdapter?.disableForegroundDispatch(this)
    }

    /**
     * Keep every discovered tag while this screen is resumed, including the
     * success dialog.
     *
     * A follow-up scan is not written after success, but it still has to be
     * captured here. This activity has no NFC intent filter. Turning dispatch
     * off would hand the tag just written to [NfcHandlerActivity], which
     * toggles monitoring. The tag is often still against the phone.
     */
    private fun syncNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        if (!keepsNfcForegroundDispatch(writeOutcome.value)) {
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
        // Success refuses another write, but the tag stays captured here.
        syncNfcForegroundDispatch()
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

/** OK on every result, success or failure, returns to Settings. */
internal fun NfcWriteOutcome.closesScreenWhenAcknowledged(): Boolean = true

/**
 * True while a later tag may still be written.
 * Success stays on screen until the user acknowledges it. Until then a
 * follow-up tag is ignored so it cannot replace the success result.
 * A failure can still be replaced by another tag before OK. OK itself
 * leaves this screen for every result.
 * The tag is still captured: [keepsNfcForegroundDispatch] stays true, so
 * the scan is not delivered to [NfcHandlerActivity].
 */
internal fun acceptsAnotherNfcTag(current: NfcWriteOutcome?): Boolean =
    current != NfcWriteOutcome.Success

/**
 * Whether this resumed screen keeps NFC tags instead of releasing them.
 *
 * Every result keeps them, including [NfcWriteOutcome.Success]. The success
 * dialog does not pause the activity. Releasing the tag there is not an
 * ignore: [NfcWriteActivity] has no NFC intent filter, so the tag just
 * written matches [NfcHandlerActivity] and toggles monitoring.
 *
 * Dispatch turns off in [NfcWriteActivity.onPause], when this screen is leaving.
 */
internal fun keepsNfcForegroundDispatch(current: NfcWriteOutcome?): Boolean =
    when (current) {
        null,
        NfcWriteOutcome.Success,
        NfcWriteOutcome.ReadOnly,
        NfcWriteOutcome.NotNdef,
        NfcWriteOutcome.TooSmall,
        NfcWriteOutcome.Failed -> true
    }

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
    NfcWriteOutcome.Success -> NfcWriteAlert("SUCCESS")
    NfcWriteOutcome.ReadOnly -> NfcWriteAlert("FAILURE - Tag is read-only")
    NfcWriteOutcome.NotNdef -> NfcWriteAlert("FAILURE - Incorrect Tag type")
    NfcWriteOutcome.TooSmall -> NfcWriteAlert("FAILURE - Tag is too small")
    NfcWriteOutcome.Failed -> NfcWriteAlert("FAILURE - Write failed")
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
            confirmButton = {
                TextButton(onClick = { onOutcomeDismissed(outcome) }) {
                    Text("OK")
                }
            },
        )
    }
}
