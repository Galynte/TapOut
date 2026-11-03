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
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.galynte.tapblok.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.SortedMap
import java.util.TreeMap

class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var db: AppDatabase
    private var blockedApps: List<String> = emptyList()
    private var isBreakActive = false
    private var breakTimer: CountDownTimer? = null
    private var sessionTimer: CountDownTimer? = null

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.galynte.tapblok.ACTION_START_BREAK"
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_BREAK) {
            startBreak()
            return START_NOT_STICKY
        }

        Log.d("AppMonitoringService", "Service has started.")

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        // toggleMonitoring already published the flag and attempt count for the
        // home-screen resume. Repeat them for starts that skip that helper.
        prefs.edit {
            putBoolean("is_monitoring_active", true)
            putInt("breaks_remaining", 3)
            putInt("blocked_app_attempts", 0)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TapOut is Active")
            .setContentText("App monitoring and blocking is running.")
            .setSmallIcon(R.drawable.ic_stat_tapout)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            val localContext = this@AppMonitoringService
            blockedApps = db.blockedAppDao().getAllBlockedAppsList().map { it.packageName }
            Log.d("AppMonitoringService", "Initial loaded blocked apps from DB: $blockedApps")

            while (isActive) {
                if (!hasUsageStatsPermission(localContext) || !Settings.canDrawOverlays(localContext)) {
                    Log.e("AppMonitoringService", "Permissions revoked. Stopping service.")
                    stopSelf()
                    break
                }

                if (!isBreakActive) {
                    val foregroundApp = getForegroundApp()
                    Log.d("AppMonitoringService", "Current App: $foregroundApp")

                    if (foregroundApp != null && foregroundApp in blockedApps && foregroundApp != packageName) {
                        val blockIntent = Intent(localContext, BlockingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("BLOCKED_APP_PACKAGE_NAME", foregroundApp)
                        }
                        startActivity(blockIntent)
                        Log.d("AppMonitoringService", "Blocked app detected: $foregroundApp")

                        val currentPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        val attempts = currentPrefs.getInt("blocked_app_attempts", 0)
                        currentPrefs.edit {
                            putInt("blocked_app_attempts", attempts + 1)
                        }
                    }
                }

                delay(1000)
            }
        }

        val durationMinutes = prefs.getInt("monitoring_duration_minutes", 0)
        if (durationMinutes > 0) {
            // toggleMonitoring publishes this before returning when a duration is set.
            prefs.edit {
                putLong("session_start_timestamp", System.currentTimeMillis())
                apply()
            }

            val durationMillis = durationMinutes * 60 * 1000L
            sessionTimer = object : CountDownTimer(durationMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    Log.d("AppMonitoringService", "Session timer finished. Stopping service.")
                    stopSelf()
                }
            }.start()
        }

        return START_STICKY
    }

    private fun startBreak() {
        isBreakActive = true
        Log.d("AppMonitoringService", "Break started.")

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val breakMinutes = prefs.getInt("break_duration_minutes", 5)
        val breakMillis = breakMinutes * 60 * 1000L

        breakTimer = object : CountDownTimer(breakMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                isBreakActive = false
                Log.d("AppMonitoringService", "Break finished.")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear the active flag so UI checks (isServiceRunning) see the correct state.
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit {
            putBoolean("is_monitoring_active", false)
        }
        serviceScope.cancel()
        breakTimer?.cancel()
        sessionTimer?.cancel()
        Log.d("AppMonitoringService", "Service has been destroyed.")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun getForegroundApp(): String? {
        var currentApp: String? = null
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )

        if (appList != null && appList.isNotEmpty()) {
            val sortedMap: SortedMap<Long, String> = TreeMap()
            for (usageStats in appList) {
                sortedMap[usageStats.lastTimeUsed] = usageStats.packageName
            }
            if (sortedMap.isNotEmpty()) {
                currentApp = sortedMap[sortedMap.lastKey()]
            }
        }

        return currentApp
    }
}