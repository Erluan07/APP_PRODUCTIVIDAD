package com.kronos.focus.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

data class CalEvent(val title: String, val startMs: Long, val endMs: Long, val allDay: Boolean, val calendar: String)

/** Lee los eventos de tu calendario (Samsung Calendar, Google Calendar…) del propio teléfono. Solo lectura. */
object CalendarReader {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Eventos que ocurren en el día de [dayMs] (sin los que rechazaste), ordenados por hora. */
    fun eventsForDay(ctx: Context, dayMs: Long = System.currentTimeMillis()): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val c = Calendar.getInstance().apply {
            timeInMillis = dayMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        val end = c.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        return runCatching {
            ctx.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { q ->
                buildList {
                    while (q.moveToNext()) {
                        if (q.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        add(CalEvent(q.getString(0)?.takeIf { it.isNotBlank() } ?: "(Sin título)", q.getLong(1), q.getLong(2), q.getInt(3) == 1, q.getString(4) ?: ""))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Eventos con horario que se solapan con [startMs]..[endMs]. */
    fun conflicts(events: List<CalEvent>, startMs: Long, endMs: Long): List<CalEvent> =
        events.filter { !it.allDay && startMs < it.endMs && endMs > it.startMs }
}
