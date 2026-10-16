package com.marbar0407.activeapptime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageRepository {

    fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns the package that is currently in the foreground, ignoring the given packages
     * (our own app, SystemUI, launcher). Looks back a short window of usage events.
     */
    fun currentForegroundPackage(context: Context, ignore: Set<String>): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 5 * 60 * 1000L
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var candidate: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!ignore.contains(event.packageName)) candidate = event.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (event.packageName == candidate) candidate = null
                }
            }
        }
        return candidate
    }

    /**
     * Total foreground time per package since local midnight, computed from raw usage events.
     * The currently resumed package is counted up to "now".
     */
    fun todayForegroundDurations(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val begin = todayStartMillis()
        val end = System.currentTimeMillis()
        val result = HashMap<String, Long>()
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var currentPkg: String? = null
        var currentStart = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (currentPkg != null && currentPkg != event.packageName) {
                        result[currentPkg] = (result[currentPkg] ?: 0L) + (event.timeStamp - currentStart)
                    }
                    if (currentPkg != event.packageName) {
                        currentPkg = event.packageName
                        currentStart = event.timeStamp
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val pkg = currentPkg
                    if (pkg != null && pkg == event.packageName) {
                        result[pkg] = (result[pkg] ?: 0L) + (event.timeStamp - currentStart)
                        currentPkg = null
                    }
                }
            }
        }
        val pkg = currentPkg
        if (pkg != null) {
            result[pkg] = (result[pkg] ?: 0L) + (end - currentStart)
        }
        return result
    }

    fun todayDurationFor(context: Context, pkg: String): Long {
        return todayForegroundDurations(context)[pkg] ?: 0L
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            millis < 60_000L -> "<1m"
            hours > 0 -> "${hours}h${minutes}m"
            else -> "${minutes}m"
        }
    }
}
