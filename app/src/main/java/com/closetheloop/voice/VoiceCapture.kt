package com.closetheloop.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app speech capture via the system SpeechRecognizer (avoids the recognizer chooser). Main thread only. */
class VoiceCapture(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String = "", val rmsDb: Float = 0f) : State
        data class Heard(val text: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = State.Failed("Speech recognition unavailable")
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        _state.value = State.Listening()
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        )
    }

    fun cancel() {
        recognizer?.cancel()
        _state.value = State.Idle
    }

    fun reset() {
        _state.value = State.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            (_state.value as? State.Listening)?.let { _state.value = it.copy(rmsDb = rmsdB) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults) ?: return
            (_state.value as? State.Listening)?.let { _state.value = it.copy(partial = text) }
        }

        override fun onResults(results: Bundle?) {
            if (_state.value !is State.Listening) return
            _state.value = firstResult(results)?.let { State.Heard(it) } ?: State.Failed("Didn't catch that")
        }

        override fun onError(error: Int) {
            // Errors after cancel() (e.g. ERROR_CLIENT) are expected noise.
            if (_state.value !is State.Listening) return
            _state.value = State.Failed(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "No connection"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, try again"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language not available"
                    else -> "Speech error ($error)"
                }
            )
        }
    }
}
