package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.agenda.AgendaBlock
import org.json.JSONArray
import org.json.JSONObject

/** Agenda del día confirmada en el ritual matutino. Una vez confirmada no hay API para editarla. */
class AgendaStore private constructor(private val prefs: SharedPreferences) {

    fun blocks(): List<AgendaBlock> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_BLOCKS, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AgendaBlock(o.getString("n"), o.getLong("s"), o.getLong("e"))
        }
    }.getOrDefault(emptyList())

    fun committedDay(): Long = prefs.getLong(KEY_DAY, -1L)

    fun commit(blocks: List<AgendaBlock>, epochDay: Long) {
        val arr = JSONArray()
        blocks.sortedBy { it.startMs }.forEach {
            arr.put(JSONObject().put("n", it.name).put("s", it.startMs).put("e", it.endMs))
        }
        prefs.edit().putString(KEY_BLOCKS, arr.toString()).putLong(KEY_DAY, epochDay).apply()
    }

    fun activeBlockAt(now: Long): AgendaBlock? = blocks().firstOrNull { now >= it.startMs && now < it.endMs }

    fun futureStarts(now: Long): List<Long> = blocks().map { it.startMs }.filter { it > now }

    /** Solo builds debug (UI). */
    fun clear() { prefs.edit().remove(KEY_BLOCKS).remove(KEY_DAY).apply() }

    companion object {
        private const val KEY_BLOCKS = "blocks"
        private const val KEY_DAY = "committed_day"
        @Volatile private var instance: AgendaStore? = null
        fun get(context: Context): AgendaStore = instance ?: synchronized(this) {
            instance ?: AgendaStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("agenda", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
