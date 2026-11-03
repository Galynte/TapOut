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

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.galynte.tapblok.ui.components.TapOutScaffold
import com.galynte.tapblok.ui.theme.TapOutTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

/**
 * Renders the README screens from the real Compose UI. Output lands in
 * build/screenshots so the images can be copied into docs/assets/images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
class ReadmeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun privacyNotice() {
        composeRule.setContent {
            TapOutTheme {
                PrivacyScreen(onAccept = {})
            }
        }
        composeRule.onRoot().save("app_privacy_notice.png")
    }

    @Test
    fun homeMonitoringActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("blocked_app_attempts", 0)
            .putBoolean("enable_qr_code", false)
            .commit()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        ShadowSettings.setCanDrawOverlays(true)

        composeRule.setContent {
            TapOutTheme {
                MainScreen()
            }
        }
        composeRule.onRoot().save("app_active.png")
    }

    @Test
    fun settings() {
        composeRule.setContent {
            TapOutTheme {
                settingsScreen(monitoring = false, enableQrCode = true)
            }
        }
        composeRule.onNodeWithText("Display QR Code").assertIsDisplayed()
        composeRule.onRoot().save("app_settings.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h1000dp-420dpi")
    fun settingsWhileMonitoring() {
        composeRule.setContent {
            TapOutTheme {
                settingsScreen(monitoring = true, enableQrCode = true)
            }
        }
        composeRule.onNodeWithText("Stop monitoring to change QR code controls").assertIsDisplayed()
        composeRule.onNodeWithText("Stop monitoring to change session duration").assertIsDisplayed()
        composeRule.onNodeWithText("Stop monitoring to change break settings").assertIsDisplayed()
        composeRule.onNodeWithText("Display QR Code").assertIsDisplayed()
        composeRule.onNodeWithText("Open Source Licenses").assertIsDisplayed()
        composeRule.onRoot().save("app_settings_monitoring.png")
    }

    @Test
    fun displayQrCode() {
        composeRule.setContent {
            TapOutTheme {
                TapOutScaffold(
                    title = "Your QR Code",
                    onBackClick = {}
                ) { padding ->
                    QrCodeScreen(
                        modifier = Modifier.padding(padding),
                        content = QrCodeActivity.QR_CODE_CONTENT
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("QR Code").assertIsDisplayed()
        val shot = shot("app_qr_code.png")
        composeRule.onRoot().save("app_qr_code.png")
        assertSameQrSymbol(shot, printableQr())
    }

    @Composable
    private fun settingsScreen(monitoring: Boolean, enableQrCode: Boolean) {
        SettingsScreen(
            onBackClick = {},
            isMonitoringActive = monitoring,
            enableQrCode = enableQrCode,
            breakDurationMinutes = 0,
            monitoringDurationMinutes = 0,
            onEnableQrCodeChanged = {},
            onBreakOptionChanged = {},
            onMonitoringDurationChanged = {},
            onWriteNfcTagClick = {},
            onShowQrCodeClick = {},
            onEditPermissionsClick = {}
        )
    }

    @Test
    fun blockedApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notes = ApplicationInfo().apply {
            packageName = "com.example.notes"
            nonLocalizedLabel = "Notes"
        }
        val packageInfo = PackageInfo().apply {
            packageName = notes.packageName
            applicationInfo = notes
        }
        val packageManager = shadowOf(context.packageManager)
        packageManager.installPackage(packageInfo)
        packageManager.setApplicationIcon("com.example.notes", notesIcon(context))

        composeRule.setContent {
            TapOutTheme {
                BlockingScreen(
                    packageName = "com.example.notes",
                    onGoHomeClick = {},
                    onTakeBreakClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().save("app_blocked.png")
    }

    private fun shot(name: String) = java.io.File("build/screenshots", name).apply {
        parentFile?.mkdirs()
    }

    private fun printableQr(): java.io.File = listOf(
        java.io.File("docs/assets/images/tagout-toggle-qr.png"),
        java.io.File("../docs/assets/images/tagout-toggle-qr.png")
    ).firstOrNull { it.isFile } ?: error("Printable QR image is missing from the repo")

    /** Module centers of the on-screen code match the README image. */
    private fun assertSameQrSymbol(screenshot: java.io.File, printable: java.io.File) {
        val screen = javax.imageio.ImageIO.read(screenshot)
        val readme = javax.imageio.ImageIO.read(printable)
        var minX = screen.width
        var minY = screen.height
        var maxX = 0
        var maxY = 0
        for (y in 0 until screen.height) {
            for (x in 0 until screen.width) {
                val pixel = screen.getRGB(x, y) and 0xFFFFFF
                if (pixel == 0xFFFFFF || pixel == 0x000000) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        val symbol = darkBounds(readme)
        val module = finderModule(readme, symbol[0], symbol[1])
        val modules = Math.round((symbol[2] - symbol[0] + 1) / module.toFloat())
        val screenWidth = maxX - minX + 1
        val screenHeight = maxY - minY + 1
        var diffs = 0
        for (moduleY in 0 until modules) {
            for (moduleX in 0 until modules) {
                val readmeX = symbol[0] + moduleX * module + module / 2
                val readmeY = symbol[1] + moduleY * module + module / 2
                val screenX = minX + readmeX * screenWidth / readme.width
                val screenY = minY + readmeY * screenHeight / readme.height
                val readmeDark = (readme.getRGB(readmeX, readmeY) and 0xFFFFFF) == 0
                val screenDark = (screen.getRGB(screenX, screenY) and 0xFFFFFF) == 0
                if (readmeDark != screenDark) diffs++
            }
        }
        assertEquals(0, diffs)
    }

    private fun darkBounds(image: java.awt.image.BufferedImage): IntArray {
        var minX = image.width
        var minY = image.height
        var maxX = 0
        var maxY = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) and 0xFFFFFF) == 0) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return intArrayOf(minX, minY, maxX, maxY)
    }

    private fun finderModule(image: java.awt.image.BufferedImage, x: Int, y: Int): Int {
        var run = 0
        while (x + run < image.width && (image.getRGB(x + run, y) and 0xFFFFFF) == 0) {
            run++
        }
        return (run / 7).coerceAtLeast(1)
    }

    private fun notesIcon(context: Context): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3E5C73.toInt() }
        canvas.drawRoundRect(8f, 8f, 184f, 184f, 40f, 40f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(56f, 44f, 136f, 148f, 14f, 14f, paint)
        paint.color = 0xFF3E5C73.toInt()
        canvas.drawRoundRect(72f, 68f, 120f, 80f, 4f, 4f, paint)
        canvas.drawRoundRect(72f, 92f, 120f, 104f, 4f, 4f, paint)
        canvas.drawRoundRect(72f, 116f, 104f, 128f, 4f, 4f, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun SemanticsNodeInteraction.save(name: String) {
        captureRoboImage(
            shot(name),
            RoborazziOptions(taskType = RoborazziTaskType.Record)
        )
    }
}
