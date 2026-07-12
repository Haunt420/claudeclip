package com.haunted421.clipbubdeep.action

import android.os.Parcelable
import com.haunted421.clipbubdeep.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class Action(
    val id: String,
    val titleResId: Int,
    val iconResId: Int,
    var enabled: Boolean = true,
    var order: Int = 0
) : Parcelable {
    companion object {
        val DEFAULT_ACTIONS = listOf(
            Action("share",   R.string.share,   R.drawable.ic_share,   enabled = true, order = 0),
            Action("copy",    R.string.copy,    R.drawable.ic_copy,    enabled = true, order = 1),
            Action("clip",    R.string.clip,    R.drawable.ic_append,  enabled = true, order = 2),
            Action("paste",   R.string.paste,   R.drawable.ic_paste,   enabled = true, order = 3),
            Action("tasker",  R.string.tasker,  R.drawable.ic_tasker,  enabled = true, order = 4),
            Action("history", R.string.history, R.drawable.ic_history, enabled = true, order = 5)
        )
    }
}
