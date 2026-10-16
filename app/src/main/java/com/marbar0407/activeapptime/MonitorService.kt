package com.marbar0407.activeapptime

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

class MonitorService : Service() {

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var prefs: Prefs
    private val state = UsageRepository.EventState()
    private val ignorePackages = mutableSetOf<String>()

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.post {
                UsageRepository.rebuildToday(this@MonitorService, state, System.currentTimeMillis())
                publish()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                UsageRepository.advance(this@MonitorService, state, System.currentTimeMillis())
                publish()
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

        handlerThread = HandlerThread("activeapptime").also { it.start() }
        handler = Handler(handlerThread.looper)

        UsageRepository.rebuildToday(this, state, System.currentTimeMillis())
        startAsForeground("…", "ActiveAppTime", "Получение данных об использовании…")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(timeReceiver, filter)
        }
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

    private fun publish() {
        val current = state.foregroundPkg()
        val shown = if (current != null && !ignorePackages.contains(current)) {
            prefs.lastPackage = current
            current
        } else {
            prefs.lastPackage
        }
        val now = System.currentTimeMillis()
        val millis = if (shown != null) UsageRepository.durationFor(state, shown, now) else 0L
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
        try {
            unregisterReceiver(timeReceiver)
        } catch (_: Exception) {
        }
        handlerThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val INTERVAL_MS = 5_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
