package com.brayan.tecladoanclado

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ClipboardItem(val text: String, var isPinned: Boolean = false)
data class QuickReplyItem(var shortcut: String, var text: String)

object DataManager {
    private const val PREFS_NAME = "PinnedKeyboardPrefsV2"
    private const val KEY_ITEMS = "clipboard_items"
    private const val KEY_QUICK_REPLIES = "quick_replies"
    private const val KEY_SOUND = "key_sound"
    private const val KEY_SOUND_ENTER = "key_sound_enter"
    private const val KEY_VIBE = "key_vibration"
    private const val KEY_AUTOCORRECT = "key_autocorrect"
    private const val KEY_LEARNED_WORDS = "key_learned_words"
    private const val KEY_QR_TRIGGER = "key_qr_trigger"
    private const val KEY_RECENT_EMOJIS = "key_recent_emojis" // EL RELOJITO DE EMOJIS

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveItems(context: Context, items: List<ClipboardItem>) {
        getPrefs(context).edit().putString(KEY_ITEMS, Gson().toJson(items)).apply()
    }
    fun loadItems(context: Context): MutableList<ClipboardItem> {
        val json = getPrefs(context).getString(KEY_ITEMS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ClipboardItem>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveQuickReplies(context: Context, items: List<QuickReplyItem>) {
        getPrefs(context).edit().putString(KEY_QUICK_REPLIES, Gson().toJson(items)).apply()
    }
    fun loadQuickReplies(context: Context): MutableList<QuickReplyItem> {
        val json = getPrefs(context).getString(KEY_QUICK_REPLIES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<QuickReplyItem>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveLearnedWords(context: Context, words: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_LEARNED_WORDS, words).apply()
    }
    fun loadLearnedWords(context: Context): MutableSet<String> {
        return getPrefs(context).getStringSet(KEY_LEARNED_WORDS, null)?.toMutableSet() ?: mutableSetOf()
    }

    fun isSoundEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SOUND, true)
    fun setSoundEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()

    fun isSoundEnterEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SOUND_ENTER, true)
    fun setSoundEnterEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SOUND_ENTER, enabled).apply()

    fun isVibrationEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_VIBE, false)
    fun setVibrationEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_VIBE, enabled).apply()

    fun isAutocorrectEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_AUTOCORRECT, true)
    fun setAutocorrectEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_AUTOCORRECT, enabled).apply()

    fun getQrTrigger(context: Context): String = getPrefs(context).getString(KEY_QR_TRIGGER, "[") ?: "["
    fun setQrTrigger(context: Context, trigger: String) = getPrefs(context).edit().putString(KEY_QR_TRIGGER, trigger).apply()

    // --- MAGIA DE EMOJIS RECIENTES ---
    fun loadRecentEmojis(context: Context): MutableList<String> {
        val json = getPrefs(context).getString(KEY_RECENT_EMOJIS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<String>>() {}.type
        return Gson().fromJson(json, type)
    }
    fun addRecentEmoji(context: Context, emoji: String) {
        val recents = loadRecentEmojis(context)
        recents.remove(emoji) 
        recents.add(0, emoji)
        if (recents.size > 40) recents.removeAt(recents.size - 1) 
        getPrefs(context).edit().putString(KEY_RECENT_EMOJIS, Gson().toJson(recents)).apply()
    }
}
