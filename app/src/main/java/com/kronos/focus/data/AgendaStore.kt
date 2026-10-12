package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.FlexTask
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

    fun flex(): List<FlexTask> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_FLEX, "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); FlexTask(o.getString("n"), o.optBoolean("d", false)) }
    }.getOrDefault(emptyList())

    private fun saveFlex(list: List<FlexTask>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("d", it.done)) }
        prefs.edit().putString(KEY_FLEX, arr.toString()).apply()
    }

    /** Hecha = true. (Fase 4 exigirá foto de evidencia antes de llamar a esto.) */
    fun setFlexDone(name: String, done: Boolean) = saveFlex(flex().map { if (it.name == name) it.copy(done = done) else it })

    private fun writeBlocks(blocks: List<AgendaBlock>) {
        val arr = JSONArray()
        blocks.sortedBy { it.startMs }.forEach {
            arr.put(JSONObject().put("n", it.name).put("s", it.startMs).put("e", it.endMs))
        }
        prefs.edit().putString(KEY_BLOCKS, arr.toString()).apply()
    }

    fun commit(blocks: List<AgendaBlock>, flexNames: List<String>, epochDay: Long) {
        saveFlex(flexNames.distinct().map { FlexTask(it) })
        writeBlocks(blocks)
        prefs.edit().putLong(KEY_DAY, epochDay).apply()
    }

    /** Solo agrega (durante el día). No existe forma de quitar o editar bloques confirmados. */
    fun addBlock(b: AgendaBlock) = writeBlocks(blocks() + b)

    fun addFlex(name: String) = saveFlex(flex() + FlexTask(name))

    fun activeBlockAt(now: Long): AgendaBlock? = blocks().firstOrNull { now >= it.startMs && now < it.endMs }

    fun futureStarts(now: Long): List<Long> = blocks().map { it.startMs }.filter { it > now }

    /** Solo builds debug (UI). */
    fun clear() { prefs.edit().remove(KEY_BLOCKS).remove(KEY_DAY).remove(KEY_FLEX).apply() }

    companion object {
        private const val KEY_BLOCKS = "blocks"
        private const val KEY_DAY = "committed_day"
        private const val KEY_FLEX = "flex"
        @Volatile private var instance: AgendaStore? = null
        fun get(context: Context): AgendaStore = instance ?: synchronized(this) {
            instance ?: AgendaStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("agenda", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
