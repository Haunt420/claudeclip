package com.haunted421.clipbubdeep.clipboard

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry)

    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchEntries(query: String): Flow<List<ClipboardEntry>>

    @Query("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM clipboard_entries")
    suspend fun getTotalSizeBytes(): Long

    @Query("SELECT COUNT(*) FROM clipboard_entries")
    suspend fun getCount(): Int

    @Query("DELETE FROM clipboard_entries WHERE id IN (SELECT id FROM clipboard_entries ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Delete
    suspend fun delete(entry: ClipboardEntry)

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()
}
