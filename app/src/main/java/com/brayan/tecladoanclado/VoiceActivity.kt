package com.brayan.tecladoanclado

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // 1. Intentamos forzar la ventana oficial de Google agresivamente
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
                setPackage("com.google.android.googlequicksearchbox") // Obliga a usar Google
            }
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            // 2. Si Vivo desinstaló o bloqueó ese paquete, usamos el método estándar
            try {
                val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
                }
                startActivityForResult(fallbackIntent, 100)
            } catch (ex: Exception) {
                // Si todo falla, cierra el Ninja sin crashear el teclado
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 3. Capturamos lo que hablaste
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                
                // 4. Se lo lanzamos de regreso al archivo MiTecladoAnclado.kt
                val broadcastIntent = Intent("com.brayan.tecladoanclado.VOICE_TEXT")
                broadcastIntent.putExtra("text", text)
                sendBroadcast(broadcastIntent)
            }
        }
        // 5. El ninja desaparece
        finish()
    }
}
