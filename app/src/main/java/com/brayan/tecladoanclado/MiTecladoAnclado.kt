package com.brayan.tecladoanclado

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MiTecladoAnclado : InputMethodService() {
    private lateinit var adapter: PinnedAdapter
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var speechRecognizer: SpeechRecognizer
    
    // Estados de Mayúscula: 0=Minúscula, 1=Una vez, 2=Caps Lock
    private var shiftState = 0
    private var lastShiftTime = 0L
    
    // Contenedores
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutNumpad: View
    private lateinit var layoutClipboard: View

    private var btnMic1: Button? = null

    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            deleteHandler.postDelayed(this, 50) 
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkSystemClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutNumpad = view.findViewById(R.id.layout_numpad)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)

        btnMic1 = view.findViewById(R.id.btnMic1)
        setupSpeechRecognizer()

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

    // Auto-Mayúsculas al iniciar una oración
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        checkSystemClipboard()
        updateAutoCaps(info)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps(currentInputEditorInfo)
    }

    private fun updateAutoCaps(info: EditorInfo?) {
        if (info != null && shiftState != 2) { // Solo si no está bloqueada
            val capsMode = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
            if (capsMode != 0) {
                setShiftState(1)
            } else if (shiftState == 1) {
                setShiftState(0)
            }
        }
    }

    // Actualiza visualmente las letras y el ícono
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
                        else -> "⇪" // Caps Lock
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
                
                // LÓGICA DE PULSACIÓN LARGA PARA TILDES RAPIDAS
                child.setOnLongClickListener {
                    val text = child.text.toString().lowercase()
                    val accentedChar = when(text) {
                        "a" -> "á"; "e" -> "é"; "i" -> "í"; "o" -> "ó"; "u" -> "ú"
                        "n" -> "ñ"; "!" -> "¡"; "?" -> "¿"
                        else -> null
                    }
                    if (accentedChar != null) {
                        val textToInsert = if (shiftState > 0) accentedChar.uppercase() else accentedChar
                        currentInputConnection?.commitText(textToInsert, 1)
                        if (shiftState == 1) setShiftState(0) // Regresar a minuscula si era estado 1
                        true // Consumió el evento
                    } else {
                        false
                    }
                }

                if (child.tag == "DELETE") {
                    child.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
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
        val ic = currentInputConnection ?: return
        val tag = button.tag as? String

        when (tag) {
            "ENTER" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            "SPACE" -> ic.commitText(" ", 1)
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            "MIC" -> startVoiceRecognition()
            
            "SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime < 400) {
                    setShiftState(2) // Doble toque -> Caps Lock
                } else {
                    setShiftState(if (shiftState == 0) 1 else 0)
                }
                lastShiftTime = now
            }
            
            // Navegación de menús
            "CLIPBOARD" -> { checkSystemClipboard(); switchLayout(layoutClipboard) }
            "MODE_LETTERS" -> switchLayout(layoutLetters)
            "MODE_SYM1" -> switchLayout(layoutSymbols1)
            "MODE_SYM2" -> switchLayout(layoutSymbols2)
            "MODE_NUMPAD" -> switchLayout(layoutNumpad) // Abre el teclado numerico grande
            
            else -> {
                val textToInsert = button.text.toString() // El texto del boton ya está en mayuscula/minuscula
                ic.commitText(textToInsert, 1)
                
                // Si escribes una letra y el Shift estaba en 1 (no bloqueado), regresa a minúscula
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
