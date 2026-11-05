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

import android.app.AppOpsManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Process

private const val PREFS_NAME = "app_prefs"
private const val PREF_MONITORING_ACTIVE = "is_monitoring_active"
private const val PREF_BLOCKED_APP_ATTEMPTS = "blocked_app_attempts"
private const val PREF_SESSION_START_TIMESTAMP = "session_start_timestamp"
private const val PREF_MONITORING_DURATION_MINUTES = "monitoring_duration_minutes"

// Tracks the monitoring session without ActivityManager.getRunningServices().
// toggleMonitoring publishes the new session before it returns. The service writes
// the same values again from onStartCommand and onDestroy, including starts and
// stops that do not go through toggleMonitoring, but those callbacks run only
// after startForegroundService and stopService have already returned.
//
// A timed session ends at session_start_timestamp plus the configured duration.
// That check runs here so a finished countdown is already inactive before the
// home screen, an NFC tag, or a QR code reads the flag.
fun isServiceRunning(context: Context, serviceClass: Class<out Service>): Boolean {
    // serviceClass param is unused but kept for source compatibility with existing call sites.
    if (deactivateMonitoringIfExpired(context)) return false
    return monitoringPrefs(context).getBoolean(PREF_MONITORING_ACTIVE, false)
}

/**
 * Milliseconds until the active timed session ends, or null when monitoring is
 * off or has no duration. Zero means the countdown has completed.
 */
fun sessionRemainingMillis(context: Context, now: Long = System.currentTimeMillis()): Long? {
    val prefs = monitoringPrefs(context)
    return timedSessionRemainingMillis(
        active = prefs.getBoolean(PREF_MONITORING_ACTIVE, false),
        durationMinutes = prefs.getInt(PREF_MONITORING_DURATION_MINUTES, 0),
        startTimestamp = prefs.getLong(PREF_SESSION_START_TIMESTAMP, 0L),
        now = now
    )
}

/** Whole minutes still shown for a countdown. The final partial minute stays at 1. */
fun sessionMinutesRemaining(remainingMillis: Long): Int {
    if (remainingMillis <= 0L) return 0
    return ((remainingMillis + 60_000L - 1L) / 60_000L).toInt()
}

internal fun timedSessionRemainingMillis(
    active: Boolean,
    durationMinutes: Int,
    startTimestamp: Long,
    now: Long
): Long? {
    if (!active || durationMinutes <= 0 || startTimestamp <= 0L) return null
    return (startTimestamp + durationMinutes * 60_000L - now).coerceAtLeast(0L)
}

/**
 * Clears the active flag and stops the service once the countdown has completed.
 * Returns true when a session was deactivated.
 *
 * The flag is committed before this returns. The home screen reads it on resume
 * and while the countdown is on screen, and waiting for [AppMonitoringService.onDestroy]
 * leaves monitoring looking active after the timer hits zero.
 */
internal fun deactivateMonitoringIfExpired(
    context: Context,
    now: Long = System.currentTimeMillis()
): Boolean {
    val remaining = sessionRemainingMillis(context, now) ?: return false
    if (remaining > 0L) return false
    deactivateMonitoring(context)
    return true
}

internal fun deactivateMonitoring(context: Context) {
    monitoringPrefs(context).edit()
        .putBoolean(PREF_MONITORING_ACTIVE, false)
        .commit()
    context.stopService(Intent(context, AppMonitoringService::class.java))
}

enum class MonitoringToggle {
    Started,
    Stopped
}

/**
 * Starts monitoring when it is off, and stops it when it is on.
 *
 * The session is committed before this returns. [QrLinkActivity] opens the home
 * screen immediately, and that screen reads [isServiceRunning], the attempt
 * count, and the session start on resume. Compose keeps those values until the
 * next resume. Waiting for [AppMonitoringService.onStartCommand] would show the
 * previous session, and the next scan would toggle the opposite way.
 */
fun toggleMonitoring(context: Context): MonitoringToggle {
    val serviceIntent = Intent(context, AppMonitoringService::class.java)
    val outcome = if (isServiceRunning(context, AppMonitoringService::class.java)) {
        context.stopService(serviceIntent)
        MonitoringToggle.Stopped
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
        MonitoringToggle.Started
    } else {
        context.startService(serviceIntent)
        MonitoringToggle.Started
    }
    // Durable before QrLinkActivity finishes and the home screen reads it.
    val prefs = monitoringPrefs(context)
    val editor = prefs.edit()
        .putBoolean(PREF_MONITORING_ACTIVE, outcome == MonitoringToggle.Started)
    if (outcome == MonitoringToggle.Started) {
        editor.putInt(PREF_BLOCKED_APP_ATTEMPTS, 0)
        if (prefs.getInt(PREF_MONITORING_DURATION_MINUTES, 0) > 0) {
            editor.putLong(PREF_SESSION_START_TIMESTAMP, System.currentTimeMillis())
        }
    }
    editor.commit()
    return outcome
}

private fun monitoringPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}