package com.kronos.focus.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

data class Proof(val id: Long, val ts: Long, val task: String, val kind: String, val file: String)

/**
 * Registro de evidencias (SQLite local). Solo se puede AGREGAR: triggers de SQLite abortan cualquier
 * UPDATE/DELETE y cada fila encadena el hash de la anterior (si alguien altera algo, verify() lo detecta).
 */
class ProofStore private constructor(private val ctx: Context) : SQLiteOpenHelper(ctx, "proofs.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE proofs(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, day INTEGER NOT NULL, " +
                "task TEXT NOT NULL, kind TEXT NOT NULL, file TEXT NOT NULL, fileSha TEXT NOT NULL, prevHash TEXT NOT NULL, hash TEXT NOT NULL)"
        )
        db.execSQL("CREATE TRIGGER proofs_no_update BEFORE UPDATE ON proofs BEGIN SELECT RAISE(ABORT, 'registro inmutable'); END;")
        db.execSQL("CREATE TRIGGER proofs_no_delete BEFORE DELETE ON proofs BEGIN SELECT RAISE(ABORT, 'registro inmutable'); END;")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun fileSha(f: File) = sha256(f.readBytes())
    private fun chain(prev: String, ts: Long, task: String, kind: String, fileSha: String) = sha256("$prev|$ts|$task|$kind|$fileSha".toByteArray())

    @Synchronized
    fun append(task: String, kind: String, file: File): Boolean = runCatching {
        val db = writableDatabase
        val prev = db.rawQuery("SELECT hash FROM proofs ORDER BY id DESC LIMIT 1", null).use { if (it.moveToFirst()) it.getString(0) else "GENESIS" }
        val ts = System.currentTimeMillis()
        val fs = fileSha(file)
        db.insertOrThrow("proofs", null, ContentValues().apply {
            put("ts", ts); put("day", LocalDate.now().toEpochDay()); put("task", task); put("kind", kind)
            put("file", file.name); put("fileSha", fs); put("prevHash", prev); put("hash", chain(prev, ts, task, kind, fs))
        })
        true
    }.getOrDefault(false)

    fun hasProofToday(task: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM proofs WHERE day = ? AND task = ? LIMIT 1", arrayOf(LocalDate.now().toEpochDay().toString(), task)
    ).use { it.moveToFirst() }

    fun recent(limit: Int = 5): List<Proof> = readableDatabase.rawQuery(
        "SELECT id, ts, task, kind, file FROM proofs ORDER BY id DESC LIMIT $limit", null
    ).use { c -> buildList { while (c.moveToNext()) add(Proof(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4))) } }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM proofs", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Recalcula toda la cadena y comprueba que cada foto siga igual. */
    fun verify(): Boolean = runCatching {
        var prev = "GENESIS"
        readableDatabase.rawQuery("SELECT ts, task, kind, file, fileSha, prevHash, hash FROM proofs ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val (ts, task, kind, file, fs, prevHash, hash) = listOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6))
                if (prevHash != prev || chain(prev, ts.toLong(), task, kind, fs) != hash) return false
                val f = File(proofDir(ctx), file)
                if (!f.exists() || fileSha(f) != fs) return false
                prev = hash
            }
        }
        true
    }.getOrDefault(false)

    companion object {
        fun proofDir(ctx: Context) = File(ctx.filesDir, "proofs").apply { mkdirs() }
        @Volatile private var instance: ProofStore? = null
        fun get(context: Context): ProofStore = instance ?: synchronized(this) {
            instance ?: ProofStore(context.applicationContext).also { instance = it }
        }
    }
}
