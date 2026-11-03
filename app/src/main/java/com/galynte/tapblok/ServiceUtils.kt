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

// Tracks the monitoring session without ActivityManager.getRunningServices().
// toggleMonitoring publishes the new value before it returns. The service writes
// the same flag again from onStartCommand and onDestroy, including starts and
// stops that do not go through toggleMonitoring, but those callbacks run only
// after startForegroundService and stopService have already returned.
fun isServiceRunning(context: Context, serviceClass: Class<out Service>): Boolean {
    // serviceClass param is unused but kept for source compatibility with existing call sites.
    return monitoringPrefs(context).getBoolean(PREF_MONITORING_ACTIVE, false)
}

enum class MonitoringToggle {
    Started,
    Stopped
}

/**
 * Starts monitoring when it is off, and stops it when it is on.
 *
 * The session flag is committed before this returns. [QrLinkActivity] opens the
 * home screen immediately, and that screen reads [isServiceRunning] on resume.
 * Waiting for [AppMonitoringService] to write the flag would show the previous
 * session, so the next scan would toggle the opposite way.
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
    monitoringPrefs(context).edit()
        .putBoolean(PREF_MONITORING_ACTIVE, outcome == MonitoringToggle.Started)
        .commit()
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