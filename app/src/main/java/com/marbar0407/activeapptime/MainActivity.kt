package com.marbar0407.activeapptime

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)

        findViewById<Button>(R.id.usageButton).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.notifButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            } else {
                openNotificationSettings()
            }
        }

        findViewById<Button>(R.id.liveButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 36) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS))
                    return@setOnClickListener
                } catch (_: Exception) {
                }
            }
            openNotificationSettings()
        }

        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            try {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }

        toggleButton.setOnClickListener {
            val prefs = Prefs(this)
            if (prefs.running) {
                MonitorService.stop(this)
                prefs.running = false
            } else {
                MonitorService.start(this)
                prefs.running = true
            }
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openNotificationSettings() {
        try {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun refresh() {
        val usage = hasUsageAccess()
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val running = Prefs(this).running

        statusText.text = buildString {
            append("Доступ к использованию: ")
            append(if (usage) "выдан ✓" else "не выдан ✗")
            append("\n")
            append("Уведомления: ")
            append(if (notif) "разрешены ✓" else "запрещены ✗")
            append("\n")
            append("Мониторинг: ")
            append(if (running) "работает ✓" else "остановлен ✗")
        }
        toggleButton.text = if (running) "Остановить мониторинг" else "Запустить мониторинг"
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val REQ_NOTIF = 100
    }
}
