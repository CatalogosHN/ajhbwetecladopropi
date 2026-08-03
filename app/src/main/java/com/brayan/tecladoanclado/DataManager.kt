package com.brayan.tecladoanclado

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ClipboardItem(val text: String, var isPinned: Boolean = false)

object DataManager {
    private const val PREFS_NAME = "PinnedKeyboardPrefsV2"
    private const val KEY_ITEMS = "clipboard_items"
    private const val KEY_SOUND = "key_sound"
    private const val KEY_VIBE = "key_vibration"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveItems(context: Context, items: List<ClipboardItem>) {
        val json = Gson().toJson(items)
        getPrefs(context).edit().putString(KEY_ITEMS, json).apply()
    }

    fun loadItems(context: Context): MutableList<ClipboardItem> {
        val json = getPrefs(context).getString(KEY_ITEMS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ClipboardItem>>() {}.type
        return Gson().fromJson(json, type)
    }

    // Configuración de Sonido (Por defecto: Activado)
    fun isSoundEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SOUND, true)
    fun setSoundEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()

    // Configuración de Vibración (Por defecto: Desactivado)
    fun isVibrationEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_VIBE, false)
    fun setVibrationEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_VIBE, enabled).apply()
}
