package com.brayan.tecladoanclado

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
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
    
    // Motores de Audio y Vibración
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private var soundEnabled = true
    private var vibrationEnabled = false
    
    // Estados
    private var shiftState = 0
    private var lastShiftTime = 0L
    private var isEsToEn = true // Traductor: true = ES->EN, false = EN->ES
    
    // Contenedores
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutNumpad: View
    private lateinit var layoutClipboard: View
    private lateinit var layoutTranslator: View
    
    private var btnMic1: Button? = null
    private lateinit var etTranslateInput: EditText
    private lateinit var btnLangToggle: Button
    private lateinit var btnTranslateSend: Button

    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            playClickFeedback()
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            deleteHandler.postDelayed(this, 50) 
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { checkSystemClipboard() }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutNumpad = view.findViewById(R.id.layout_numpad)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)
        layoutTranslator = view.findViewById(R.id.layout_translator)

        btnMic1 = view.findViewById(R.id.btnMic1)
        etTranslateInput = view.findViewById(R.id.etTranslateInput)
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

        val recyclerView = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        val items = DataManager.loadItems(this)
        
        adapter = PinnedAdapter(items, 
            onItemClick = { text -> currentInputConnection?.commitText(text, 1) },
            onItemLongClick = { item, position -> handleLongPressItem(item, position) }
        )
        
        recyclerView.layoutManager = GridLayoutManager(this, 3) 
        recyclerView.adapter = adapter

        setKeyListeners(view as ViewGroup)
        
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        soundEnabled = DataManager.isSoundEnabled(this)
        vibrationEnabled = DataManager.isVibrationEnabled(this)
        checkSystemClipboard()
        updateAutoCaps(info)
    }

    // --- MAGIA DEL SONIDO GENERAL ---
    private fun playClickFeedback() {
        if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.8f)
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(40)
                }
            } catch (e: Exception) {}
        }
    }

    // --- SONIDO ÉPICO DEL ENTER ---
    private fun playEnterSound() {
        if (soundEnabled) {
            try {
                // Intenta reproducir tu archivo mp3 customizado
                val mediaPlayer = MediaPlayer.create(this, R.raw.sonido_enter)
                mediaPlayer.setOnCompletionListener { it.release() }
                mediaPlayer.start()
            } catch (e: Exception) {
                // Si olvidaste subir el archivo, suena el normal para que no se crashee
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 1.0f)
            }
        }
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            } catch (e: Exception) {}
        }
    }

    // --- LÓGICA DEL TRADUCTOR GOOGLE ---
    private fun setupTranslator() {
        btnLangToggle.setOnClickListener {
            isEsToEn = !isEsToEn
            btnLangToggle.text = if (isEsToEn) "ES ➔ EN" else "EN ➔ ES"
        }

        btnTranslateSend.setOnClickListener {
            val textToTranslate = etTranslateInput.text.toString()
            if (textToTranslate.isNotBlank()) {
                btnTranslateSend.text = "Traduciendo..."
                btnTranslateSend.isEnabled = false
                
                thread {
                    try {
                        val sl = if (isEsToEn) "es" else "en"
                        val tl = if (isEsToEn) "en" else "es"
                        val encodedText = URLEncoder.encode(textToTranslate, "UTF-8")
                        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encodedText"
                        
                        val conn = URL(urlStr).openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        
                        if (conn.responseCode == 200) {
                            val response = conn.inputStream.bufferedReader().readText()
                            val jsonArray = JSONArray(response)
                            val translatedText = jsonArray.getJSONArray(0).getJSONArray(0).getString(0)
                            
                            Handler(Looper.getMainLooper()).post {
                                currentInputConnection?.commitText(translatedText + " ", 1)
                                etTranslateInput.text.clear()
                                switchLayout(layoutLetters) // Regresa al teclado normal
                                btnTranslateSend.text = "✨ Traducir y Enviar al chat"
                                btnTranslateSend.isEnabled = true
                            }
                        } else {
                            throw Exception("Error de servidor")
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@MiTecladoAnclado, "Error de red. Revisa tu internet.", Toast.LENGTH_SHORT).show()
                            btnTranslateSend.text = "✨ Traducir y Enviar al chat"
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
                } else if (tag == null && child.text.length == 1 && child.text.first().isLetter()) {
                    child.text = if (isUpper) child.text.toString().uppercase() else child.text.toString().lowercase()
                }
            }
        }
    }

    private fun setKeyListeners(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) {
                setKeyListeners(child)
            } else if (child is Button) {
                
                child.setOnLongClickListener {
                    val text = child.text.toString().lowercase()
                    val accentedChar = when(text) {
                        "a" -> "á"; "e" -> "é"; "i" -> "í"; "o" -> "ó"; "u" -> "ú"
                        "n" -> "ñ"; "!" -> "¡"; "?" -> "¿"
                        else -> null
                    }
                    if (accentedChar != null) {
                        playClickFeedback()
                        val textToInsert = if (shiftState > 0) accentedChar.uppercase() else accentedChar
                        currentInputConnection?.commitText(textToInsert, 1)
                        if (shiftState == 1) setShiftState(0) 
                        true 
                    } else {
                        false
                    }
                }

                if (child.tag == "DELETE") {
                    child.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                playClickFeedback()
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
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
        
        // El Enter tiene su propio sonido especial
        if (tag != "ENTER") playClickFeedback()
        
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
                if (now - lastShiftTime < 400) {
                    setShiftState(2) 
                } else {
                    setShiftState(if (shiftState == 0) 1 else 0)
                }
                lastShiftTime = now
            }
            
            // Menús
            "TRANSLATOR" -> switchLayout(layoutTranslator)
            "CLIPBOARD" -> { checkSystemClipboard(); switchLayout(layoutClipboard) }
            "MODE_LETTERS" -> switchLayout(layoutLetters)
            "MODE_SYM1" -> switchLayout(layoutSymbols1)
            "MODE_SYM2" -> switchLayout(layoutSymbols2)
            "MODE_NUMPAD" -> switchLayout(layoutNumpad) 
            
            else -> {
                val textToInsert = button.text.toString() 
                ic.commitText(textToInsert, 1)
                if (shiftState == 1 && tag == null && textToInsert.length == 1) {
                    setShiftState(0)
                }
            }
        }
    }

    private fun switchLayout(activeLayout: View) {
        layoutLetters.visibility = View.GONE
        layoutSymbols1.visibility = View.GONE
        layoutSymbols2.visibility = View.GONE
        layoutNumpad.visibility = View.GONE
        layoutClipboard.visibility = View.GONE
        layoutTranslator.visibility = View.GONE
        activeLayout.visibility = View.VISIBLE
    }

    private fun checkSystemClipboard() {
        if (clipboardManager.hasPrimaryClip()) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val newText = clip.getItemAt(0).text?.toString()
                if (!newText.isNullOrBlank()) {
                    val items = DataManager.loadItems(this)
                    if (items.find { it.text == newText } == null) {
                        items.add(0, ClipboardItem(newText, false))
                        DataManager.saveItems(this, items)
                        if (::adapter.isInitialized) adapter.updateData(items)
                    }
                }
            }
        }
        if (::adapter.isInitialized) adapter.updateData(DataManager.loadItems(this))
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

    // --- CEREBRO DE VOZ INVISIBLE ---
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
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
        }
        try {
            updateMicStatus("🔴"); speechRecognizer.startListening(intent)
        } catch (e: Exception) { updateMicStatus("🎤") }
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener) 
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }
}
