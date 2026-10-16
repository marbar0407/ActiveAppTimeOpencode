package com.marbar0407.activeapptime

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private val ignorePackages = mutableSetOf<String>()

    private val tick = object : Runnable {
        override fun run() {
            try {
                update()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.running = true
        ignorePackages += packageName
        ignorePackages += "com.android.systemui"
        resolveLauncherPackage()?.let { ignorePackages += it }
        startAsForeground("…", "ActiveAppTime", "Получение данных об использовании…")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun resolveLauncherPackage(): String? {
        return try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun startAsForeground(chip: String, title: String, text: String) {
        val notification = NotificationHelper.build(this, chip, title, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun update() {
        val current = UsageRepository.currentForegroundPackage(this, ignorePackages)
        if (current != null) prefs.lastPackage = current
        val shown = current ?: prefs.lastPackage
        val millis = if (shown != null) UsageRepository.todayDurationFor(this, shown) else 0L
        val time = UsageRepository.formatDuration(millis)
        val label = shown?.let { appLabel(it) } ?: "—"
        val notification = NotificationHelper.build(
            this,
            chipText = time,
            title = "Сегодня · $label",
            text = "$label · $time"
        )
        getSystemService(NotificationManager::class.java)
            .notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val INTERVAL_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
