package com.marbar0407.activeapptime

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("activeapptime", Context.MODE_PRIVATE)

    var running: Boolean
        get() = sp.getBoolean("running", false)
        set(value) {
            if (running != value) sp.edit().putBoolean("running", value).apply()
        }

    var lastPackage: String?
        get() = sp.getString("last_package", null)
        set(value) {
            if (lastPackage != value) sp.edit().putString("last_package", value).apply()
        }
}
