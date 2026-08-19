package com.brayan.tecladoanclado

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class VoiceActivity : Activity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentPartialText = "" // Aquí se guarda la palabra exacta en vivo
    private lateinit var tvStatus: TextView
    private var isSaved = false // Candado de seguridad para no guardar doble

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- DISEÑO VISUAL GENERADO POR CÓDIGO (Elegante y sin XML extra) ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E6121212")) // Negro transparente al 90%
        }
        
        val tvTitle = TextView(this).apply {
            text = "Dictado de Voz Blindado"
            setTextColor(Color.parseColor("#2196F3")) // Azul tecnológico
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        
        val iconMic = TextView(this).apply {
            text = "🎤"
            textSize = 70f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        
        tvStatus = TextView(this).apply {
            text = "Escuchando...\n(Habla ahora)"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(32, 0, 32, 0)
        }
        
        val btnStop = Button(this).apply {
            text = "Terminar"
            setBackgroundColor(Color.parseColor("#4285F4"))
            setTextColor(Color.WHITE)
            // Si el usuario toca terminar, salvamos el texto parcial y cerramos
            setOnClickListener { saveAndExit(currentPartialText) }
        }
        
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 60, 0, 0) }
        
        layout.addView(tvTitle)
        layout.addView(iconMic)
        layout.addView(tvStatus)
        layout.addView(btnStop, btnParams)
        
        setContentView(layout)

        startListening()
    }

    private fun startListening() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { tvStatus.text = "Te escucho..." }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    // Si falla el internet pero ya llevabas texto, lo salvamos!
                    if (currentPartialText.isNotBlank()) {
                        saveAndExit(currentPartialText)
                    } else {
                        Toast.makeText(this@VoiceActivity, "Pausa o sin conexión", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                override fun onResults(results: Bundle?) {
                    // Dictado completado con éxito
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        saveAndExit(matches[0])
                    } else {
                        finish()
                    }
                }

                // MAGIA: Captura palabra por palabra en vivo mientras hablas
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        currentPartialText = matches[0]
                        tvStatus.text = currentPartialText // Muestra el texto en pantalla en vivo
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN") // Español Honduras
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Activa la lectura en tiempo real
            }
            
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "El motor de voz de Google falló", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Guarda el texto en la bóveda y cierra
    private fun saveAndExit(text: String) {
        if (isSaved) return
        isSaved = true
        
        val cleanText = text.trim()
        if (cleanText.isNotEmpty()) {
            val previousText = DataManager.getPendingVoiceText(this)
            DataManager.setPendingVoiceText(this, previousText + cleanText + " ")
        }
        
        speechRecognizer?.cancel()
        finish() // Esto regresa el control a tu Teclado Anclado automáticamente
    }

    override fun onPause() {
        super.onPause()
        // EL SALVAVIDAS ANTI-LLAMADAS:
        // Si el teléfono recibe una llamada o minimizas la pantalla, se activa onPause.
        // Rescatamos lo que hayas dicho hasta ese milisegundo y lo guardamos a la fuerza.
        if (!isSaved && currentPartialText.isNotBlank()) {
            saveAndExit(currentPartialText)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy() // Limpia memoria para no gastar batería
    }
}
