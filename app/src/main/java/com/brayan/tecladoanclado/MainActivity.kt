package com.brayan.tecladoanclado

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
    private lateinit var adapter: PinnedAdapter
    private var items = mutableListOf<ClipboardItem>()

    companion object {
        private const val PICK_FILE_REQUEST_CODE = 101
        private const val CREATE_FILE_REQUEST_CODE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        items = DataManager.loadItems(this)
        val rvMainPins = findViewById<RecyclerView>(R.id.rvMainPins)
        val etNewPin = findViewById<EditText>(R.id.etNewPin)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnExport = findViewById<Button>(R.id.btnExport)
        val btnImport = findViewById<Button>(R.id.btnImport)

        adapter = PinnedAdapter(items, onItemClick = {}, onItemLongClick = { _, _ -> })

        rvMainPins.layoutManager = LinearLayoutManager(this)
        rvMainPins.adapter = adapter

        // Guardar individual
        btnAdd.setOnClickListener {
            val text = etNewPin.text.toString()
            if (text.isNotBlank()) {
                items.add(ClipboardItem(text, isPinned = true))
                adapter.notifyItemInserted(items.size - 1)
                DataManager.saveItems(this, items)
                etNewPin.text.clear()
                Toast.makeText(this, "Respuesta guardada", Toast.LENGTH_SHORT).show()
            }
        }

        // Exportar a TXT
        btnExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "respuestas_teclado.txt")
            }
            startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
        }

        // Importar de TXT
        btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
        }

        // Reordenar y borrar deslizando
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

    // Manejar la respuesta del explorador de archivos (Importar / Exportar)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            try {
                if (requestCode == CREATE_FILE_REQUEST_CODE) {
                    // Escribir archivo separando con guiones
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            val sb = StringBuilder()
                            for (item in items) {
                                sb.append(item.text).append("\n---------\n")
                            }
                            writer.write(sb.toString())
                        }
                    }
                    Toast.makeText(this, "¡Respuestas exportadas con éxito!", Toast.LENGTH_SHORT).show()
                } else if (requestCode == PICK_FILE_REQUEST_CODE) {
                    // Leer archivo y dividir por los guiones "---------"
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val content = reader.readText()
                            val rawList = content.split(Regex("----[-]*")) // Detecta 3 o más guiones
                            val newItems = mutableListOf<ClipboardItem>()
                            for (block in rawList) {
                                val trimmed = block.trim()
                                if (trimmed.isNotEmpty()) {
                                    newItems.add(ClipboardItem(trimmed, isPinned = true))
                                }
                            }
                            if (newItems.isNotEmpty()) {
                                items.clear()
                                items.addAll(newItems)
                                DataManager.saveItems(this, items)
                                adapter.updateData(items)
                                Toast.makeText(this, "¡Importadas ${newItems.size} respuestas!", Toast.LENGTH_SHORT).show()
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
        if (::adapter.isInitialized) {
            items = DataManager.loadItems(this)
            adapter.updateData(items)
        }
    }
}
