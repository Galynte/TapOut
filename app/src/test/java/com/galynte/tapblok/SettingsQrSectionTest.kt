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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.galynte.tapblok.ui.theme.TapOutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsQrSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displayQrCodeStaysHiddenUntilControlsAreEnabled() {
        composeRule.setContent {
            TapOutTheme {
                settingsScreen(enableQrCode = false)
            }
        }
        composeRule.onAllNodesWithText("Display QR Code").assertCountEquals(0)
        composeRule.onNodeWithText("Enable QR Code controls").assertIsDisplayed()
        composeRule.onNodeWithText("Displays Scan QR Code button on the main screen").assertIsDisplayed()
    }

    @Test
    fun displayQrCodeIsNestedActionWhenControlsAreEnabled() {
        var clicks = 0
        composeRule.setContent {
            TapOutTheme {
                settingsScreen(
                    enableQrCode = true,
                    onShowQrCodeClick = { clicks++ }
                )
            }
        }
        composeRule.onNodeWithText("Display QR Code").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Printable code for the phone camera").assertIsDisplayed()
        assertEquals(1, clicks)
    }

    @Composable
    private fun settingsScreen(
        enableQrCode: Boolean,
        onShowQrCodeClick: () -> Unit = {}
    ) {
        SettingsScreen(
            onBackClick = {},
            isMonitoringActive = false,
            enableQrCode = enableQrCode,
            breakDurationMinutes = 0,
            monitoringDurationMinutes = 0,
            onEnableQrCodeChanged = {},
            onBreakOptionChanged = {},
            onMonitoringDurationChanged = {},
            onWriteNfcTagClick = {},
            onShowQrCodeClick = onShowQrCodeClick,
            onEditPermissionsClick = {}
        )
    }
}
