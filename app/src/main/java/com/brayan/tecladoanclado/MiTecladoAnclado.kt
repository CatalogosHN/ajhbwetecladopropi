package com.brayan.tecladoanclado

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
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
    private var isCapsOn = false
    private lateinit var clipboardManager: ClipboardManager
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

    // Antena del portapapeles
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkSystemClipboard()
    }

    // Antena para recibir el texto dictado por Google
    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text")
            if (!text.isNullOrEmpty()) {
                currentInputConnection?.commitText("$text ", 1)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        
        // Encender la antena de voz
        val filter = IntentFilter("com.brayan.tecladoanclado.VOICE_TEXT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(voiceReceiver, filter)
        }
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)

        // Botones de micrófono
        btnMic1 = view.findViewById(R.id.btnMic1)
        btnMic2 = view.findViewById(R.id.btnMic2)
        btnMic3 = view.findViewById(R.id.btnMic3)
        
        // Botón Enter Flotante
        val btnClipboardEnter = view.findViewById<Button>(R.id.btnClipboardEnter)
        btnClipboardEnter?.setOnClickListener {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        val items = DataManager.loadItems(this)
        
        adapter = PinnedAdapter(items, 
            onItemClick = { text ->
                currentInputConnection?.commitText(text, 1)
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        checkSystemClipboard()
    }

    private fun checkSystemClipboard() {
        if (clipboardManager.hasPrimaryClip()) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val newText = clip.getItemAt(0).text?.toString()
                if (!newText.isNullOrBlank()) {
                    val items = DataManager.loadItems(this)
                    val existingItem = items.find { it.text == newText }
                    if (existingItem == null) {
                        items.add(0, ClipboardItem(newText, false))
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
            realItem.isPinned = false
            DataManager.saveItems(this, items)
            adapter.updateData(items)
            Toast.makeText(this, "Elemento desanclado", Toast.LENGTH_SHORT).show()
        } else {
            realItem.isPinned = true
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
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            
            // Abre la ventana de Google al tocar el microfono
            "MIC" -> {
                val intent = Intent(this, VoiceActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            
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

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener) 
        unregisterReceiver(voiceReceiver) // Apaga la antena al cerrar el teclado
    }
}
