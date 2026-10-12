package com.kronos.focus.services

import android.content.Context
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.data.StatsStore
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator

/** Agregar tareas al día ya confirmado (solo se puede agregar). Devuelven un error o null. */
object AgendaActions {
    fun addBlock(ctx: Context, b: AgendaBlock): String? {
        val store = AgendaStore.get(ctx)
        AgendaValidator.validateAddition(store.blocks(), b, System.currentTimeMillis())?.let { return it }
        store.addBlock(b)
        StatsStore.get(ctx).addTasks(AlarmScheduler.epochDay())
        AlarmScheduler.scheduleBlocks(ctx)
        if (BunkerStateStore.get(ctx).isActive()) BunkerWatchdogService.start(ctx)
        return null
    }

    fun addFlex(ctx: Context, name: String): String? {
        val store = AgendaStore.get(ctx)
        if (name.isBlank()) return "Escribe el nombre."
        if (store.flex().any { it.name.equals(name.trim(), true) }) return "Ya está en tu día."
        store.addFlex(name.trim())
        StatsStore.get(ctx).addTasks(AlarmScheduler.epochDay())
        return null
    }
}
