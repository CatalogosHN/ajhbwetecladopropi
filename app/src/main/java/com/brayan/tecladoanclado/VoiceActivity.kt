package com.brayan.tecladoanclado

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-HN")
            }
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Guardamos lo que hablaste en la memoria temporal
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                DataManager.setPendingVoiceText(this, matches[0] + " ")
            }
        }
        finish()
    }
}
