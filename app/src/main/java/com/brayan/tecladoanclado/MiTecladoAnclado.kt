package com.brayan.tecladoanclado

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MiTecladoAnclado : InputMethodService() {
    private lateinit var adapter: PinnedAdapter
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var speechRecognizer: SpeechRecognizer
    
    // Motores
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private var soundEnabled = true
    private var soundEnterEnabled = true
    private var vibrationEnabled = false
    
    // Estados de Mayúsculas
    private var shiftState = 0
    private var lastShiftTime = 0L
    private var isEsToEn = true 
    
    // Modo Respuestas Rápidas (Buscador)
    private var isQrMode = false
    private var qrSearchQuery = ""
    private var allQrItems = mutableListOf<QuickReplyItem>()
    private lateinit var qrAdapter: QuickReplyKeyboardAdapter
    
    // Contenedores
    private lateinit var layoutTopBar: View
    private lateinit var layoutTranslatorBar: View
    private lateinit var layoutQrSearchBar: View
    private lateinit var tvQrSearch: TextView
    private lateinit var rvQuickRepliesKeyboard: RecyclerView
    
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutNumpad: View
    private lateinit var layoutClipboard: View
    private lateinit var layoutEmojis: View // NUEVO CONTENEDOR EMOJIS

    private var btnMic1: Button? = null
    private lateinit var btnLangToggle: Button
    private lateinit var btnTranslateSend: Button

    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            playClickFeedback()
            if (isQrMode) {
                if (qrSearchQuery.isNotEmpty()) {
                    qrSearchQuery = qrSearchQuery.dropLast(1)
                    updateQrSearchUI()
                } else {
                    closeQrMode()
                }
            } else {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            deleteHandler.postDelayed(this, 50) 
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { 
        Handler(Looper.getMainLooper()).postDelayed({ checkSystemClipboard() }, 150)
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutTopBar = view.findViewById(R.id.layout_top_bar)
        layoutTranslatorBar = view.findViewById(R.id.layout_translator_bar)
        layoutQrSearchBar = view.findViewById(R.id.layout_qr_search_bar)
        tvQrSearch = view.findViewById(R.id.tvQrSearch)
        rvQuickRepliesKeyboard = view.findViewById(R.id.rv_quick_replies_keyboard)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutNumpad = view.findViewById(R.id.layout_numpad)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)
        layoutEmojis = view.findViewById(R.id.layout_emojis) // INICIANDO EMOJIS

        btnMic1 = view.findViewById(R.id.btnMic1)
        btnLangToggle = view.findViewById(R.id.btnLangToggle)
        btnTranslateSend = view.findViewById(R.id.btnTranslateSend)

        setupSpeechRecognizer()
        setupTranslator()

        val btnClipboardEnter = view.findViewById<Button>(R.id.btnClipboardEnter)
        btnClipboardEnter?.setOnClickListener {
            playEnterSound()
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }

        // Configuración Portapapeles
        val rvClipboard = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        adapter = PinnedAdapter(DataManager.loadItems(this), 
            onItemClick = { text -> currentInputConnection?.commitText(text, 1) },
            onItemLongClick = { item, position -> handleLongPressItem(item, position) }
        )
        rvClipboard.layoutManager = GridLayoutManager(this, 3) 
        rvClipboard.adapter = adapter

        // Configuración Respuestas Rápidas
        allQrItems = DataManager.loadQuickReplies(this)
        qrAdapter = QuickReplyKeyboardAdapter(allQrItems) { selectedItem ->
            playClickFeedback()
            currentInputConnection?.commitText(selectedItem.text, 1)
            closeQrMode()
        }
        rvQuickRepliesKeyboard.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvQuickRepliesKeyboard.adapter = qrAdapter

        // NUEVO: CONFIGURACIÓN EMOJIS MASIVOS
        val rvEmojis = view.findViewById<RecyclerView>(R.id.rv_emojis_keyboard)
        val emojiList = "😀,😃,😄,😁,😆,😅,😂,🤣,🥲,☺️,😊,😇,🙂,🙃,😉,😌,😍,🥰,😘,😗,😙,😚,😋,😛,😝,😜,🤪,🤨,🧐,🤓,😎,🥸,🤩,🥳,😏,😒,😞,😔,😟,😕,🙁,☹️,😣,😖,😫,😩,🥺,😢,😭,😤,😠,😡,🤬,🤯,😳,🥵,🥶,😱,😨,😰,😥,😓,🫣,🤭,🫢,🫡,🤔,🤫,🤥,😶,😐,😑,😬,🙄,😯,😦,😧,😮,😲,🥱,😴,🤤,😪,😮‍💨,😵,😵‍💫,🤐,🥴,🤢,🤮,🤧,😷,🤒,🤕,🤑,🤠,😈,👿,👹,👺,🤡,💩,👻,💀,👽,👾,🤖,🎃,🫶,🤲,👐,🙌,👏,🤝,👍,👎,👊,✊,🤛,🤜,🤞,✌️,🫰,🤟,🤘,👌,🤌,🤏,🫳,🫴,👈,👉,👆,👇,☝️,✋,🤚,🖐,🖖,👋,🤙,💪,🦾,🖕,✍️,🙏,🦶,🦵,🦿,💄,💋,👄,🦷,👅,👂,🦻,👃,👣,👁,👀,🫀,🫁,🧠,🗣,👤,👥,🫂,❤️,🧡,💛,💚,💙,💜,🖤,🤍,🤎,💔,❣️,💕,💞,💓,💗,💖,💘,💝,❤️‍🔥,❤️‍🩹,📦,🚚,🛵,🚗,🚕,🚙,🚌,🚓,🚑,🚒,🛒,🛍️,🎁,🏷️,💲,💵,💴,💶,💷,🪙,💰,💳,🧾,✅,❌,⚠️,📌,📍,📲,📱,💻,🖥️,🖨️,📸,🎥,💥,🔥,✨,🌟,💫,⭐,🔥,💯,💢,💬,👁️‍🗨️,🗯️,💭,💤".split(",")
        
        rvEmojis.layoutManager = GridLayoutManager(this, 8) // 8 emojis por fila
        rvEmojis.adapter = EmojiAdapter(emojiList) { emoji ->
            playClickFeedback()
            currentInputConnection?.commitText(emoji, 1)
        }

        setKeyListeners(view as ViewGroup)
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        soundEnabled = DataManager.isSoundEnabled(this)
        soundEnterEnabled = DataManager.isSoundEnterEnabled(this)
        vibrationEnabled = DataManager.isVibrationEnabled(this)
        allQrItems = DataManager.loadQuickReplies(this)
        
        closeQrMode()
        checkSystemClipboard()
        updateAutoCaps(info)
    }

    private fun playClickFeedback() {
        if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.8f)
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vibrator.vibrate(40)
            } catch (e: Exception) {}
        }
    }

    private fun playEnterSound() {
        if (soundEnterEnabled) {
            try {
                val mp = MediaPlayer.create(this, R.raw.sonido_enter)
                mp.setOnCompletionListener { it.release() }
                mp.start()
            } catch (e: Exception) {
                if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 0.8f)
            }
        } else if (soundEnabled) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 0.8f)
        }
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vibrator.vibrate(50)
            } catch (e: Exception) {}
        }
    }

    private fun openQrMode() {
        isQrMode = true
        qrSearchQuery = ""
        layoutTopBar.visibility = View.GONE
        layoutTranslatorBar.visibility = View.GONE
        layoutQrSearchBar.visibility = View.VISIBLE
        rvQuickRepliesKeyboard.visibility = View.VISIBLE
        switchLayout(layoutLetters)
        updateQrSearchUI()
    }

    private fun closeQrMode() {
        isQrMode = false
        layoutQrSearchBar.visibility = View.GONE
        rvQuickRepliesKeyboard.visibility = View.GONE
        layoutTopBar.visibility = View.VISIBLE
    }

    private fun updateQrSearchUI() {
        tvQrSearch.text = "🔍 Buscar: $qrSearchQuery"
        val filteredList = if (qrSearchQuery.isBlank()) {
            allQrItems
        } else {
            allQrItems.filter { isFuzzyMatch(qrSearchQuery, it.shortcut) || isFuzzyMatch(qrSearchQuery, it.text) }
        }
        qrAdapter.updateData(filteredList)
    }

    private fun isFuzzyMatch(query: String, target: String): Boolean {
        if (query.isEmpty()) return true
        var qIndex = 0
        val lowerTarget = target.lowercase()
        val lowerQuery = query.lowercase()
        for (char in lowerTarget) {
            if (char == lowerQuery[qIndex]) {
                qIndex++
                if (qIndex == lowerQuery.length) return true
            }
        }
        return false
    }

    private fun setupTranslator() {
        btnLangToggle.setOnClickListener {
            playClickFeedback()
            isEsToEn = !isEsToEn
            btnLangToggle.text = if (isEsToEn) "ES ➔ EN" else "EN ➔ ES"
        }
        btnTranslateSend.setOnClickListener {
            playClickFeedback()
            val ic = currentInputConnection ?: return@setOnClickListener
            val textToTranslate = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
            if (textToTranslate.isNotBlank()) {
                btnTranslateSend.text = "⏳ Traduciendo..."
                btnTranslateSend.isEnabled = false
                thread {
                    try {
                        val sl = if (isEsToEn) "es" else "en"
                        val tl = if (isEsToEn) "en" else "es"
                        val encodedText = URLEncoder.encode(textToTranslate, "UTF-8")
                        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encodedText"
                        val conn = URL(urlStr).openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                        conn.connectTimeout = 5000
                        
                        if (conn.responseCode == 200) {
                            val response = conn.inputStream.bufferedReader().readText()
                            val jsonArray = JSONArray(response)
                            val translatedText = jsonArray.getJSONArray(0).getJSONArray(0).getString(0)
                            Handler(Looper.getMainLooper()).post {
                                ic.deleteSurroundingText(textToTranslate.length, 0)
                                ic.commitText(translatedText, 1)
                                btnTranslateSend.text = "✨ Traducir texto"
                                btnTranslateSend.isEnabled = true
                            }
                        } else throw Exception()
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@MiTecladoAnclado, "Error de internet", Toast.LENGTH_SHORT).show()
                            btnTranslateSend.text = "✨ Traducir texto"
                            btnTranslateSend.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps(currentInputEditorInfo)
    }

    private fun updateAutoCaps(info: EditorInfo?) {
        if (info != null && shiftState != 2) { 
            val capsMode = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
            if (capsMode != 0) {
                setShiftState(1)
            } else if (shiftState == 1) {
                setShiftState(0)
            }
        }
    }

    private fun setShiftState(state: Int) {
        shiftState = state
        val isUpper = shiftState > 0
        updateLettersCase(layoutLetters as ViewGroup, isUpper)
    }

    private fun updateLettersCase(group: ViewGroup, isUpper: Boolean) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ViewGroup) {
                updateLettersCase(child, isUpper)
            } else if (child is Button) {
                val tag = child.tag as? String
                if (tag == "SHIFT") {
                    child.text = when(shiftState) {
                        0 -> "⇧"
                        1 -> "⬆"
                        else -> "⇪" 
                    }
                } else if (tag == null && child.text.length == 1) {
                    val letter = child.text.toString()
                    if (letter.matches(Regex("[a-zA-ZñÑ]"))) {
                        child.text = if (isUpper) letter.uppercase() else letter.lowercase()
                    }
                }
            }
        }
    }

    private fun setKeyListeners(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) setKeyListeners(child)
            else if (child is Button) {
                val tag = child.tag as? String
                
                if (tag == "IGNORE") continue
                
                child.setOnLongClickListener {
                    if (isQrMode) return@setOnLongClickListener false
                    val text = child.text.toString().lowercase()
                    val accentedChar = when(text) {
                        "a" -> "á"; "e" -> "é"; "i" -> "í"; "o" -> "ó"; "u" -> "ú"; "n" -> "ñ"; "!" -> "¡"; "?" -> "¿"; else -> null
                    }
                    if (accentedChar != null) {
                        playClickFeedback()
                        currentInputConnection?.commitText(if (shiftState > 0) accentedChar.uppercase() else accentedChar, 1)
                        if (shiftState == 1) setShiftState(0) 
                        true 
                    } else false 
                }

                if (tag == "DELETE") {
                    child.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                playClickFeedback()
                                if (isQrMode) {
                                    if (qrSearchQuery.isNotEmpty()) {
                                        qrSearchQuery = qrSearchQuery.dropLast(1)
                                        updateQrSearchUI()
                                    } else {
                                        closeQrMode()
                                    }
                                } else {
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                }
                                deleteHandler.postDelayed(deleteRunnable, 400) 
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> deleteHandler.removeCallbacks(deleteRunnable)
                        }
                        true
                    }
                } else {
                    child.setOnClickListener { handleKeyPress(child) }
                }
            }
        }
    }

    private fun handleKeyPress(button: Button) {
        val tag = button.tag as? String
        if (tag != "ENTER") playClickFeedback() 
        val textToInsert = button.text.toString()

        if (isQrMode) {
            when (tag) {
                "CLOSE_QR_MODE" -> closeQrMode()
                "CLEAR_QR_SEARCH" -> { qrSearchQuery = ""; updateQrSearchUI() }
                "SPACE" -> { qrSearchQuery += " "; updateQrSearchUI() }
                null -> {
                    if (textToInsert.length == 1) {
                        qrSearchQuery += textToInsert.lowercase()
                        updateQrSearchUI()
                    }
                }
            }
            return
        }

        val ic = currentInputConnection ?: return

        when (tag) {
            "ENTER" -> {
                playEnterSound()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            "SPACE" -> ic.commitText(" ", 1)
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            "MIC" -> startVoiceRecognition()
            "SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime < 400) setShiftState(2) else setShiftState(if (shiftState == 0) 1 else 0)
                lastShiftTime = now
            }
            
            "OPEN_TRANSLATOR" -> { layoutTopBar.visibility = View.GONE; layoutTranslatorBar.visibility = View.VISIBLE }
            "CLOSE_TRANSLATOR" -> { layoutTranslatorBar.visibility = View.GONE; layoutTopBar.visibility = View.VISIBLE }
            "OPEN_QR_MODE" -> openQrMode()
            
            // ABRIR EMOJIS
            "OPEN_EMOJI" -> switchLayout(layoutEmojis)
            
            "CLIPBOARD" -> { checkSystemClipboard(); switchLayout(layoutClipboard) }
            "MODE_LETTERS" -> switchLayout(layoutLetters)
            "MODE_SYM1" -> switchLayout(layoutSymbols1)
            "MODE_SYM2" -> switchLayout(layoutSymbols2)
            "MODE_NUMPAD" -> switchLayout(layoutNumpad) 
            
            else -> {
                ic.commitText(textToInsert, 1)
                if (shiftState == 1 && tag == null && textToInsert.length == 1) setShiftState(0)
            }
        }
    }

    private fun switchLayout(activeLayout: View) {
        layoutLetters.visibility = View.GONE
        layoutSymbols1.visibility = View.GONE
        layoutSymbols2.visibility = View.GONE
        layoutNumpad.visibility = View.GONE
        layoutClipboard.visibility = View.GONE
        layoutEmojis.visibility = View.GONE // Asegura apagar emojis
        activeLayout.visibility = View.VISIBLE
    }

    private fun checkSystemClipboard() {
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val newText = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
                    if (!newText.isNullOrBlank()) {
                        val items = DataManager.loadItems(this)
                        if (items.find { it.text == newText } == null) {
                            items.add(0, ClipboardItem(newText, false))
                            DataManager.saveItems(this, items)
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        
        if (::adapter.isInitialized) {
            Handler(Looper.getMainLooper()).post { adapter.updateData(DataManager.loadItems(this)) }
        }
    }

    private fun handleLongPressItem(item: ClipboardItem, position: Int) {
        val items = DataManager.loadItems(this)
        val realItem = items.find { it.text == item.text } ?: return
        if (realItem.isPinned) {
            realItem.isPinned = false
            Toast.makeText(this, "Elemento desanclado", Toast.LENGTH_SHORT).show()
        } else {
            realItem.isPinned = true
            items.remove(realItem); items.add(0, realItem)
            Toast.makeText(this, "📌 Elemento anclado", Toast.LENGTH_SHORT).show()
        }
        DataManager.saveItems(this, items)
        adapter.updateData(items)
    }

    private fun clearUnpinned() {
        val items = DataManager.loadItems(this)
        val pinnedOnly = items.filter { it.isPinned }.toMutableList()
        DataManager.saveItems(this, pinnedOnly)
        adapter.updateData(pinnedOnly)
        Toast.makeText(this, "Elementos recientes eliminados", Toast.LENGTH_SHORT).show()
    }

    private fun updateMicStatus(text: String) { btnMic1?.text = text }
    
    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { updateMicStatus("🔴") }
                override fun onBeginningOfSpeech() { updateMicStatus("🗣️") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { updateMicStatus("⏳") }
                override fun onError(error: Int) { updateMicStatus("🎤") }
                override fun onResults(results: Bundle?) {
                    updateMicStatus("🎤")
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) currentInputConnection?.commitText(matches[0] + " ", 1)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Abre la app para dar permiso de micrófono", Toast.LENGTH_LONG).show()
            return
        }
        if (!::speechRecognizer.isInitialized) {
            Toast.makeText(this, "Error: Google Voice no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
        }
        try {
            updateMicStatus("🔴")
            speechRecognizer.startListening(intent)
        } catch (e: Exception) { updateMicStatus("🎤") }
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener) 
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }

    // --- NUEVO ADAPTADOR PARA EMOJIS (Motor de reciclaje infinito) ---
    inner class EmojiAdapter(private val emojiList: List<String>, private val onEmojiClick: (String) -> Unit) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {
        inner class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
                gravity = android.view.Gravity.CENTER
                textSize = 28f
            }
            return EmojiViewHolder(tv)
        }
        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.textView.text = emojiList[position]
            holder.textView.setOnClickListener { onEmojiClick(emojiList[position]) }
        }
        override fun getItemCount() = emojiList.size
    }

    // Adaptador de Respuestas Rápidas
    inner class QuickReplyKeyboardAdapter(private var items: List<QuickReplyItem>, private val onClick: (QuickReplyItem) -> Unit) : RecyclerView.Adapter<QuickReplyKeyboardAdapter.QRViewHolder>() {
        fun updateData(newItems: List<QuickReplyItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }
        inner class QRViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view) {
            val tvShortcut = TextView(view.context)
            val tvText = TextView(view.context)
            init {
                view.orientation = LinearLayout.VERTICAL
                view.setPadding(24, 24, 24, 24)
                view.setBackgroundColor(Color.parseColor("#1E1E1E"))
                val params = LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.MATCH_PARENT)
                params.setMargins(8, 8, 8, 8)
                view.layoutParams = params
                tvShortcut.setTextColor(Color.parseColor("#2196F3"))
                tvShortcut.textSize = 15f
                tvShortcut.setTypeface(null, Typeface.BOLD)
                tvText.setTextColor(Color.WHITE)
                tvText.textSize = 13f
                tvText.maxLines = 4
                tvText.ellipsize = android.text.TextUtils.TruncateAt.END
                tvText.setPadding(0, 8, 0, 0)
                view.addView(tvShortcut)
                view.addView(tvText)
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = QRViewHolder(LinearLayout(parent.context))
        override fun onBindViewHolder(holder: QRViewHolder, position: Int) {
            val item = items[position]
            holder.tvShortcut.text = "⚡ ${item.shortcut}"
            holder.tvText.text = item.text
            holder.view.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
}
