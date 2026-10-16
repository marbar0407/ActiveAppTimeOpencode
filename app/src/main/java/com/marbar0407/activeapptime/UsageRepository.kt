package com.marbar0407.activeapptime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * Robust "today so far" tracker built on raw UsageEvents.
 *
 * Key ideas (fixes the classic "next-day / all-night inflation" bug):
 *  - A session is tracked per (package + activity class), not per package.
 *  - A session closes on ACTIVITY_PAUSED *and* ACTIVITY_STOPPED (some devices only emit STOPPED).
 *  - All open sessions are force-closed on SCREEN_NON_INTERACTIVE / KEYGUARD_SHOWN / DEVICE_SHUTDOWN,
 *    so nothing keeps accumulating while the screen is off.
 *  - Midnight is a soft boundary: sessions that are open at 00:00 are carried into the new day
 *    with their start clamped to midnight.
 *  - Negative / duplicated / out-of-order durations are clamped away.
 */
object UsageRepository {

    private const val LOOKBACK_MS = 24L * 60 * 60 * 1000L

    class EventState {
        var dayKey: String = ""
        var cursor: Long = 0L
        var foregroundKey: String? = null
        val accumulated = HashMap<String, Long>()
        val open = HashMap<String, Long>()

        fun foregroundPkg(): String? = foregroundKey?.substringBefore('/')
    }

    fun dayKey(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }

    fun todayStart(millis: Long): Long {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun key(pkg: String, cls: String) = "$pkg/$cls"
    private fun pkgOf(key: String) = key.substringBefore('/')

    /** Full recompute for the current day. Called once on service start. */
    fun rebuildToday(context: Context, state: EventState, now: Long) {
        val today = todayStart(now)
        state.accumulated.clear()
        state.open.clear()
        state.foregroundKey = null
        state.dayKey = dayKey(now)
        state.cursor = today

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Determine what was already on screen at midnight and carry it into today.
        val seed = EventState()
        processEvents(usm.queryEvents(today - LOOKBACK_MS, today), seed)
        for (k in seed.open.keys) state.open[k] = today
        state.foregroundKey = seed.foregroundKey?.takeIf { state.open.containsKey(it) }
            ?: state.open.maxByOrNull { it.value }?.key

        processEvents(usm.queryEvents(today, now), state)
        state.cursor = now
    }

    /** Incremental update from the last processed timestamp. Handles midnight / clock changes. */
    fun advance(context: Context, state: EventState, now: Long) {
        val newDayKey = dayKey(now)
        val today = todayStart(now)

        if (state.dayKey != newDayKey) {
            val carried = HashMap<String, Long>()
            for (k in state.open.keys) carried[k] = today
            state.open.clear()
            state.open.putAll(carried)
            state.accumulated.clear()
            state.foregroundKey = state.foregroundKey?.takeIf { state.open.containsKey(it) }
                ?: state.open.maxByOrNull { it.value }?.key
            state.dayKey = newDayKey
            state.cursor = today
        }

        if (state.cursor < today) state.cursor = today
        if (state.cursor > now) state.cursor = now

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        processEvents(usm.queryEvents(state.cursor, now), state)
        state.cursor = now
    }

    fun processEvents(events: UsageEvents?, state: EventState) {
        if (events == null) return
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val ts = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = e.packageName ?: continue
                    val k = key(pkg, e.className ?: "")
                    if (!state.open.containsKey(k)) state.open[k] = ts
                    state.foregroundKey = k
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val pkg = e.packageName ?: continue
                    val k = key(pkg, e.className ?: "")
                    val start = state.open.remove(k)
                    if (start != null) addClosed(state, pkg, ts - start)
                    if (state.foregroundKey == k) {
                        state.foregroundKey = state.open.maxByOrNull { it.value }?.key
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    closeAll(state, ts)
                }
            }
        }
    }

    private fun addClosed(state: EventState, pkg: String, duration: Long) {
        if (duration <= 0L) return
        state.accumulated[pkg] = (state.accumulated[pkg] ?: 0L) + duration
    }

    private fun closeAll(state: EventState, ts: Long) {
        for ((k, start) in state.open) {
            addClosed(state, pkgOf(k), ts - start)
        }
        state.open.clear()
        state.foregroundKey = null
    }

    fun durationFor(state: EventState, pkg: String, now: Long): Long {
        var total = state.accumulated[pkg] ?: 0L
        for ((k, start) in state.open) {
            if (pkgOf(k) == pkg) total += (now - start).coerceAtLeast(0L)
        }
        return total.coerceAtLeast(0L)
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
