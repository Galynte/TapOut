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

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrLinkDispatchTest {

    @Test
    fun contentUsesTagoutToggleLink() {
        assertEquals("tagout://TAGOUT_TOGGLE", QrToggle.CONTENT)
        assertEquals(QrToggle.CONTENT, QrCodeActivity.QR_CODE_CONTENT)
    }

    @Test
    fun matchesLinkAndPlainTokenOnly() {
        assertTrue(QrToggle.matches("tagout://TAGOUT_TOGGLE"))
        assertTrue(QrToggle.matches("  TAGOUT_TOGGLE  "))
        assertTrue(QrToggle.matches("tagout://tagout_toggle/"))
        assertFalse(QrToggle.matches("TAPBLOK_TOGGLE"))
        assertFalse(QrToggle.matches("tagout://other"))
        assertFalse(QrToggle.matches("https://example.com/TAGOUT_TOGGLE"))
        assertFalse(QrToggle.matches(null))
    }

    @Test
    fun cameraViewIntentResolvesToQrLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(QrToggle.CONTENT))
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals(QrLinkActivity::class.java.name, resolved?.activityInfo?.name)
    }

    @Test
    fun unrelatedTagoutLinkDoesNotResolveToQrLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tagout://something-else"))
        val resolved = context.packageManager.resolveActivity(intent, 0)
        val name = resolved?.activityInfo?.name
        assertTrue(name == null || name != QrLinkActivity::class.java.name)
    }

    @Test
    fun linkOpensTapOutAndStopsMonitoringWhenEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs(context).edit()
            .putBoolean(QrToggle.PREF_ENABLED, true)
            .putBoolean("is_monitoring_active", true)
            .commit()

        val activity = launch(QrToggle.CONTENT)
        val next = shadowOf(activity).nextStartedActivity

        assertEquals(MainActivity::class.java.name, next?.component?.className)
        assertEquals("Monitoring stopped.", ShadowToast.getTextOfLatestToast())
        assertTrue(activity.isFinishing)
        assertFalse(isServiceRunning(context, AppMonitoringService::class.java))
    }

    @Test
    fun linkStartsMonitoringWhenEnabledAndIdle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs(context).edit()
            .putBoolean(QrToggle.PREF_ENABLED, true)
            .putBoolean("is_monitoring_active", false)
            .commit()

        val activity = launch(QrToggle.CONTENT)

        assertEquals(
            MainActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className
        )
        assertEquals("Monitoring started.", ShadowToast.getTextOfLatestToast())
        assertTrue(isServiceRunning(context, AppMonitoringService::class.java))
    }

    @Test
    fun secondScanFollowsTheSessionCommittedByTheFirst() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs(context).edit()
            .putBoolean(QrToggle.PREF_ENABLED, true)
            .putBoolean("is_monitoring_active", true)
            .commit()

        launch(QrToggle.CONTENT)
        assertEquals("Monitoring stopped.", ShadowToast.getTextOfLatestToast())
        assertFalse(isServiceRunning(context, AppMonitoringService::class.java))

        launch(QrToggle.CONTENT)
        assertEquals("Monitoring started.", ShadowToast.getTextOfLatestToast())
        assertTrue(isServiceRunning(context, AppMonitoringService::class.java))
    }

    @Test
    fun startPublishesFreshSessionStatsBeforeServiceCallbacks() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val previousStart = System.currentTimeMillis() - 20 * 60_000L
        prefs(base).edit()
            .putBoolean("is_monitoring_active", false)
            .putInt("blocked_app_attempts", 7)
            .putInt("monitoring_duration_minutes", 30)
            .putLong("session_start_timestamp", previousStart)
            .commit()
        val context = serviceCallbackBlocked(base)

        val before = System.currentTimeMillis()
        assertEquals(MonitoringToggle.Started, toggleMonitoring(context))
        val after = System.currentTimeMillis()

        val stored = prefs(base)
        assertTrue(stored.getBoolean("is_monitoring_active", false))
        assertTrue(context.startedService)
        assertEquals(0, stored.getInt("blocked_app_attempts", -1))
        val start = stored.getLong("session_start_timestamp", 0L)
        assertTrue(
            "session start $start should be written before the service callback, was $previousStart",
            start in before..after
        )
        val elapsedMs = after - start
        val remainingMinutes = ((30 * 60_000L - elapsedMs).coerceAtLeast(0) / 60_000L).toInt()
        assertTrue(remainingMinutes == 29 || remainingMinutes == 30)
    }

    @Test
    fun startWithoutDurationResetsAttemptsAndKeepsTimestamp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        prefs(base).edit()
            .putBoolean("is_monitoring_active", false)
            .putInt("blocked_app_attempts", 4)
            .putInt("monitoring_duration_minutes", 0)
            .putLong("session_start_timestamp", 1234L)
            .commit()

        assertEquals(MonitoringToggle.Started, toggleMonitoring(serviceCallbackBlocked(base)))

        val stored = prefs(base)
        assertEquals(0, stored.getInt("blocked_app_attempts", -1))
        assertEquals(1234L, stored.getLong("session_start_timestamp", 0L))
    }

    @Test
    fun stopLeavesAttemptCountAndSessionStartUntouched() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        prefs(base).edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("blocked_app_attempts", 5)
            .putInt("monitoring_duration_minutes", 30)
            .putLong("session_start_timestamp", 999L)
            .commit()

        assertEquals(MonitoringToggle.Stopped, toggleMonitoring(serviceCallbackBlocked(base)))

        val stored = prefs(base)
        assertFalse(stored.getBoolean("is_monitoring_active", true))
        assertEquals(5, stored.getInt("blocked_app_attempts", -1))
        assertEquals(999L, stored.getLong("session_start_timestamp", 0L))
    }

    @Test
    fun togglePublishesSessionBeforeServiceCallbacks() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        prefs(base).edit().putBoolean("is_monitoring_active", false).commit()
        val context = object : ContextWrapper(base) {
            var started = false
            var stopped = false

            override fun startForegroundService(service: Intent): ComponentName {
                started = true
                return ComponentName(base, AppMonitoringService::class.java)
            }

            override fun startService(service: Intent): ComponentName {
                started = true
                return ComponentName(base, AppMonitoringService::class.java)
            }

            override fun stopService(name: Intent): Boolean {
                stopped = true
                return true
            }
        }

        assertEquals(MonitoringToggle.Started, toggleMonitoring(context))
        assertTrue(context.started)
        assertFalse(context.stopped)
        assertTrue(isServiceRunning(base, AppMonitoringService::class.java))

        context.started = false
        assertEquals(MonitoringToggle.Stopped, toggleMonitoring(context))
        assertTrue(context.stopped)
        assertFalse(context.started)
        assertFalse(isServiceRunning(base, AppMonitoringService::class.java))
    }

    @Test
    fun linkDoesNothingUsefulWhenControlsAreOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs(context).edit()
            .putBoolean(QrToggle.PREF_ENABLED, false)
            .putBoolean("is_monitoring_active", true)
            .commit()

        val activity = launch(QrToggle.CONTENT)

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(
            "Turn on QR code controls in TapOut settings to use this code.",
            ShadowToast.getTextOfLatestToast()
        )
        assertTrue(isServiceRunning(context, AppMonitoringService::class.java))
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private fun serviceCallbackBlocked(base: Context) = object : ContextWrapper(base) {
        var startedService = false

        override fun startForegroundService(service: Intent): ComponentName {
            startedService = true
            return ComponentName(base, AppMonitoringService::class.java)
        }

        override fun startService(service: Intent): ComponentName {
            startedService = true
            return ComponentName(base, AppMonitoringService::class.java)
        }

        override fun stopService(name: Intent): Boolean {
            return true
        }
    }

    private fun launch(content: String): QrLinkActivity {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(content))
        return Robolectric.buildActivity(QrLinkActivity::class.java, intent).create().get()
    }
}
