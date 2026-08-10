package com.brayan.tecladoanclado

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private lateinit var clipAdapter: PinnedAdapter
    private lateinit var qrAdapter: QuickReplyAppAdapter
    
    private var clipItems = mutableListOf<ClipboardItem>()
    private var qrItems = mutableListOf<QuickReplyItem>()
    
    // Controla qué pestaña estamos viendo
    private var isClipboardMode = true

    companion object {
        private const val PICK_FILE_REQUEST_CODE = 101
        private const val CREATE_FILE_REQUEST_CODE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Permisos de Audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        // Cargar listas guardadas
        clipItems = DataManager.loadItems(this)
        qrItems = DataManager.loadQuickReplies(this)

        val rvMainPins = findViewById<RecyclerView>(R.id.rvMainPins)
        val btnTabClip = findViewById<Button>(R.id.btnTabClip)
        val btnTabQR = findViewById<Button>(R.id.btnTabQR)
        val layoutClipInput = findViewById<LinearLayout>(R.id.layoutClipInput)
        val layoutQRInput = findViewById<LinearLayout>(R.id.layoutQRInput)
        
        val etNewPin = findViewById<EditText>(R.id.etNewPin)
        val etQrShortcut = findViewById<EditText>(R.id.etQrShortcut)
        val etQrText = findViewById<EditText>(R.id.etQrText)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        
        // ¡NUESTROS BOTONES RESCATADOS!
        val btnExport = findViewById<Button>(R.id.btnExport)
        val btnImport = findViewById<Button>(R.id.btnImport)

        // Configurar Switches
        val switchSound = findViewById<Switch>(R.id.switchSound)
        val switchSoundEnter = findViewById<Switch>(R.id.switchSoundEnter)
        val switchVibration = findViewById<Switch>(R.id.switchVibration)

        val switchAutocorrect = findViewById<Switch>(R.id.switchAutocorrect)
        
        switchSound.isChecked = DataManager.isSoundEnabled(this)
        switchSoundEnter.isChecked = DataManager.isSoundEnterEnabled(this)
        switchVibration.isChecked = DataManager.isVibrationEnabled(this)

        switchAutocorrect.isChecked = DataManager.isAutocorrectEnabled(this)
        
        switchSound.setOnCheckedChangeListener { _, isChecked -> DataManager.setSoundEnabled(this, isChecked) }
        switchSoundEnter.setOnCheckedChangeListener { _, isChecked -> DataManager.setSoundEnterEnabled(this, isChecked) }
        switchVibration.setOnCheckedChangeListener { _, isChecked -> DataManager.setVibrationEnabled(this, isChecked) }

        switchAutocorrect.setOnCheckedChangeListener { _, isChecked -> DataManager.setAutocorrectEnabled(this, isChecked) }

        // --- CONFIGURAR TECLA GATILLO ---
        val etQrTrigger = findViewById<android.widget.EditText>(R.id.etQrTrigger)
        val btnSaveTrigger = findViewById<android.widget.Button>(R.id.btnSaveTrigger)
        
        etQrTrigger.setText(DataManager.getQrTrigger(this))

        btnSaveTrigger.setOnClickListener {
            val newTrigger = etQrTrigger.text.toString()
            if (newTrigger.isNotEmpty()) {
                DataManager.setQrTrigger(this, newTrigger)
                android.widget.Toast.makeText(this, "Tecla actualizada a: $newTrigger", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "La tecla no puede estar vacía", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // Inicializar Adaptadores
        clipAdapter = PinnedAdapter(clipItems, onItemClick = {}, onItemLongClick = { _, _ -> })
        qrAdapter = QuickReplyAppAdapter(qrItems)
        rvMainPins.layoutManager = LinearLayoutManager(this)
        rvMainPins.adapter = clipAdapter

        // Lógica de Pestañas
        btnTabClip.setOnClickListener {
            isClipboardMode = true
            btnTabClip.backgroundTintList = getColorStateList(android.R.color.holo_blue_light)
            btnTabQR.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            layoutClipInput.visibility = View.VISIBLE
            layoutQRInput.visibility = View.GONE
            rvMainPins.adapter = clipAdapter
        }

        btnTabQR.setOnClickListener {
            isClipboardMode = false
            btnTabQR.backgroundTintList = getColorStateList(android.R.color.holo_blue_light)
            btnTabClip.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            layoutClipInput.visibility = View.GONE
            layoutQRInput.visibility = View.VISIBLE
            rvMainPins.adapter = qrAdapter
        }

        // Lógica para Guardar una nueva respuesta
        btnAdd.setOnClickListener {
            if (isClipboardMode) {
                val text = etNewPin.text.toString()
                if (text.isNotBlank()) {
                    clipItems.add(ClipboardItem(text, isPinned = true))
                    clipAdapter.notifyItemInserted(clipItems.size - 1)
                    DataManager.saveItems(this, clipItems)
                    etNewPin.text.clear()
                    Toast.makeText(this, "Portapapeles guardado", Toast.LENGTH_SHORT).show()
                }
            } else {
                val shortcut = etQrShortcut.text.toString().trim()
                val text = etQrText.text.toString().trim()
                if (shortcut.isNotBlank() && text.isNotBlank()) {
                    qrItems.add(QuickReplyItem(shortcut, text))
                    qrAdapter.notifyItemInserted(qrItems.size - 1)
                    DataManager.saveQuickReplies(this, qrItems)
                    etQrShortcut.text.clear()
                    etQrText.text.clear()
                    Toast.makeText(this, "Respuesta rápida guardada", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- LÓGICA DE EXPORTAR TXT ---
        btnExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                // El nombre cambia según la pestaña en la que estés
                putExtra(Intent.EXTRA_TITLE, if(isClipboardMode) "portapapeles.txt" else "respuestas_rapidas.txt")
            }
            startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
        }

        // --- LÓGICA DE IMPORTAR TXT ---
        btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
        }

        // Lógica para Reordenar y Eliminar Deslizando
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (isClipboardMode) {
                    Collections.swap(clipItems, fromPos, toPos)
                    clipAdapter.notifyItemMoved(fromPos, toPos)
                    DataManager.saveItems(this@MainActivity, clipItems)
                } else {
                    Collections.swap(qrItems, fromPos, toPos)
                    qrAdapter.notifyItemMoved(fromPos, toPos)
                    DataManager.saveQuickReplies(this@MainActivity, qrItems)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (isClipboardMode) {
                    clipItems.removeAt(pos)
                    clipAdapter.notifyItemRemoved(pos)
                    DataManager.saveItems(this@MainActivity, clipItems)
                } else {
                    qrItems.removeAt(pos)
                    qrAdapter.notifyItemRemoved(pos)
                    DataManager.saveQuickReplies(this@MainActivity, qrItems)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvMainPins)
    }

    // --- EL CEREBRO PARA LEER Y ESCRIBIR EL ARCHIVO TXT ---
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            try {
                if (requestCode == CREATE_FILE_REQUEST_CODE) {
                    // EXPORTAR
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            val sb = StringBuilder()
                            if (isClipboardMode) {
                                for (item in clipItems) {
                                    sb.append(item.text).append("\n---------\n")
                                }
                            } else {
                                for (item in qrItems) {
                                    sb.append("${item.shortcut} | ${item.text}").append("\n---------\n")
                                }
                            }
                            writer.write(sb.toString())
                        }
                    }
                    Toast.makeText(this, "¡Exportado con éxito!", Toast.LENGTH_SHORT).show()

                } else if (requestCode == PICK_FILE_REQUEST_CODE) {
                    // IMPORTAR
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val content = reader.readText()
                            val rawList = content.split(Regex("----[-]*"))
                            
                            if (isClipboardMode) {
                                // Importar Portapapeles normal
                                val newItems = mutableListOf<ClipboardItem>()
                                for (block in rawList) {
                                    val trimmed = block.trim()
                                    if (trimmed.isNotEmpty()) newItems.add(ClipboardItem(trimmed, isPinned = true))
                                }
                                if (newItems.isNotEmpty()) {
                                    clipItems.clear()
                                    clipItems.addAll(newItems)
                                    DataManager.saveItems(this, clipItems)
                                    clipAdapter.updateData(clipItems)
                                    Toast.makeText(this, "¡Importadas ${newItems.size} al portapapeles!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Importar Respuestas Rápidas (Formato: Clave | Respuesta)
                                val newItems = mutableListOf<QuickReplyItem>()
                                for (block in rawList) {
                                    val trimmed = block.trim()
                                    if (trimmed.isNotEmpty()) {
                                        val parts = trimmed.split("|", limit = 2)
                                        if (parts.size == 2) {
                                            newItems.add(QuickReplyItem(parts[0].trim(), parts[1].trim()))
                                        } else {
                                            // Si olvidaste ponerle clave al TXT, le asigna una por defecto
                                            newItems.add(QuickReplyItem("atajo", trimmed))
                                        }
                                    }
                                }
                                if (newItems.isNotEmpty()) {
                                    qrItems.clear()
                                    qrItems.addAll(newItems)
                                    DataManager.saveQuickReplies(this, qrItems)
                                    qrAdapter.updateData(qrItems) 
                                    Toast.makeText(this, "¡Importadas ${newItems.size} respuestas rápidas!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error procesando el archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clipItems = DataManager.loadItems(this)
        clipAdapter.updateData(clipItems)
        
        qrItems = DataManager.loadQuickReplies(this)
        qrAdapter.updateData(qrItems)
    }

    // --- ADAPTADOR VISUAL PARA LAS RESPUESTAS RÁPIDAS EN LA APP ---
    inner class QuickReplyAppAdapter(private var items: MutableList<QuickReplyItem>) : RecyclerView.Adapter<QuickReplyAppAdapter.QRViewHolder>() {
        
        fun updateData(newItems: MutableList<QuickReplyItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }

        inner class QRViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view) {
            val tvShortcut = TextView(view.context)
            val tvText = TextView(view.context)
            init {
                view.orientation = LinearLayout.VERTICAL
                view.setPadding(32, 24, 32, 24)
                view.setBackgroundColor(Color.parseColor("#1E1E1E"))
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, 8)
                view.layoutParams = params

                tvShortcut.setTextColor(Color.parseColor("#4CAF50"))
                tvShortcut.textSize = 16f
                tvShortcut.setTypeface(null, Typeface.BOLD)

                tvText.setTextColor(Color.WHITE)
                tvText.textSize = 14f
                tvText.setPadding(0, 8, 0, 0)
                
                view.addView(tvShortcut)
                view.addView(tvText)
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QRViewHolder {
            return QRViewHolder(LinearLayout(parent.context))
        }
        override fun onBindViewHolder(holder: QRViewHolder, position: Int) {
            val item = items[position]
            holder.tvShortcut.text = "⚡ Clave: ${item.shortcut}"
            holder.tvText.text = item.text
        }
        override fun getItemCount() = items.size
    }
}
