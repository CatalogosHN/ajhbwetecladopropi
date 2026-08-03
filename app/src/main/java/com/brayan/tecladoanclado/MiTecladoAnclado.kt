package com.brayan.tecladoanclado

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MiTecladoAnclado : InputMethodService() {
    private lateinit var adapter: PinnedAdapter

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        
        val items = DataManager.loadItems(this)
        adapter = PinnedAdapter(items, isEditable = false, onItemClick = { text ->
            currentInputConnection?.commitText(text, 1)
        }) { }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        return view
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (::adapter.isInitialized) {
            adapter.updateData(DataManager.loadItems(this))
        }
    }
}
