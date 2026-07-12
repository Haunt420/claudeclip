package com.haunted421.clipbubdeep

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import com.haunted421.clipbubdeep.action.ActionHandler
import com.haunted421.clipbubdeep.action.ActionRepository
import com.haunted421.clipbubdeep.clipboard.ClipboardHistoryManager
import com.haunted421.clipbubdeep.selection.SelectionDetector
import com.haunted421.clipbubdeep.ui.FloatingMenuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class TextCommandService : AccessibilityService() {

    private lateinit var selectionDetector: SelectionDetector
    private lateinit var menuManager: FloatingMenuManager
    private lateinit var actionHandler: ActionHandler
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private lateinit var actionRepository: ActionRepository
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val ACTION_TASKER_BROADCAST = "com.haunted421.clipbubdeep.TEXT_SELECTED"
        private const val TAG = "ClipBub"
    }

    override fun onServiceConnected() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        clipboardHistoryManager = ClipboardHistoryManager.getInstance(this)
        clipboardHistoryManager.startListening()
        actionRepository = ActionRepository(this)
        actionHandler = ActionHandler(this, prefs, clipboardHistoryManager)
        menuManager = FloatingMenuManager(this, actionHandler, actionRepository, prefs)
        selectionDetector = SelectionDetector(
            onTextSelected = { text, pkg, rect ->
                menuManager.showMenu(rect, text, pkg)
            },
            onSelectionCleared = {
                menuManager.hideMenu()
            }
        )
        Log.i(TAG, "ClipBub Deep service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        selectionDetector.handleEvent(event, rootInActiveWindow, this)
    }

    override fun onInterrupt() { menuManager.hideMenu() }

    override fun onDestroy() {
        menuManager.hideMenu()
        clipboardHistoryManager.stopListening()
        super.onDestroy()
    }
}
