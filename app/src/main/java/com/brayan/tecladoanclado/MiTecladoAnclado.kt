package com.brayan.tecladoanclado

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
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MiTecladoAnclado : InputMethodService() {
    private lateinit var adapter: PinnedAdapter
    private var isCapsOn = false
    private lateinit var speechRecognizer: SpeechRecognizer
    
    // Contenedores
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutClipboard: View

    private var btnMic1: Button? = null
    private var btnMic2: Button? = null
    private var btnMic3: Button? = null

    // Lógica para el borrado continuo
    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            deleteHandler.postDelayed(this, 50) 
        }
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

        // Configuración del portapapeles ¡HACIA ABAJO!
        val recyclerView = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        val items = DataManager.loadItems(this)
        adapter = PinnedAdapter(items, isEditable = false, onItemClick = { text ->
            currentInputConnection?.commitText(text, 1)
            // Regresa a las letras mágicamente después de pegar
            switchLayout(layoutLetters) 
        }) { }
        
        recyclerView.layoutManager = LinearLayoutManager(this) // Lista vertical
        recyclerView.adapter = adapter

        setKeyListeners(view as ViewGroup)
        
        return view
    }

    // Actualiza la lista si fuiste a la app a editar algo
    override fun onWindowShown() {
        super.onWindowShown()
        if (::adapter.isInitialized) {
            adapter.updateData(DataManager.loadItems(this))
        }
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
            
            // Acciones del portapapeles
            "CLIPBOARD" -> switchLayout(layoutClipboard)
            "MANAGE_CLIPBOARD" -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
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
