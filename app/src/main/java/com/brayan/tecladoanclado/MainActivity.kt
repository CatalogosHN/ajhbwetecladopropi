package com.brayan.tecladoanclado

import android.Manifest
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
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private lateinit var clipAdapter: PinnedAdapter
    private lateinit var qrAdapter: QuickReplyAppAdapter
    
    private var clipItems = mutableListOf<ClipboardItem>()
    private var qrItems = mutableListOf<QuickReplyItem>()
    private var isClipboardMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

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

        // Configurar Switches
        val switchSound = findViewById<Switch>(R.id.switchSound)
        val switchSoundEnter = findViewById<Switch>(R.id.switchSoundEnter)
        val switchVibration = findViewById<Switch>(R.id.switchVibration)
        switchSound.isChecked = DataManager.isSoundEnabled(this)
        switchSoundEnter.isChecked = DataManager.isSoundEnterEnabled(this)
        switchVibration.isChecked = DataManager.isVibrationEnabled(this)
        switchSound.setOnCheckedChangeListener { _, isChecked -> DataManager.setSoundEnabled(this, isChecked) }
        switchSoundEnter.setOnCheckedChangeListener { _, isChecked -> DataManager.setSoundEnterEnabled(this, isChecked) }
        switchVibration.setOnCheckedChangeListener { _, isChecked -> DataManager.setVibrationEnabled(this, isChecked) }

        // Adaptadores
        clipAdapter = PinnedAdapter(clipItems, onItemClick = {}, onItemLongClick = { _, _ -> })
        qrAdapter = QuickReplyAppAdapter(qrItems)
        rvMainPins.layoutManager = LinearLayoutManager(this)
        rvMainPins.adapter = clipAdapter

        // Cambio de Pestañas
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

        // Guardar Datos
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

        // Reordenar y Eliminar deslizando
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

    // Adaptador programático visual para las respuestas
    inner class QuickReplyAppAdapter(private val items: MutableList<QuickReplyItem>) : RecyclerView.Adapter<QuickReplyAppAdapter.QRViewHolder>() {
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
