package com.haunted421.clipbubdeep.action

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ActionRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("clipbub_actions", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getActions(): List<Action> {
        val json = prefs.getString("actions", null) ?: return Action.DEFAULT_ACTIONS
        return try {
            val type = object : TypeToken<List<Action>>() {}.type
            gson.fromJson(json, type) ?: Action.DEFAULT_ACTIONS
        } catch (e: Exception) {
            Action.DEFAULT_ACTIONS
        }
    }

    fun saveActions(actions: List<Action>) {
        prefs.edit { putString("actions", gson.toJson(actions)) }
    }

    fun getEnabledActions(): List<Action> =
        getActions().filter { it.enabled }.sortedBy { it.order }
}
