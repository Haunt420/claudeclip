package com.haunted421.clipbubdeep.action

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.haunted421.clipbubdeep.R
import com.haunted421.clipbubdeep.TextCommandService
import com.haunted421.clipbubdeep.clipboard.ClipboardHistoryManager
import com.haunted421.clipbubdeep.clipboard.HistoryActivity

class ActionHandler(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val historyManager: ClipboardHistoryManager
) {
    private val cm get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun execute(actionId: String, text: String, sourcePackage: String, service: TextCommandService?) {
        when (actionId) {
            "share"   -> share(text)
            "copy"    -> copy(text)
            "clip"    -> { /* handled by gesture in FloatingMenuManager */ }
            "paste"   -> paste(service)
            "tasker"  -> sendToTasker(text, sourcePackage)
            "history" -> openHistory()
        }
    }

    fun share(text: String) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, null
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun copy(text: String) {
        cm.setPrimaryClip(ClipData.newPlainText("ClipBub", text))
        toast(context.getString(R.string.copied))
    }

    fun overwriteClip(text: String) {
        cm.setPrimaryClip(ClipData.newPlainText("ClipBub", text))
        toast(context.getString(R.string.clip_overwritten))
    }

    fun appendClip(text: String) {
        val existing = safeClipText() ?: run {
            toast(context.getString(R.string.clip_restricted))
            cm.setPrimaryClip(ClipData.newPlainText("ClipBub", text))
            return
        }
        cm.setPrimaryClip(
            ClipData.newPlainText("ClipBub",
                if (existing.isEmpty()) text else "$existing\n\n$text")
        )
        toast(context.getString(R.string.appended))
    }

    fun prependClip(text: String) {
        val existing = safeClipText() ?: run {
            toast(context.getString(R.string.clip_restricted))
            cm.setPrimaryClip(ClipData.newPlainText("ClipBub", text))
            return
        }
        cm.setPrimaryClip(
            ClipData.newPlainText("ClipBub",
                if (existing.isEmpty()) text else "$text\n\n$existing")
        )
        toast(context.getString(R.string.prepended))
    }

    fun paste(service: TextCommandService?) {
        val root = service?.rootInActiveWindow
        val node = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (node == null) { toast(context.getString(R.string.no_editable_field)); return }
        try {
            if (node.isEditable) node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            else toast(context.getString(R.string.no_editable_field))
        } finally { node.recycle() }
    }

    fun sendToTasker(text: String, sourcePackage: String) {
        val action = prefs.getString("tasker_custom_action", TextCommandService.ACTION_TASKER_BROADCAST)
        context.sendBroadcast(Intent(action).apply {
            putExtra("text", text)
            putExtra("source_package", sourcePackage)
            putExtra("timestamp", System.currentTimeMillis())
        })
        toast(context.getString(R.string.sent_to_tasker))
    }

    fun openHistory() {
        context.startActivity(
            Intent(context, HistoryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun searchWeb(text: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun safeClipText(): String? = try {
        cm.primaryClip?.getItemAt(0)?.text?.toString()
    } catch (e: SecurityException) {
        Log.w("ClipBub", "Clipboard read blocked: ${e.message}"); null
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
