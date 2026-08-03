package com.brayan.tecladoanclado

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Llama a la ventana oficial de Google Voice
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...")
        }
        
        try {
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                // Envía el texto de regreso al teclado
                val broadcastIntent = Intent("com.brayan.tecladoanclado.VOICE_TEXT")
                broadcastIntent.putExtra("text", text)
                sendBroadcast(broadcastIntent)
            }
        }
        finish() // Cierra la pantalla invisible
    }
}
