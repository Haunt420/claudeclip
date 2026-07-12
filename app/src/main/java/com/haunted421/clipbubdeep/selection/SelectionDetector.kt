package com.haunted421.clipbubdeep.selection

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.haunted421.clipbubdeep.TextCommandService

class SelectionDetector(
    private val onTextSelected: (text: String, pkg: String, rect: Rect) -> Unit,
    private val onSelectionCleared: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var ignoreUntil = 0L
    private var pendingClipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    companion object {
        private const val TAG = "ClipBub.Detector"
        private const val CLIP_TIMEOUT_MS = 2_000L

        private val EXCLUDED_PACKAGES = setOf(
            "com.haunted421.clipbubdeep",
            "com.android.systemui",
            "com.android.launcher3",
            "com.samsung.android.launcher",
            "com.sec.android.app.launcher",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.android.settings",
            "com.samsung.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.lastpass.lpandroid",
            "com.onepassword.android",
            "com.agilebits.onepassword",
            "com.dashlane",
            "com.bitwarden.mobile"
        )
    }

    fun handleEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo?, service: TextCommandService) {
        if (System.currentTimeMillis() < ignoreUntil) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg in EXCLUDED_PACKAGES) return

        // Dismiss on navigation events
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.post { onSelectionCleared() }
            return
        }

        if (root == null) return

        try {
            // Pass 1 + 2: standard node text and event text fallback
            val standardResult = resolveStandard(root, event)
            if (standardResult != null) {
                cancelClipListener(service)
                handler.post { onTextSelected(standardResult.first, pkg, standardResult.second) }
                return
            }

            // Pass 3: clipboard intercept for web content / PDF / custom renderers
            val copyNode = findCopyNode(root)
            if (copyNode != null) {
                try {
                    val rect = Rect()
                    copyNode.getBoundsInScreen(rect)
                    attemptClipIntercept(copyNode, rect, pkg, service)
                } finally {
                    if (copyNode !== root) copyNode.recycle()
                }
                return
            }
        } finally {
            root.recycle()
        }

        handler.post { onSelectionCleared() }
    }

    private fun resolveStandard(root: AccessibilityNodeInfo, event: AccessibilityEvent): Pair<String, Rect>? {
        val node = findSelectionNode(root) ?: return null
        try {
            val s = node.textSelectionStart
            val e = node.textSelectionEnd
            if (s < 0 || e <= s) return null

            val nodeText = node.text?.toString()
            if (nodeText != null && e <= nodeText.length) {
                val selected = nodeText.substring(s, e)
                if (selected.isNotEmpty()) {
                    val rect = Rect().also { node.getBoundsInScreen(it) }
                    return selected to rect
                }
            }

            val eventText = event.text?.joinToString("") { it }?.trim() ?: ""
            if (eventText.isNotEmpty()) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                return eventText to rect
            }
        } finally {
            if (node !== root) node.recycle()
        }
        return null
    }

    private fun attemptClipIntercept(
        copyNode: AccessibilityNodeInfo,
        rect: Rect,
        pkg: String,
        service: TextCommandService
    ) {
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cancelClipListener(service)

        val listener = object : ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged() {
                cm.removePrimaryClipChangedListener(this)
                pendingClipListener = null
                handler.removeCallbacksAndMessages("clip_timeout")
                val text = try { cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() } catch (e: Exception) { null }
                if (!text.isNullOrBlank()) {
                    handler.post { onTextSelected(text, pkg, rect) }
                }
            }
        }
        pendingClipListener = listener
        cm.addPrimaryClipChangedListener(listener)

        handler.postAtTime({
            if (pendingClipListener === listener) {
                cm.removePrimaryClipChangedListener(listener)
                pendingClipListener = null
            }
        }, "clip_timeout", SystemClock.uptimeMillis() + CLIP_TIMEOUT_MS)

        copyNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }

    private fun cancelClipListener(service: TextCommandService) {
        pendingClipListener?.let { l ->
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.removePrimaryClipChangedListener(l)
            pendingClipListener = null
        }
        handler.removeCallbacksAndMessages("clip_timeout")
    }

    private fun findSelectionNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.textSelectionStart >= 0 && node.textSelectionEnd > node.textSelectionStart) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findCopyNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            @Suppress("DEPRECATION")
            if (node.actions and AccessibilityNodeInfo.ACTION_COPY != 0) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }
}
