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
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Motores
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private var soundEnabled = true
    private var soundEnterEnabled = true
    private var vibrationEnabled = false
    private var autocorrectEnabled = true
    
    // Memoria de Palabras
    private var learnedWords = mutableSetOf<String>()
    private var currentBestSuggestion = ""
    
    // Estados
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
    private lateinit var layoutSuggestionsBar: View
    private lateinit var layoutTranslatorBar: View
    private lateinit var layoutQrSearchBar: View
    private lateinit var tvQrSearch: TextView
    private lateinit var rvQuickRepliesKeyboard: RecyclerView
    
    private lateinit var btnSuggest1: Button
    private lateinit var btnSuggest2: Button
    private lateinit var btnSuggest3: Button
    
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutNumpad: View
    private lateinit var layoutClipboard: View
    private lateinit var layoutEmojis: View 

    private var btnMic1: Button? = null
    private lateinit var btnLangToggle: Button
    private lateinit var btnTranslateSend: Button

    private val deleteRunnable = object : Runnable {
        override fun run() {
            playClickFeedback()
            if (isQrMode) {
                if (qrSearchQuery.isNotEmpty()) {
                    qrSearchQuery = qrSearchQuery.dropLast(1)
                    updateQrSearchUI()
                } else closeQrMode()
            } else {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                mainHandler.postDelayed({ updateSuggestionsUI() }, 20)
            }
            mainHandler.postDelayed(this, 50) 
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { 
        mainHandler.postDelayed({ checkSystemClipboard() }, 150)
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        learnedWords = DataManager.loadLearnedWords(this)
        if (learnedWords.isEmpty()) {
            val baseWords = listOf("qué", "cómo", "cuándo", "dónde", "método", "envío", "garantía", "cámara", "teléfono", "también", "está", "días", "gracias", "artículo", "domicilio", "transferencia", "depósito", "número", "página", "tecnología", "promoción", "atención", "inmediata", "compra", "catálogo", "hola", "buenas", "tardes", "días", "noches", "lempiras", "lps")
            learnedWords.addAll(baseWords)
            DataManager.saveLearnedWords(this, learnedWords)
        }
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutTopBar = view.findViewById(R.id.layout_top_bar)
        layoutSuggestionsBar = view.findViewById(R.id.layout_suggestions_bar)
        layoutTranslatorBar = view.findViewById(R.id.layout_translator_bar)
        layoutQrSearchBar = view.findViewById(R.id.layout_qr_search_bar)
        tvQrSearch = view.findViewById(R.id.tvQrSearch)
        rvQuickRepliesKeyboard = view.findViewById(R.id.rv_quick_replies_keyboard)
        
        btnSuggest1 = view.findViewById(R.id.btnSuggest1)
        btnSuggest2 = view.findViewById(R.id.btnSuggest2)
        btnSuggest3 = view.findViewById(R.id.btnSuggest3)
        
        val suggestListener = View.OnClickListener {
            val text = it.tag as? String ?: return@OnClickListener
            playClickFeedback()
            insertSuggestion(text)
        }
        btnSuggest1.setOnClickListener(suggestListener)
        btnSuggest2.setOnClickListener(suggestListener)
        btnSuggest3.setOnClickListener(suggestListener)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutNumpad = view.findViewById(R.id.layout_numpad)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)
        layoutEmojis = view.findViewById(R.id.layout_emojis) 

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

        val rvClipboard = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        adapter = PinnedAdapter(DataManager.loadItems(this), 
            onItemClick = { text -> currentInputConnection?.commitText(text, 1) },
            onItemLongClick = { item, position -> handleLongPressItem(item, position) }
        )
        rvClipboard.layoutManager = GridLayoutManager(this, 3) 
        rvClipboard.adapter = adapter

        allQrItems = DataManager.loadQuickReplies(this)
        qrAdapter = QuickReplyKeyboardAdapter(allQrItems) { selectedItem ->
            playClickFeedback()
            currentInputConnection?.commitText(selectedItem.text, 1)
            closeQrMode()
        }
        rvQuickRepliesKeyboard.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvQuickRepliesKeyboard.adapter = qrAdapter

        val rvEmojis = view.findViewById<RecyclerView>(R.id.rv_emojis_keyboard)
        val emojiList = "😀,😃,😄,😁,😆,😅,😂,🤣,🥲,☺️,😊,😇,🙂,🙃,😉,😌,😍,🥰,😘,😗,😙,😚,😋,😛,😝,😜,🤪,🤨,🧐,🤓,😎,🥸,🤩,🥳,😏,😒,😞,😔,😟,😕,🙁,☹️,😣,😖,😫,😩,🥺,😢,😭,😤,😠,😡,🤬,🤯,😳,🥵,🥶,😱,😨,😰,😥,😓,🫣,🤭,🫢,🫡,🤔,🤫,🤥,😶,😐,😑,😬,🙄,😯,😦,😧,😮,😲,🥱,😴,🤤,😪,😮‍💨,😵,😵‍💫,🤐,🥴,🤢,🤮,🤧,😷,🤒,🤕,🤑,🤠,😈,👿,👹,👺,🤡,💩,👻,💀,👽,👾,🤖,🎃,🫶,🤲,👐,🙌,👏,🤝,👍,👎,👊,✊,🤛,🤜,🤞,✌️,🫰,🤟,🤘,👌,🤌,🤏,🫳,🫴,👈,👉,👆,👇,☝️,✋,🤚,🖐,🖖,👋,🤙,💪,🦾,🖕,✍️,🙏,🦶,🦵,🦿,💄,💋,👄,🦷,👅,👂,🦻,👃,👣,👁,👀,🫀,🫁,🧠,🗣,👤,👥,🫂,❤️,🧡,💛,💚,💙,💜,🖤,🤍,🤎,💔,❣️,💕,💞,💓,💗,💖,💘,💝,❤️‍🔥,❤️‍🩹,📦,🚚,🛵,🚗,🚕,🚙,🚌,🚓,🚑,🚒,🛒,🛍️,🎁,🏷️,💲,💵,💴,💶,💷,🪙,💰,💳,🧾,✅,❌,⚠️,📌,📍,📲,📱,💻,🖥️,🖨️,📸,🎥,💥,🔥,✨,🌟,💫,⭐,🔥,💯,💢,💬,👁️‍🗨️,🗯️,💭,💤".split(",")
        
        rvEmojis.layoutManager = GridLayoutManager(this, 8) 
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
        autocorrectEnabled = DataManager.isAutocorrectEnabled(this)
        allQrItems = DataManager.loadQuickReplies(this)
        learnedWords = DataManager.loadLearnedWords(this)
        
        closeQrMode()
        checkSystemClipboard()
        updateAutoCaps(info)
        layoutSuggestionsBar.visibility = View.GONE
        layoutTopBar.visibility = View.VISIBLE
    }

    private fun playClickFeedback() {
        backgroundExecutor.execute {
            if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.8f)
            if (vibrationEnabled) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") vibrator.vibrate(40)
                } catch (e: Exception) {}
            }
        }
    }

    private fun playEnterSound() {
        backgroundExecutor.execute {
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
    }

    // --- CORRECCIÓN: CÁLCULO EXACTO DE LA PALABRA PARA REEMPLAZARLA ---
    private fun getCurrentWord(): String {
        val ic = currentInputConnection ?: return ""
        val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: return ""
        // Toma todas las letras pegadas al cursor hacia atrás
        val word = textBefore.takeLastWhile { it.isLetter() || it == 'ñ' || it == 'Ñ' || it == 'á' || it == 'é' || it == 'í' || it == 'ó' || it == 'ú' }
        return word
    }

    private fun updateSuggestionsUI() {
        if (!autocorrectEnabled) return
        val currentWord = getCurrentWord()
        
        if (currentWord.length >= 2) {
            val lowerWord = currentWord.lowercase()
            val matches = learnedWords.filter { 
                removeAccents(it).startsWith(removeAccents(lowerWord)) && it.length >= currentWord.length
            }.sortedBy { it.length }.take(2)

            btnSuggest1.text = currentWord; btnSuggest1.tag = currentWord
            
            if (matches.isNotEmpty()) {
                currentBestSuggestion = matches[0]
                if (currentWord[0].isUpperCase()) currentBestSuggestion = currentBestSuggestion.replaceFirstChar { it.uppercase() }
                
                btnSuggest2.text = currentBestSuggestion; btnSuggest2.tag = currentBestSuggestion
                
                if (matches.size > 1) {
                    btnSuggest3.text = matches[1]; btnSuggest3.tag = matches[1]
                } else {
                    btnSuggest3.text = ""; btnSuggest3.tag = ""
                }
            } else {
                currentBestSuggestion = ""
                btnSuggest2.text = ""; btnSuggest2.tag = ""
                btnSuggest3.text = ""; btnSuggest3.tag = ""
            }
            
            layoutTopBar.visibility = View.GONE
            layoutSuggestionsBar.visibility = View.VISIBLE
        } else {
            currentBestSuggestion = ""
            layoutSuggestionsBar.visibility = View.GONE
            layoutTopBar.visibility = View.VISIBLE
        }
    }

    private fun insertSuggestion(suggestion: String) {
        val currentWord = getCurrentWord()
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty() && suggestion.isNotEmpty()) {
            // BORRA EXACTAMENTE LA LONGITUD DE LA PALABRA ACTUAL Y PEGA LA NUEVA
            ic.deleteSurroundingText(currentWord.length, 0)
            ic.commitText("$suggestion ", 1)
            learnWord(suggestion)
        }
        mainHandler.postDelayed({ updateSuggestionsUI() }, 50)
    }

    private fun learnWord(word: String) {
        if (word.length > 2) {
            learnedWords.add(word.lowercase())
            backgroundExecutor.execute { DataManager.saveLearnedWords(this, learnedWords) }
        }
    }

    private fun removeAccents(str: String): String {
        return str.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
    }

    private fun openQrMode() {
        isQrMode = true
        qrSearchQuery = ""
        layoutTopBar.visibility = View.GONE
        layoutTranslatorBar.visibility = View.GONE
        layoutSuggestionsBar.visibility = View.GONE
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
                            mainHandler.post {
                                ic.deleteSurroundingText(textToTranslate.length, 0)
                                ic.commitText(translatedText, 1)
                                btnTranslateSend.text = "✨ Traducir texto"
                                btnTranslateSend.isEnabled = true
                            }
                        } else throw Exception()
                    } catch (e: Exception) {
                        mainHandler.post {
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
                    if (letter[0].isLetter() || letter == "ñ" || letter == "Ñ" || letter == "á" || letter == "é" || letter == "í" || letter == "ó" || letter == "ú") {
                        child.text = if (isUpper) letter.uppercase() else letter.lowercase()
                    }
                }
            }
        }
    }

    // --- REESCRITURA MODO GBOARD: LATENCIA CERO Y ACTUACIÓN AL BAJAR EL DEDO ---
    private fun setKeyListeners(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) setKeyListeners(child)
            else if (child is Button) {
                val tag = child.tag as? String
                
                if (tag == "IGNORE" || tag == "OPEN_QR_MODE" || tag == "OPEN_EMOJI" || tag == "OPEN_TRANSLATOR" || tag == "CLIPBOARD" || tag == "CLOSE_QR_MODE" || tag == "CLEAR_QR_SEARCH") {
                    child.setOnClickListener { handleKeyPress(child) }
                    continue
                }

                if (tag == "DELETE") {
                    child.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                v.isPressed = true
                                playClickFeedback()
                                if (isQrMode) {
                                    if (qrSearchQuery.isNotEmpty()) {
                                        qrSearchQuery = qrSearchQuery.dropLast(1)
                                        updateQrSearchUI()
                                    } else closeQrMode()
                                } else {
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                    mainHandler.postDelayed({ updateSuggestionsUI() }, 20)
                                }
                                mainHandler.postDelayed(deleteRunnable, 400) 
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.isPressed = false
                                mainHandler.removeCallbacks(deleteRunnable)
                            }
                        }
                        true
                    }
                    continue
                }

                var isLongPress = false
                var longPressRunnable: Runnable? = null

                child.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isLongPress = false
                            v.isPressed = true
                            
                            if (tag == "ENTER") playEnterSound() else playClickFeedback()

                            // MAGIA GBOARD: LAS LETRAS SE ESCRIBEN INSTANTANEAMENTE AL TOCAR, NO AL LEVANTAR
                            if (tag == null && child.text.length == 1) {
                                val textToInsert = child.text.toString()
                                if (isQrMode) {
                                    qrSearchQuery += textToInsert.lowercase()
                                    updateQrSearchUI()
                                } else {
                                    currentInputConnection?.commitText(textToInsert, 1)
                                    if (shiftState == 1) setShiftState(0)
                                    mainHandler.postDelayed({ updateSuggestionsUI() }, 20)
                                }
                            }

                            val text = child.text.toString().lowercase()
                            val accentedChar = when(text) {
                                "a" -> "á"; "e" -> "é"; "i" -> "í"; "o" -> "ó"; "u" -> "ú"
                                "n" -> "ñ"; "!" -> "¡"; "?" -> "¿"; else -> null
                            }

                            if (accentedChar != null) {
                                longPressRunnable = Runnable {
                                    isLongPress = true
                                    v.isPressed = false
                                    backgroundExecutor.execute {
                                        if (vibrationEnabled) {
                                            try {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                                                else @Suppress("DEPRECATION") vibrator.vibrate(20)
                                            } catch (e: Exception) {}
                                        }
                                    }
                                    if (!isQrMode) {
                                        // Borra la letra que escribimos rápido y pone la tilde
                                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                        val textToInsert = if (shiftState > 0) accentedChar.uppercase() else accentedChar
                                        currentInputConnection?.commitText(textToInsert, 1)
                                        mainHandler.postDelayed({ updateSuggestionsUI() }, 20)
                                    }
                                }
                                mainHandler.postDelayed(longPressRunnable!!, 350)
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            v.isPressed = false
                            longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                            
                            // Si NO fue una pulsación larga, procedemos con los botones especiales (Enter, Espacio)
                            if (!isLongPress && (tag != null || child.text.length != 1)) {
                                handleKeyPress(child)
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            v.isPressed = false
                            longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        }
                    }
                    true
                }
            }
        }
    }

    private fun handleKeyPress(button: Button) {
        val tag = button.tag as? String
        
        if (isQrMode && tag == "SPACE") {
            qrSearchQuery += " "
            updateQrSearchUI()
            return
        }

        val ic = currentInputConnection ?: return

        when (tag) {
            "ENTER" -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            "SPACE" -> {
                val word = getCurrentWord()
                if (autocorrectEnabled && currentBestSuggestion.isNotEmpty() && removeAccents(currentBestSuggestion.lowercase()) == removeAccents(word.lowercase()) && currentBestSuggestion != word) {
                    ic.deleteSurroundingText(word.length, 0)
                    ic.commitText("$currentBestSuggestion ", 1)
                    learnWord(currentBestSuggestion)
                } else {
                    if (word.isNotEmpty()) learnWord(word)
                    ic.commitText(" ", 1)
                }
                mainHandler.postDelayed({ updateSuggestionsUI() }, 20)
            }
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            "MIC" -> startVoiceRecognition()
            "SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime < 400) setShiftState(2) else setShiftState(if (shiftState == 0) 1 else 0)
                lastShiftTime = now
            }
            
            "OPEN_TRANSLATOR" -> { layoutTopBar.visibility = View.GONE; layoutSuggestionsBar.visibility = View.GONE; layoutTranslatorBar.visibility = View.VISIBLE }
            "CLOSE_TRANSLATOR" -> { layoutTranslatorBar.visibility = View.GONE; layoutTopBar.visibility = View.VISIBLE }
            "OPEN_QR_MODE" -> openQrMode()
            "OPEN_EMOJI" -> switchLayout(layoutEmojis)
            
            "CLIPBOARD" -> { checkSystemClipboard(); switchLayout(layoutClipboard) }
            "MODE_LETTERS" -> switchLayout(layoutLetters)
            "MODE_SYM1" -> switchLayout(layoutSymbols1)
            "MODE_SYM2" -> switchLayout(layoutSymbols2)
            "MODE_NUMPAD" -> switchLayout(layoutNumpad) 
        }
    }

    private fun switchLayout(activeLayout: View) {
        layoutLetters.visibility = View.GONE
        layoutSymbols1.visibility = View.GONE
        layoutSymbols2.visibility = View.GONE
        layoutNumpad.visibility = View.GONE
        layoutClipboard.visibility = View.GONE
        layoutEmojis.visibility = View.GONE 
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
            mainHandler.post { adapter.updateData(DataManager.loadItems(this)) }
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
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
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
        if (speechRecognizer == null) {
            Toast.makeText(this, "Google Voice bloqueado por el sistema. Instala la app 'Google' de la Play Store y actívala como motor de voz.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
        }
        try {
            updateMicStatus("🔴")
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) { 
            updateMicStatus("🎤") 
            Toast.makeText(this, "Asegúrate de que la app 'Google' esté instalada y habilitada.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener) 
        speechRecognizer?.destroy()
    }

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
