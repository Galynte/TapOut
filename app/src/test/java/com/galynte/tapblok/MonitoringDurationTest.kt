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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Process
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
import org.robolectric.shadows.ShadowService
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonitoringDurationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun grantPermissionsAndClearSession() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        ShadowSettings.setCanDrawOverlays(true)
        prefs().edit().clear().commit()
    }

    @Test
    fun countdownShowsTheFinalPartialMinuteAsOne() {
        assertEquals(15, sessionMinutesRemaining(15 * 60_000L))
        assertEquals(15, sessionMinutesRemaining(14 * 60_000L + 1))
        assertEquals(1, sessionMinutesRemaining(60_000L))
        assertEquals(1, sessionMinutesRemaining(1L))
        assertEquals(0, sessionMinutesRemaining(0L))
    }

    @Test
    fun finishedCountdownDeactivatesMonitoringAndKeepsSessionStats() {
        val start = 1_700_000_000_000L
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("blocked_app_attempts", 6)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", start)
            .commit()

        assertEquals(0L, sessionRemainingMillis(context, now = start + 15 * 60_000L))
        assertTrue(deactivateMonitoringIfExpired(context, now = start + 15 * 60_000L))

        assertFalse(isServiceRunning(context, AppMonitoringService::class.java))
        assertFalse(prefs().getBoolean("is_monitoring_active", true))
        assertEquals(6, prefs().getInt("blocked_app_attempts", -1))
        assertEquals(start, prefs().getLong("session_start_timestamp", 0L))
        assertNull(sessionRemainingMillis(context, now = start + 15 * 60_000L))
    }

    @Test
    fun countdownWithTimeLeftStaysActive() {
        val start = System.currentTimeMillis() - 14 * 60_000L
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", start)
            .commit()

        assertFalse(deactivateMonitoringIfExpired(context))
        assertTrue(isServiceRunning(context, AppMonitoringService::class.java))
        val remaining = sessionRemainingMillis(context)
        assertTrue(remaining != null && remaining > 0L)
    }

    @Test
    fun disabledDurationDoesNotExpire() {
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("monitoring_duration_minutes", 0)
            .putLong("session_start_timestamp", 999L)
            .commit()

        assertNull(sessionRemainingMillis(context))
        assertFalse(deactivateMonitoringIfExpired(context, now = Long.MAX_VALUE))
        assertTrue(isServiceRunning(context, AppMonitoringService::class.java))
    }

    @Test
    fun serviceStartAfterCountdownStopsWithoutRestartingTheTimer() {
        val start = System.currentTimeMillis() - 16 * 60_000L
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("blocked_app_attempts", 4)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", start)
            .commit()

        val controller = Robolectric.buildService(AppMonitoringService::class.java).create()
        try {
            // A sticky restart is delivered with a null intent. Boot and the
            // shortcut send an explicit intent through startForegroundService().
            // Either way the service has to enter the foreground before it stops,
            // or the system throws ForegroundServiceDidNotStartInTimeException.
            val service = controller.get()
            val result = service.onStartCommand(null, Service.START_FLAG_RETRY, 1)
            assertExpiredStartEnteredForegroundThenStopped(service, result)
            assertEquals(4, prefs().getInt("blocked_app_attempts", -1))
            assertEquals(start, prefs().getLong("session_start_timestamp", 0L))
        } finally {
            runCatching { controller.destroy() }
        }
    }

    @Test
    fun explicitForegroundStartAfterCountdownEntersForegroundBeforeStopping() {
        val start = System.currentTimeMillis() - 16 * 60_000L
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", start)
            .commit()

        val startIntent = Intent(context, AppMonitoringService::class.java)
        val controller = Robolectric.buildService(
            AppMonitoringService::class.java,
            startIntent
        ).create()
        try {
            val service = controller.get()
            val result = service.onStartCommand(startIntent, 0, 1)
            assertExpiredStartEnteredForegroundThenStopped(service, result)
            assertEquals(start, prefs().getLong("session_start_timestamp", 0L))
        } finally {
            runCatching { controller.destroy() }
        }
    }

    @Test
    fun breakAfterSessionIsGoneDoesNotPromoteToForeground() {
        prefs().edit()
            .putBoolean("is_monitoring_active", false)
            .putInt("blocked_app_attempts", 7)
            .putInt("breaks_remaining", 2)
            .commit()

        val breakIntent = breakIntent()
        val controller = Robolectric.buildService(
            AppMonitoringService::class.java,
            breakIntent
        ).create()
        try {
            // BlockingActivity uses startService(), then finishes. This instance
            // did not come from startForegroundService(), so it must not call
            // startForeground() — that throws ForegroundServiceStartNotAllowedException
            // once the activity is gone — and it must stop itself. Otherwise the
            // ongoing "TapOut is Active" notification stays up with no monitor loop.
            val service = controller.get()
            val result = service.onStartCommand(breakIntent, 0, 1)
            assertEquals(Service.START_NOT_STICKY, result)
            val shadow = shadowOf(service)
            assertEquals(0, shadow.lastForegroundNotificationId)
            assertFalse(shadow.isLastForegroundNotificationAttached)
            assertTrue(shadow.isStoppedBySelf)
            assertFalse(isBreakActive(service))
            assertFalse(prefs().getBoolean("is_monitoring_active", true))
            assertEquals(7, prefs().getInt("blocked_app_attempts", -1))
            assertEquals(2, prefs().getInt("breaks_remaining", -1))
        } finally {
            runCatching { controller.destroy() }
        }
    }

    @Test
    fun breakOnRunningSessionStartsBreakAndStaysForeground() {
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", System.currentTimeMillis())
            .commit()

        val controller = Robolectric.buildService(AppMonitoringService::class.java).create()
        try {
            val service = controller.get()
            val startResult = service.onStartCommand(
                Intent(context, AppMonitoringService::class.java),
                0,
                1
            )
            assertEquals(Service.START_STICKY, startResult)

            val result = service.onStartCommand(breakIntent(), 0, 2)
            assertEquals(Service.START_NOT_STICKY, result)
            val shadow = shadowOf(service)
            assertEquals(AppMonitoringService.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
            assertTrue(shadow.isLastForegroundNotificationAttached)
            assertFalse(shadow.isForegroundStopped)
            assertFalse(shadow.isStoppedBySelf)
            assertTrue(isBreakActive(service))
            assertTrue(prefs().getBoolean("is_monitoring_active", false))
        } finally {
            runCatching { controller.destroy() }
        }
    }

    @Test
    fun breakAfterMonitoringStoppedRemovesTheForegroundNotification() {
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("monitoring_duration_minutes", 0)
            .commit()

        val controller = Robolectric.buildService(AppMonitoringService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(Intent(context, AppMonitoringService::class.java), 0, 1)
            // The session already ended on this instance. The active flag is
            // clear, which is what deactivateMonitoring and onDestroy write,
            // but the service object has not been destroyed yet.
            prefs().edit().putBoolean("is_monitoring_active", false).commit()

            val result = service.onStartCommand(breakIntent(), 0, 2)
            assertEquals(Service.START_NOT_STICKY, result)
            val shadow = shadowOf(service)
            assertTrue(shadow.isForegroundStopped)
            assertTrue(shadow.notificationShouldRemoved)
            assertFalse(shadow.isLastForegroundNotificationAttached)
            assertTrue(shadow.isStoppedBySelf)
            assertFalse(isBreakActive(service))
            assertFalse(prefs().getBoolean("is_monitoring_active", true))
        } finally {
            runCatching { controller.destroy() }
        }
    }

    @Test
    fun serviceRestartKeepsTheOriginalCountdown() {
        val start = System.currentTimeMillis() - 60_000L
        prefs().edit()
            .putBoolean("is_monitoring_active", true)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", start)
            .commit()

        val controller = Robolectric.buildService(AppMonitoringService::class.java).create()
        try {
            controller.startCommand(0, 1)
            assertTrue(prefs().getBoolean("is_monitoring_active", false))
            assertEquals(start, prefs().getLong("session_start_timestamp", 0L))
        } finally {
            runCatching { controller.destroy() }
        }
    }

    @Test
    fun freshServiceStartReplacesAStaleTimestamp() {
        val before = System.currentTimeMillis()
        prefs().edit()
            .putBoolean("is_monitoring_active", false)
            .putInt("monitoring_duration_minutes", 15)
            .putLong("session_start_timestamp", before - 60 * 60_000L)
            .commit()

        val controller = Robolectric.buildService(AppMonitoringService::class.java).create()
        try {
            controller.startCommand(0, 1)
            val start = prefs().getLong("session_start_timestamp", 0L)
            assertTrue(prefs().getBoolean("is_monitoring_active", false))
            assertTrue(start >= before)
        } finally {
            runCatching { controller.destroy() }
        }
    }

    private fun assertExpiredStartEnteredForegroundThenStopped(
        service: AppMonitoringService,
        result: Int
    ) {
        assertEquals(Service.START_NOT_STICKY, result)
        val shadow: ShadowService = shadowOf(service)
        // The id is recorded by startForeground and kept after the notification
        // is removed. Zero means the expired start never entered the foreground.
        assertEquals(AppMonitoringService.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.notificationShouldRemoved)
        assertFalse(shadow.isLastForegroundNotificationAttached)
        assertTrue(shadow.isStoppedBySelf)
        assertFalse(prefs().getBoolean("is_monitoring_active", true))
    }

    private fun breakIntent(): Intent {
        return Intent(context, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.ACTION_START_BREAK
        }
    }

    private fun isBreakActive(service: AppMonitoringService): Boolean {
        val field = AppMonitoringService::class.java.getDeclaredField("isBreakActive")
        field.isAccessible = true
        return field.getBoolean(service)
    }

    private fun prefs() = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
}
