package com.brayan.tecladoanclado

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataManager {
    private const val PREFS_NAME = "PinnedKeyboardPrefs"
    private const val KEY_ITEMS = "pinned_items"

    fun saveItems(context: Context, items: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(items)
        prefs.edit().putString(KEY_ITEMS, json).apply()
    }

    fun loadItems(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<String>>() {}.type
        return Gson().fromJson(json, type)
    }
}
