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
import android.os.Build
import android.os.Process

// A helper function to check if the monitoring service is currently running.
// The implementation uses a SharedPreferences flag that the service itself manages
// on start/stop (via onStartCommand / onDestroy). This avoids the deprecated
// ActivityManager.getRunningServices() API while keeping the exact same call sites
// and signature for minimal diff.
fun isServiceRunning(context: Context, serviceClass: Class<out Service>): Boolean {
    // serviceClass param is unused but kept for source compatibility with existing call sites.
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_monitoring_active", false)
}

enum class MonitoringToggle {
    Started,
    Stopped
}

/** Starts monitoring when it is off, and stops it when it is on. */
fun toggleMonitoring(context: Context): MonitoringToggle {
    val serviceIntent = Intent(context, AppMonitoringService::class.java)
    return if (isServiceRunning(context, AppMonitoringService::class.java)) {
        context.stopService(serviceIntent)
        MonitoringToggle.Stopped
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
        MonitoringToggle.Started
    } else {
        context.startService(serviceIntent)
        MonitoringToggle.Started
    }
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