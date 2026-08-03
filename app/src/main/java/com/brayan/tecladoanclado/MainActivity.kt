package com.brayan.tecladoanclado

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: PinnedAdapter
    private var items = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        items = DataManager.loadItems(this)

        val rvMainPins = findViewById<RecyclerView>(R.id.rvMainPins)
        val etNewPin = findViewById<EditText>(R.id.etNewPin)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        adapter = PinnedAdapter(items, isEditable = true, onItemClick = {}) {
            DataManager.saveItems(this, items)
        }

        rvMainPins.layoutManager = LinearLayoutManager(this)
        rvMainPins.adapter = adapter

        btnAdd.setOnClickListener {
            val text = etNewPin.text.toString()
            if (text.isNotBlank()) {
                items.add(text)
                adapter.notifyItemInserted(items.size - 1)
                DataManager.saveItems(this, items)
                etNewPin.text.clear()
            }
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(rvMainPins)
    }
}
