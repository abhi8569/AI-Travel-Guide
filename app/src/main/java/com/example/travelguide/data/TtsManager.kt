package com.example.travelguide.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentText: String? = null
    private var currentRate: Float = 1.0f

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                val result = it.setLanguage(Locale.getDefault())
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isInitialized = true
                    it.setSpeechRate(currentRate)
                    setupUtteranceListener()
                }
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
            }
        })
    }

    private var currentPitch: Float = 1.0f

    fun speak(text: String, rate: Float, pitch: Float) {
        currentText = text
        currentRate = rate
        currentPitch = pitch
        
        if (!isInitialized) {
            // Re-init if needed
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    setupUtteranceListener()
                    speak(text, rate, pitch)
                }
            }
            return
        }

        tts?.let {
            it.stop()
            it.setPitch(pitch)
            it.setSpeechRate(rate)
            _isSpeaking.value = true
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuideUtteranceId")
        }
    }

    fun setPitchAndSpeed(rate: Float, pitch: Float) {
        currentRate = rate
        currentPitch = pitch
        if (isInitialized) {
            tts?.setPitch(pitch)
            tts?.setSpeechRate(rate)
            if (_isSpeaking.value) {
                // Re-trigger speak to apply changes immediately if active
                currentText?.let { text ->
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuideUtteranceId")
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }
}
