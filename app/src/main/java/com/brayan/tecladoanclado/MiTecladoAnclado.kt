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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
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
    
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private lateinit var audioManager: AudioManager
    private var soundEnabled = true
    private var soundEnterEnabled = true
    private var vibrationEnabled = false
    private var autocorrectEnabled = true
    
    private var learnedWords = mutableSetOf<String>()
    private var currentBestSuggestion = ""
    private var shiftState = 0
    private var lastShiftTime = 0L
    
    private var isQrMode = false
    private var qrSearchQuery = ""
    private var allQrItems = mutableListOf<QuickReplyItem>()
    private lateinit var qrAdapter: QuickReplyKeyboardAdapter
    
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
    private var isEsToEn = true

    private val deleteRunnable = object : Runnable {
        override fun run() {
            if (isQrMode) {
                if (qrSearchQuery.isNotEmpty()) {
                    qrSearchQuery = qrSearchQuery.dropLast(1)
                    updateQrSearchUI()
                } else closeQrMode()
            } else {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
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
        
        learnedWords = DataManager.loadLearnedWords(this)
        
        backgroundExecutor.execute {
            try {
                val identifier = resources.getIdentifier("diccionario", "raw", packageName)
                if (identifier != 0) {
                    val inputStream = resources.openRawResource(identifier)
                    val dictWords = inputStream.bufferedReader().readLines().map { it.trim().lowercase() }.filter { it.isNotBlank() }
                    learnedWords.addAll(dictWords)
                } else if (learnedWords.isEmpty()) {
                    val baseWords = listOf("qué", "cómo", "cuándo", "dónde", "quién", "por qué", "cuánto", "método", "envío", "garantía", "cámara", "teléfono", "también", "está", "días", "gracias", "artículo", "domicilio", "transferencia", "depósito", "número", "página", "tecnología", "promoción", "atención", "inmediata", "catálogo", "hola", "buenas", "tardes", "noches", "lempiras", "más", "sí", "aquí", "ahí", "allí", "él", "tú", "mí", "éxito", "rápido", "fácil", "útil", "increíble", "excelente", "ubicación", "dirección", "código", "guía", "recibo", "comprobante", "crédito", "débito", "artículos", "único", "electrónica", "audífonos", "batería", "cargador", "imágenes", "vídeo", "música", "tamaño", "volumen", "estás", "será", "hará", "tenía", "había", "podría", "debería", "sería", "mañana", "miércoles", "sábado", "próximo", "último", "máximo", "mínimo", "país", "región", "cámaras", "compra", "venta", "precio", "costo", "descuento", "gratis", "efectivo", "tarjeta", "saldo", "cuenta", "banco", "confirmar", "pedido", "entrega", "paquete", "sucursal", "disponible", "agotado", "color", "peso")
                    learnedWords.addAll(baseWords)
                    DataManager.saveLearnedWords(this@MiTecladoAnclado, learnedWords)
                }
            } catch (e: Exception) {}
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
            val text = (it as Button).text.toString()
            if (text.isNotBlank()) {
                playClickFeedback(it)
                insertSuggestion(text)
            }
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
        
        val btnTranslateSend = view.findViewById<Button>(R.id.btnTranslateSend)
        btnLangToggle.setOnClickListener {
            playClickFeedback(btnLangToggle)
            isEsToEn = !isEsToEn
            btnLangToggle.text = if (isEsToEn) "ES ➔ EN" else "EN ➔ ES"
        }
        btnTranslateSend.setOnClickListener { translateText(btnTranslateSend) }

        val btnClipboardEnter = view.findViewById<Button>(R.id.btnClipboardEnter)
        btnClipboardEnter?.setOnClickListener {
            playEnterSound(btnClipboardEnter)
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
            playClickFeedback(null)
            currentInputConnection?.commitText(selectedItem.text, 1)
            closeQrMode()
        }
        rvQuickRepliesKeyboard.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvQuickRepliesKeyboard.adapter = qrAdapter

        val rvEmojis = view.findViewById<RecyclerView>(R.id.rv_emojis_keyboard)
        val emojiList = "😀,😃,😄,😁,😆,😅,😂,🤣,🥲,☺️,😊,😇,🙂,🙃,😉,😌,😍,🥰,😘,😗,😙,😚,😋,😛,😝,😜,🤪,🤨,🧐,🤓,😎,🥸,🤩,🥳,😏,😒,😞,😔,😟,😕,🙁,☹️,😣,😖,😫,😩,🥺,😢,😭,😤,😠,😡,🤬,🤯,😳,🥵,🥶,😱,😨,😰,😥,😓,🫣,🤭,🫢,🫡,🤔,🤫,🤥,😶,😐,😑,😬,🙄,😯,😦,😧,😮,😲,🥱,😴,🤤,😪,😮‍💨,😵,😵‍💫,🤐,🥴,🤢,🤮,🤧,😷,🤒,🤕,🤑,🤠,😈,👿,👹,👺,🤡,💩,👻,💀,👽,👾,🤖,🎃,🫶,🤲,👐,🙌,👏,🤝,👍,👎,👊,✊,🤛,🤜,🤞,✌️,🫰,🤟,🤘,👌,🤌,🤏,🫳,🫴,👈,👉,👆,👇,☝️,✋,🤚,🖐,🖖,👋,🤙,💪,🦾,🖕,✍️,🙏,🦶,🦵,🦿,💄,💋,👄,🦷,👅,👂,🦻,👃,👣,👁,👀,🫀,🫁,🧠,🗣,👤,👥,🫂,❤️,🧡,💛,💚,💙,💜,🖤,🤍,🤎,💔,❣️,💕,💞,💓,💗,💖,💘,💝,❤️‍🔥,❤️‍🩹,📦,🚚,🛵,🚗,🚕,🚙,🚌,🚓,🚑,🚒,🛒,🛍️,🎁,🏷️,💲,💵,💴,💶,💷,🪙,💰,💳,🧾,✅,❌,⚠️,📌,📍,📲,📱,💻,🖥️,🖨️,📸,🎥,💥,🔥,✨,🌟,💫,⭐,🔥,💯,💢,💬,👁️‍🗨️,🗯️,💭,💤".split(",")
        rvEmojis.layoutManager = GridLayoutManager(this, 8) 
        rvEmojis.adapter = EmojiAdapter(emojiList) { emoji ->
            playClickFeedback(null)
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
        
        closeQrMode()
        checkSystemClipboard()
        updateAutoCaps(info)
        layoutSuggestionsBar.visibility = View.GONE
        layoutTopBar.visibility = View.VISIBLE
    }

    // MAGIA: HAPTIC FEEDBACK DIRECTO AL HARDWARE (CERO LATENCIA)
    private fun playClickFeedback(v: View?) {
        backgroundExecutor.execute {
            if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.8f)
        }
        if (vibrationEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    private fun playEnterSound(v: View?) {
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
        }
        if (vibrationEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    private fun getCurrentWord(): String {
        val ic = currentInputConnection ?: return ""
        val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: return ""
        return textBefore.takeLastWhile { it.isLetter() || it == 'ñ' || it == 'Ñ' || it == 'á' || it == 'é' || it == 'í' || it == 'ó' || it == 'ú' }
    }

    private fun updateSuggestionsUI() {
        if (!autocorrectEnabled) return
        val currentWord = getCurrentWord()
        
        if (currentWord.length >= 2) {
            val lowerWord = currentWord.lowercase()
            val cleanLower = removeAccents(lowerWord)

            val matches = learnedWords.asSequence()
                .filter { removeAccents(it).startsWith(cleanLower) && it.length >= currentWord.length && it != lowerWord }
                .take(2).toList()

            btnSuggest1.text = currentWord
            
            if (matches.isNotEmpty()) {
                currentBestSuggestion = matches[0]
                if (currentWord[0].isUpperCase()) currentBestSuggestion = currentBestSuggestion.replaceFirstChar { it.uppercase() }
                btnSuggest2.text = currentBestSuggestion
                btnSuggest3.text = if (matches.size > 1) matches[1] else ""
            } else {
                currentBestSuggestion = ""
                btnSuggest2.text = ""
                btnSuggest3.text = ""
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
            ic.deleteSurroundingText(currentWord.length, 0)
            ic.commitText("$suggestion ", 1)
            learnWord(suggestion)
        }
        mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
    }

    private fun learnWord(word: String) {
        if (word.length > 2) {
            val lowerWord = word.lowercase()
            if (!learnedWords.contains(lowerWord)) {
                learnedWords.add(lowerWord)
                backgroundExecutor.execute { DataManager.saveLearnedWords(this, learnedWords) }
            }
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

    private fun translateText(btnSend: Button) {
        playClickFeedback(btnSend)
        val ic = currentInputConnection ?: return
        val textToTranslate = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        if (textToTranslate.isNotBlank()) {
            btnSend.text = "⏳ Traduciendo..."
            btnSend.isEnabled = false
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
                            btnSend.text = "✨ Traducir texto"
                            btnSend.isEnabled = true
                            layoutTranslatorBar.visibility = View.GONE
                            layoutTopBar.visibility = View.VISIBLE
                        }
                    } else throw Exception()
                } catch (e: Exception) {
                    mainHandler.post {
                        Toast.makeText(this@MiTecladoAnclado, "Error de red", Toast.LENGTH_SHORT).show()
                        btnSend.text = "✨ Traducir texto"
                        btnSend.isEnabled = true
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
            if (capsMode != 0) setShiftState(1) else if (shiftState == 1) setShiftState(0)
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
                    child.text = when(shiftState) { 0 -> "⇧"; 1 -> "⬆"; else -> "⇪" }
                } else if (child.text.length == 1) {
                    val letter = child.text.toString()
                    if (letter[0].isLetter() || letter == "ñ" || letter == "Ñ") {
                        child.text = if (isUpper) letter.uppercase() else letter.lowercase()
                    }
                }
            }
        }
    }

    // MAGIA GBOARD: ACTION_POINTER_DOWN LEE MÚLTIPLES DEDOS A LA VEZ
    private fun setKeyListeners(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) setKeyListeners(child)
            else if (child is Button) {
                val tag = child.tag as? String
                
                if (tag == "SUGGESTION") continue
                
                val isActionKey = tag in listOf("MIC", "OPEN_TRANSLATOR", "CLIPBOARD", "MODE_LETTERS", "CLEAR_CLIPBOARD", "OPEN_QR_MODE", "CLOSE_QR_MODE", "CLEAR_QR_SEARCH", "OPEN_EMOJI", "MODE_SYM1", "MODE_SYM2", "MODE_NUMPAD", "CLOSE_TRANSLATOR", "IGNORE")
                
                if (isActionKey) {
                    child.setOnClickListener { handleKeyPress(child) }
                    continue
                }

                if (tag == "DELETE") {
                    child.setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                playClickFeedback(v)
                                if (isQrMode) {
                                    if (qrSearchQuery.isNotEmpty()) {
                                        qrSearchQuery = qrSearchQuery.dropLast(1)
                                        updateQrSearchUI()
                                    } else closeQrMode()
                                } else {
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                    mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
                                }
                                mainHandler.postDelayed(deleteRunnable, 400) 
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
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
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                            isLongPress = false
                            playClickFeedback(v)

                            if (tag == null || tag.matches(Regex("\\d"))) {
                                val textToInsert = child.text.toString()
                                if (isQrMode) {
                                    qrSearchQuery += textToInsert.lowercase()
                                    updateQrSearchUI()
                                } else {
                                    currentInputConnection?.commitText(textToInsert, 1)
                                    if (shiftState == 1) setShiftState(0)
                                    mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
                                }
                            } else if (tag == "SPACE" || tag == "ENTER" || tag == "SHIFT") {
                                handleKeyPress(child)
                            }

                            val text = child.text.toString().lowercase()
                            val numTag = tag
                            val accentedChar = when(text) { "a"->"á"; "e"->"é"; "i"->"í"; "o"->"ó"; "u"->"ú"; "n"->"ñ"; else -> null }
                            
                            if (accentedChar != null || (numTag != null && numTag.matches(Regex("\\d")))) {
                                longPressRunnable = Runnable {
                                    isLongPress = true
                                    if (vibrationEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                                    
                                    if (!isQrMode) {
                                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                        
                                        val textToInsert = if (numTag != null && numTag.matches(Regex("\\d"))) numTag else if (shiftState > 0) accentedChar?.uppercase() else accentedChar
                                        currentInputConnection?.commitText(textToInsert, 1)
                                        mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
                                    }
                                }
                                mainHandler.postDelayed(longPressRunnable!!, 350)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
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
                playEnterSound(button)
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
                mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
            }
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            "MIC" -> {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Instala la app de Google", Toast.LENGTH_SHORT).show()
                }
            }
            "SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime < 400) setShiftState(2) else setShiftState(if (shiftState == 0) 1 else 0)
                lastShiftTime = now
            }
            "OPEN_TRANSLATOR" -> { layoutTopBar.visibility = View.GONE; layoutSuggestionsBar.visibility = View.GONE; layoutTranslatorBar.visibility = View.VISIBLE }
            "CLOSE_TRANSLATOR" -> { layoutTranslatorBar.visibility = View.GONE; layoutTopBar.visibility = View.VISIBLE }
            "OPEN_QR_MODE" -> openQrMode()
            "CLOSE_QR_MODE" -> closeQrMode()
            "CLEAR_QR_SEARCH" -> { qrSearchQuery = ""; updateQrSearchUI() }
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
        if (::adapter.isInitialized) mainHandler.post { adapter.updateData(DataManager.loadItems(this)) }
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
        Toast.makeText(this, "Borrados", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener) 
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
