package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.sleep.SleepModel
import com.kronos.focus.domain.sleep.SleepParams
import org.json.JSONArray
import org.json.JSONObject

data class NightLog(val day: Long, val onsetMs: Long?, val wokeMs: Long, val earlyMin: Int, val smart: Boolean, val rating: Int?) {
    val sleepMin: Int? get() = onsetMs?.let { ((wokeMs - it) / 60_000L).toInt().takeIf { m -> m in 60..1000 } }
}

/** Historial de despertares, valoración matutina y parámetros personales del despertar inteligente. */
class SleepLogStore private constructor(private val prefs: SharedPreferences) {

    fun params(): SleepParams = SleepParams(prefs.getInt("window", 25), prefs.getInt("thr100", 55) / 100f)

    private fun saveParams(p: SleepParams) {
        prefs.edit().putInt("window", p.windowMin).putInt("thr100", Math.round(p.threshold * 100)).apply()
    }

    fun setOnset(ms: Long?) {
        prefs.edit().apply { if (ms == null) remove("onset") else putLong("onset", ms) }.apply()
    }

    private fun onset(): Long? = prefs.getLong("onset", -1L).takeIf { it > 0 }

    fun logs(): List<NightLog> = runCatching {
        val arr = JSONArray(prefs.getString("logs", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            NightLog(o.getLong("day"), o.optLong("onset", -1L).takeIf { v -> v > 0 }, o.getLong("woke"), o.getInt("early"), o.getBoolean("smart"),
                o.optInt("rating", 0).takeIf { v -> v > 0 })
        }
    }.getOrDefault(emptyList())

    private fun saveLogs(list: List<NightLog>) {
        val arr = JSONArray()
        list.takeLast(60).forEach {
            arr.put(JSONObject().put("day", it.day).put("onset", it.onsetMs ?: -1L).put("woke", it.wokeMs)
                .put("early", it.earlyMin).put("smart", it.smart).put("rating", it.rating ?: 0))
        }
        prefs.edit().putString("logs", arr.toString()).apply()
    }

    @Synchronized
    fun recordWake(wokeMs: Long, earlyMin: Int, smart: Boolean, day: Long) {
        saveLogs(logs() + NightLog(day, onset(), wokeMs, earlyMin, smart, null))
        setOnset(null)
        prefs.edit().putBoolean("pending", true).apply()
    }

    fun pendingRating(): Boolean = prefs.getBoolean("pending", false)

    /** Guarda la valoración (1..5) de la última noche y ajusta los parámetros personales. */
    @Synchronized
    fun rate(rating: Int) {
        val list = logs()
        if (list.isEmpty()) { prefs.edit().putBoolean("pending", false).apply(); return }
        val last = list.last()
        saveLogs(list.dropLast(1) + last.copy(rating = rating))
        saveParams(SleepModel.adjust(params(), rating, last.earlyMin))
        prefs.edit().putBoolean("pending", false).apply()
    }

    fun recent(n: Int = 7): List<NightLog> = logs().takeLast(n)
    fun avgRating(n: Int = 7): Double? = recent(n).mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()
    fun avgSleepMin(n: Int = 7): Int? = recent(n).mapNotNull { it.sleepMin }.takeIf { it.isNotEmpty() }?.average()?.toInt()

    companion object {
        @Volatile private var instance: SleepLogStore? = null
        fun get(context: Context): SleepLogStore = instance ?: synchronized(this) {
            instance ?: SleepLogStore(
                context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("sleep_log", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
