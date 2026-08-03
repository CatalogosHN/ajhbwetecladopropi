package com.brayan.tecladoanclado

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: PinnedAdapter
    private var items = mutableListOf<ClipboardItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pedir permiso del micrófono
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        items = DataManager.loadItems(this)
        val rvMainPins = findViewById<RecyclerView>(R.id.rvMainPins)
        val etNewPin = findViewById<EditText>(R.id.etNewPin)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        // Inicializar el adaptador
        adapter = PinnedAdapter(items, onItemClick = {}, onItemLongClick = { _, _ -> })

        rvMainPins.layoutManager = LinearLayoutManager(this)
        rvMainPins.adapter = adapter

        // Botón para agregar nuevos anclados manuales desde la app
        btnAdd.setOnClickListener {
            val text = etNewPin.text.toString()
            if (text.isNotBlank()) {
                items.add(ClipboardItem(text, isPinned = true))
                adapter.notifyItemInserted(items.size - 1)
                DataManager.saveItems(this, items)
                etNewPin.text.clear()
            }
        }

        // Lógica para arrastrar (reordenar) y deslizar (borrar)
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                Collections.swap(items, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                DataManager.saveItems(this@MainActivity, items)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                items.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                DataManager.saveItems(this@MainActivity, items)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvMainPins)
    }

    // ¡AQUÍ ESTÁ LA MAGIA NUEVA! 
    // Esta función obliga a la app a recargar los datos cada vez que la abres o regresas a ella
    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            items = DataManager.loadItems(this)
            adapter.updateData(items)
        }
    }
}
