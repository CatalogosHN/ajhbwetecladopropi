package com.brayan.tecladoanclado

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Nuevo modelo de datos
data class ClipboardItem(val text: String, var isPinned: Boolean = false)

object DataManager {
    private const val PREFS_NAME = "PinnedKeyboardPrefsV2" // Nuevo nombre para evitar conflictos
    private const val KEY_ITEMS = "clipboard_items"

    fun saveItems(context: Context, items: List<ClipboardItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(items)
        prefs.edit().putString(KEY_ITEMS, json).apply()
    }

    fun loadItems(context: Context): MutableList<ClipboardItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ClipboardItem>>() {}.type
        return Gson().fromJson(json, type)
    }
}
