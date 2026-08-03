package com.brayan.tecladoanclado

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MiTecladoAnclado : InputMethodService() {
    private lateinit var adapter: PinnedAdapter
    private var isCapsOn = false
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var clipboardManager: ClipboardManager
    
    // Contenedores
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutClipboard: View

    private var btnMic1: Button? = null
    private var btnMic2: Button? = null
    private var btnMic3: Button? = null

    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            deleteHandler.postDelayed(this, 50) 
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)

        btnMic1 = view.findViewById(R.id.btnMic1)
        btnMic2 = view.findViewById(R.id.btnMic2)
        btnMic3 = view.findViewById(R.id.btnMic3)
        
        setupSpeechRecognizer()

        // Configuración de CUADRÍCULA DE 3 COLUMNAS
        val recyclerView = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        val items = DataManager.loadItems(this)
        
        adapter = PinnedAdapter(items, 
            onItemClick = { text ->
                currentInputConnection?.commitText(text, 1)
                switchLayout(layoutLetters) 
            },
            onItemLongClick = { item, position ->
                handleLongPressItem(item, position)
            }
        )
        
        recyclerView.layoutManager = GridLayoutManager(this, 3) 
        recyclerView.adapter = adapter

        setKeyListeners(view as ViewGroup)
        
        return view
    }

    override fun onWindowShown() {
        super.onWindowShown()
        checkSystemClipboard()
    }

    // Lee el portapapeles del sistema cuando abres el teclado
    private fun checkSystemClipboard() {
        if (clipboardManager.hasPrimaryClip()) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val newText = clip.getItemAt(0).text?.toString()
                if (!newText.isNullOrBlank()) {
                    val items = DataManager.loadItems(this)
                    // Verifica si ya es el primero o si ya está anclado para no duplicar inútilmente
                    val existingItem = items.find { it.text == newText }
                    if (existingItem == null) {
                        items.add(0, ClipboardItem(newText, false)) // Lo agrega arriba
                        DataManager.saveItems(this, items)
                        if (::adapter.isInitialized) adapter.updateData(items)
                    }
                }
            }
        }
        if (::adapter.isInitialized) {
            adapter.updateData(DataManager.loadItems(this))
        }
    }

    private fun handleLongPressItem(item: ClipboardItem, position: Int) {
        val items = DataManager.loadItems(this)
        val realItem = items.find { it.text == item.text } ?: return

        if (realItem.isPinned) {
            // Confirmación para DESANCLAR (El diálogo requiere permisos especiales en el teclado, así que usamos el contexto directo)
            val builder = AlertDialog.Builder(this)
            builder.setTitle("¿Desanclar?")
            builder.setMessage("Este elemento ya no estará protegido contra el borrado.")
            builder.setPositiveButton("Sí, desanclar") { _, _ ->
                realItem.isPinned = false
                DataManager.saveItems(this, items)
                adapter.updateData(items)
                Toast.makeText(this, "Elemento desanclado", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Cancelar", null)
            
            val dialog = builder.create()
            val window = dialog.window
            if (window != null) {
                // Truco vital para mostrar pop-ups sobre un teclado
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) 
            }
            dialog.show()

        } else {
            // ANCLAR directamente
            realItem.isPinned = true
            // Mover al principio de la lista
            items.remove(realItem)
            items.add(0, realItem)
            DataManager.saveItems(this, items)
            adapter.updateData(items)
            Toast.makeText(this, "📌 Elemento anclado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearUnpinned() {
        val items = DataManager.loadItems(this)
        val pinnedOnly = items.filter { it.isPinned }.toMutableList()
        DataManager.saveItems(this, pinnedOnly)
        adapter.updateData(pinnedOnly)
        Toast.makeText(this, "Elementos recientes eliminados", Toast.LENGTH_SHORT).show()
    }

    private fun setKeyListeners(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) {
                setKeyListeners(child)
            } else if (child is Button) {
                if (child.tag == "DELETE") {
                    child.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                deleteHandler.postDelayed(deleteRunnable, 400) 
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                deleteHandler.removeCallbacks(deleteRunnable)
                            }
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
            "SHIFT" -> toggleCaps()
            "MIC" -> startVoiceRecognition()
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            
            "CLIPBOARD" -> {
                checkSystemClipboard()
                switchLayout(layoutClipboard)
            }
            
            "MODE_LETTERS" -> switchLayout(layoutLetters)
            "MODE_SYM1" -> switchLayout(layoutSymbols1)
            "MODE_SYM2" -> switchLayout(layoutSymbols2)
            
            else -> {
                val textToInsert = if (isCapsOn) button.text.toString().uppercase() else button.text.toString()
                ic.commitText(textToInsert, 1)
                if (isCapsOn) toggleCaps()
            }
        }
    }

    private fun switchLayout(activeLayout: View) {
        layoutLetters.visibility = View.GONE
        layoutSymbols1.visibility = View.GONE
        layoutSymbols2.visibility = View.GONE
        layoutClipboard.visibility = View.GONE
        activeLayout.visibility = View.VISIBLE
    }

    private fun toggleCaps() {
        isCapsOn = !isCapsOn
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            private fun updateMicStatus(text: String) {
                btnMic1?.text = text
                btnMic2?.text = text
                btnMic3?.text = text
            }
            
            override fun onReadyForSpeech(params: Bundle?) { updateMicStatus("🔴") }
            override fun onResults(results: Bundle?) {
                updateMicStatus("🎤")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    currentInputConnection?.commitText(matches[0] + " ", 1)
                }
            }
            override fun onError(error: Int) { updateMicStatus("🎤") }
            
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
        }
        speechRecognizer.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
