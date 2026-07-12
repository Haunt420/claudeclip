package com.haunted421.clipbubdeep.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ClipboardHistoryManager private constructor(private val context: Context) {

    private val db  = ClipboardDatabase.getInstance(context)
    private val dao = db.clipboardDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val maxSizeBytes = 50L * 1024 * 1024  // 50 MB

    private val cm get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = try {
            cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        } catch (e: SecurityException) { null }
        if (!text.isNullOrBlank()) {
            scope.launch { insertAndTrim(text) }
        }
    }

    private suspend fun insertAndTrim(text: String) {
        dao.insert(ClipboardEntry(text = text))
        // Enforce 50MB cap by deleting oldest entries in batches
        var totalBytes = dao.getTotalSizeBytes()
        while (totalBytes > maxSizeBytes) {
            val count = dao.getCount()
            if (count <= 1) break
            val toDelete = maxOf(1, (count * 0.1).toInt()) // trim 10% at a time
            dao.deleteOldest(toDelete)
            totalBytes = dao.getTotalSizeBytes()
        }
    }

    fun startListening() {
        cm.addPrimaryClipChangedListener(clipListener)
        Log.i("ClipBub", "Clipboard history listener registered")
    }

    fun stopListening() {
        cm.removePrimaryClipChangedListener(clipListener)
    }

    fun getAllEntries(): Flow<List<ClipboardEntry>> = dao.getAllEntries()

    fun searchEntries(query: String): Flow<List<ClipboardEntry>> =
        if (query.isBlank()) dao.getAllEntries() else dao.searchEntries(query)

    suspend fun delete(entry: ClipboardEntry) = dao.delete(entry)

    suspend fun deleteAll() = dao.deleteAll()

    companion object {
        @Volatile private var INSTANCE: ClipboardHistoryManager? = null

        fun getInstance(context: Context): ClipboardHistoryManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipboardHistoryManager(context.applicationContext)
                    .also { INSTANCE = it }
            }
    }
}
