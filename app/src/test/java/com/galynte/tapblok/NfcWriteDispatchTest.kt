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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer

/**
 * After a successful write the success dialog stays up, and this screen has no
 * NFC intent filter. Foreground dispatch has to stay on, or the tag just written
 * matches [NfcHandlerActivity] and toggles monitoring while it is still against
 * the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class NfcWriteDispatchTest {

    @Before
    fun enableNfcHardware() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        // The progress indicator would otherwise schedule another frame forever,
        // and Robolectric's idle never returns.
        ShadowChoreographer.setPaused(true)
    }

    @Test
    fun writtenTagResolvesToTheHandlerNotTheWriteScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            type = "application/vnd.com.galynte.tapblok"
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val names = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.name }

        assertTrue(names.contains(NfcHandlerActivity::class.java.name))
        assertFalse(names.contains(NfcWriteActivity::class.java.name))
    }

    @Test
    fun successStillCapturesTagsWhileRefusingAnotherWrite() {
        assertTrue(keepsNfcForegroundDispatch(null))
        NfcWriteOutcome.entries.forEach { outcome ->
            assertTrue(keepsNfcForegroundDispatch(outcome))
        }
        assertFalse(acceptsAnotherNfcTag(NfcWriteOutcome.Success))
        assertTrue(acceptsAnotherNfcTag(NfcWriteOutcome.Failed))
    }

    @Test
    fun successDialogKeepsForegroundDispatch() {
        Robolectric.buildActivity(NfcWriteActivity::class.java).create().use { controller ->
            val activity = controller.get()
            assertFalse(activity.isFinishing)
            writeOutcome(activity).value = NfcWriteOutcome.Success

            controller.start().resume()

            val shadow = shadowOf(NfcAdapter.getDefaultAdapter(activity))
            assertEquals(activity, shadow.enabledActivity)
            assertNull(shadow.disabledActivity)
        }
    }

    @Test
    fun successfulWriteDoesNotReleaseTheTag() {
        Robolectric.buildActivity(NfcWriteActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val shadow = shadowOf(NfcAdapter.getDefaultAdapter(activity))
            assertEquals(activity, shadow.enabledActivity)
            assertNull(shadow.disabledActivity)

            writeOutcome(activity).value = NfcWriteOutcome.Success
            syncNfcForegroundDispatch(activity)

            assertEquals(activity, shadow.enabledActivity)
            assertNull(shadow.disabledActivity)
        }
    }

    @Test
    fun leavingTheScreenReleasesForegroundDispatch() {
        Robolectric.buildActivity(NfcWriteActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val shadow = shadowOf(NfcAdapter.getDefaultAdapter(activity))
            assertEquals(activity, shadow.enabledActivity)

            controller.pause()

            assertEquals(activity, shadow.disabledActivity)
        }
    }

    private fun writeOutcome(activity: NfcWriteActivity): MutableState<NfcWriteOutcome?> {
        val field = NfcWriteActivity::class.java.getDeclaredField("writeOutcome")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(activity) as MutableState<NfcWriteOutcome?>
    }

    private fun syncNfcForegroundDispatch(activity: NfcWriteActivity) {
        val method = NfcWriteActivity::class.java.getDeclaredMethod("syncNfcForegroundDispatch")
        method.isAccessible = true
        method.invoke(activity)
    }
}
