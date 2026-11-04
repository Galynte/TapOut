/*
 * Copyright 2026 Galynte
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

import android.nfc.FormatException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.galynte.tapblok.ui.theme.TapOutTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NfcWriteAlertTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readOnlyTagIsNotWritten() {
        var writes = 0
        val outcome = performNfcWrite(
            tagInfo = NfcTagInfo(isWritable = false, maxSize = 256),
            messageSize = 32,
            write = { writes++ },
        )
        assertEquals(NfcWriteOutcome.ReadOnly, outcome)
        assertEquals(0, writes)
        assertTrue(outcome.closesScreenWhenAcknowledged())
        assertEquals("FAILURE - Tag is read-only", outcome.alert().title)
    }

    @Test
    fun tagWithoutNdefIsNotWritten() {
        var writes = 0
        val outcome = performNfcWrite(
            tagInfo = null,
            messageSize = 32,
            write = { writes++ },
        )
        assertEquals(NfcWriteOutcome.NotNdef, outcome)
        assertEquals(0, writes)
        assertTrue(outcome.closesScreenWhenAcknowledged())
        assertEquals("FAILURE - Incorrect Tag type", outcome.alert().title)
    }

    @Test
    fun tagThatIsTooSmallIsNotWritten() {
        var writes = 0
        val outcome = performNfcWrite(
            tagInfo = NfcTagInfo(isWritable = true, maxSize = 8),
            messageSize = 32,
            write = { writes++ },
        )
        assertEquals(NfcWriteOutcome.TooSmall, outcome)
        assertEquals(0, writes)
        assertEquals("FAILURE - Incorrect Tag type", outcome.alert().title)
    }

    @Test
    fun writableTagIsWritten() {
        var writes = 0
        val outcome = performNfcWrite(
            tagInfo = NfcTagInfo(isWritable = true, maxSize = 32),
            messageSize = 32,
            write = { writes++ },
        )
        assertEquals(NfcWriteOutcome.Success, outcome)
        assertEquals(1, writes)
        assertTrue(outcome.closesScreenWhenAcknowledged())
        assertEquals("SUCCESS", outcome.alert().title)
    }

    @Test
    fun writeErrorsAreReportedAsFailure() {
        assertEquals(
            NfcWriteOutcome.Failed,
            performNfcWrite(
                tagInfo = NfcTagInfo(isWritable = true, maxSize = 64),
                messageSize = 32,
                write = { throw IOException("tag lost") },
            ),
        )
        assertEquals(
            NfcWriteOutcome.Failed,
            performNfcWrite(
                tagInfo = NfcTagInfo(isWritable = true, maxSize = 64),
                messageSize = 32,
                write = { throw FormatException("bad ndef") },
            ),
        )
        assertEquals("FAILURE - Write failed", NfcWriteOutcome.Failed.alert().title)
    }

    @Test
    fun waitingScreenHasNoAlert() {
        composeRule.setContent {
            TapOutTheme {
                NfcWriteScreen(outcome = null, onOutcomeDismissed = {})
            }
        }
        composeRule.onNodeWithText("Hold tag to phone", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("SUCCESS").assertCountEquals(0)
        composeRule.onAllNodesWithText("FAILURE - Tag is read-only").assertCountEquals(0)
    }

    @Test
    fun readOnlyAlertTellsTheUserToUseAWritableTag() {
        var dismissed: NfcWriteOutcome? = null
        composeRule.setContent {
            TapOutTheme {
                NfcWriteScreen(
                    outcome = NfcWriteOutcome.ReadOnly,
                    onOutcomeDismissed = { dismissed = it },
                )
            }
        }
        composeRule.onNodeWithText("FAILURE - Tag is read-only").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(NfcWriteOutcome.ReadOnly, dismissed)
    }

    @Test
    fun unsupportedTagAlertTellsTheUserToUseAWritableTag() {
        composeRule.setContent {
            TapOutTheme {
                NfcWriteScreen(outcome = NfcWriteOutcome.NotNdef, onOutcomeDismissed = {})
            }
        }
        composeRule.onNodeWithText("FAILURE - Incorrect Tag type").assertIsDisplayed()
    }

    @Test
    fun successfulWriteIgnoresALaterTag() {
        assertFalse(acceptsAnotherNfcTag(NfcWriteOutcome.Success))
        assertTrue(acceptsAnotherNfcTag(null))
        assertTrue(acceptsAnotherNfcTag(NfcWriteOutcome.Failed))
        assertTrue(acceptsAnotherNfcTag(NfcWriteOutcome.ReadOnly))
        assertTrue(acceptsAnotherNfcTag(NfcWriteOutcome.NotNdef))
        assertTrue(acceptsAnotherNfcTag(NfcWriteOutcome.TooSmall))
    }

    @Test
    fun followUpFailureDoesNotReplaceASuccessfulWrite() {
        var writes = 0
        val attempt = attemptNfcWrite(NfcWriteOutcome.Success) {
            writes++
            NfcWriteOutcome.Failed
        }
        assertFalse(attempt.wroteTag)
        assertEquals(0, writes)
        assertEquals(NfcWriteOutcome.Success, attempt.outcome)
        assertEquals("SUCCESS", attempt.outcome?.alert()?.title)
    }

    @Test
    fun failedWriteCanBeRetriedWithAnotherTag() {
        var writes = 0
        val attempt = attemptNfcWrite(NfcWriteOutcome.Failed) {
            writes++
            NfcWriteOutcome.Success
        }
        assertTrue(attempt.wroteTag)
        assertEquals(1, writes)
        assertEquals(NfcWriteOutcome.Success, attempt.outcome)
        assertFalse(acceptsAnotherNfcTag(attempt.outcome))
    }

    @Test
    fun successAlertConfirmsTheWrite() {
        var dismissed: NfcWriteOutcome? = null
        composeRule.setContent {
            TapOutTheme {
                NfcWriteScreen(
                    outcome = NfcWriteOutcome.Success,
                    onOutcomeDismissed = { dismissed = it },
                )
            }
        }
        composeRule.onNodeWithText("SUCCESS").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(NfcWriteOutcome.Success, dismissed)
    }
}
