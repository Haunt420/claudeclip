package com.haunted421.clipbubdeep.clipboard

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "clipboard_entries")
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val sourcePackage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val date: Date get() = Date(timestamp)
}
