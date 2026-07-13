package com.haunted421.clipbubdeep

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
            // FloatingMenuManager owns the suppression window during drag/tap
            // interactions on the menu itself. Reading it directly here (rather
            // than duplicating a separate ignore-until field) means the two
            // states can never drift out of sync again.
            ignoreUntilProvider = { menuManager.ignoreDeselectionUntil },
            onTextSelected = { text, pkg, rect ->
                menuManager.showMenu(rect, text, pkg)
            },
            onSelectionCleared = {
                menuManager.hideMenu()
            }
        )
        Log.i(TAG, "ClipBub Deep service connected — waiting for selection events")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Restore the cheap pre-filter: only these four event types are ever
        // relevant. Everything else (scroll, click, hover, window content
        // changed, etc.) is discarded immediately instead of running the full
        // node-search pipeline on every single accessibility event the OS fires.
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                Log.d(TAG, "event received: type=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}")
                selectionDetector.handleEvent(event, rootInActiveWindow, this)
            }
            else -> return
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
        menuManager.hideMenu()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        menuManager.hideMenu()
        clipboardHistoryManager.stopListening()
        super.onDestroy()
    }
}
