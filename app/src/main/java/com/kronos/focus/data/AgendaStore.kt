package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.FlexTask
import com.kronos.focus.domain.agenda.MustRules
import com.kronos.focus.domain.agenda.MustTask
import org.json.JSONArray
import org.json.JSONObject

/** Agenda del día confirmada en el ritual matutino. Una vez confirmada no hay API para editarla. */
class AgendaStore private constructor(private val prefs: SharedPreferences) {

    @Volatile private var blocksCache: List<AgendaBlock>? = null
    @Volatile private var flexCache: List<FlexTask>? = null
    @Volatile private var mustCache: List<MustTask>? = null

    /** Tareas obligatorias (con inicio, sin fin): bloquean el teléfono desde su hora hasta tener la evidencia. */
    fun musts(): List<MustTask> = mustCache ?: readMusts().also { mustCache = it }

    private fun readMusts(): List<MustTask> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_MUST, "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); MustTask(o.getString("n"), o.getLong("s"), o.optBoolean("d", false), o.optBoolean("v", false)) }
    }.getOrDefault(emptyList())

    private fun saveMusts(list: List<MustTask>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("s", it.startMs).put("d", it.done).put("v", it.verify)) }
        mustCache = list
        prefs.edit().putString(KEY_MUST, arr.toString()).apply()
    }

    /** Solo agrega. No existe forma de quitar una tarea obligatoria: solo se completa con evidencia. */
    fun addMust(t: MustTask) = saveMusts(musts() + t)

    fun completeMust(name: String) = saveMusts(musts().map { if (!it.done && it.name == name) it.copy(done = true) else it })

    fun activeMust(now: Long): MustTask? = MustRules.activeAt(musts(), now)

    /** Se lee de las preferencias una sola vez (la UI y el servicio de bloqueo lo consultan muchas veces por segundo). */
    fun blocks(): List<AgendaBlock> = blocksCache ?: readBlocks().also { blocksCache = it }

    private fun readBlocks(): List<AgendaBlock> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_BLOCKS, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AgendaBlock(o.getString("n"), o.getLong("s"), o.getLong("e"))
        }
    }.getOrDefault(emptyList())

    fun committedDay(): Long = prefs.getLong(KEY_DAY, -1L)

    fun flex(): List<FlexTask> = flexCache ?: readFlex().also { flexCache = it }

    private fun readFlex(): List<FlexTask> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_FLEX, "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); FlexTask(o.getString("n"), o.optBoolean("d", false)) }
    }.getOrDefault(emptyList())

    private fun saveFlex(list: List<FlexTask>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("d", it.done)) }
        flexCache = list
        prefs.edit().putString(KEY_FLEX, arr.toString()).apply()
    }

    /** Hecha = true. (Fase 4 exigirá foto de evidencia antes de llamar a esto.) */
    fun setFlexDone(name: String, done: Boolean) = saveFlex(flex().map { if (it.name == name) it.copy(done = done) else it })

    private fun writeBlocks(blocks: List<AgendaBlock>) {
        val arr = JSONArray()
        blocks.sortedBy { it.startMs }.forEach {
            arr.put(JSONObject().put("n", it.name).put("s", it.startMs).put("e", it.endMs))
        }
        blocksCache = blocks.sortedBy { it.startMs }
        prefs.edit().putString(KEY_BLOCKS, arr.toString()).apply()
    }

    fun commit(blocks: List<AgendaBlock>, flexNames: List<String>, epochDay: Long) {
        saveFlex(flexNames.distinct().map { FlexTask(it) })
        writeBlocks(blocks)
        prefs.edit().putLong(KEY_DAY, epochDay).apply()
    }

    /** Solo agrega (durante el día). No existe forma de quitar o editar bloques confirmados. */
    fun addBlock(b: AgendaBlock) = writeBlocks(blocks() + b)

    /** SOLO para salir del Modo prueba: borra los bloques y tareas de hoy (las obligatorias pendientes no se tocan). */
    fun resetDayForTest() {
        writeBlocks(emptyList()); saveFlex(emptyList())
        prefs.edit().putLong(KEY_DAY, -1L).apply()
    }

    fun addFlex(name: String) = saveFlex(flex() + FlexTask(name))

    fun activeBlockAt(now: Long): AgendaBlock? = blocks().firstOrNull { now >= it.startMs && now < it.endMs }

    fun futureStarts(now: Long): List<Long> = (blocks().map { it.startMs } + musts().filter { !it.done }.map { it.startMs }).filter { it > now }

    companion object {
        private const val KEY_BLOCKS = "blocks"
        private const val KEY_DAY = "committed_day"
        private const val KEY_FLEX = "flex"
        private const val KEY_MUST = "must"
        @Volatile private var instance: AgendaStore? = null
        fun get(context: Context): AgendaStore = instance ?: synchronized(this) {
            instance ?: AgendaStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("agenda", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
