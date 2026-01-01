package com.example.placemate.core.input

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startListening(): Flow<SpeechState> = callbackFlow {
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechState.Ready) }
            override fun onBeginningOfSpeech() { trySend(SpeechState.Listening) }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { trySend(SpeechState.Processing) }
            override fun onError(error: Int) { trySend(SpeechState.Error("Speech Error: $error")) }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { trySend(SpeechState.Result(it)) }
                close()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { trySend(SpeechState.PartialResult(it)) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(intent)

        awaitClose {
            speechRecognizer.destroy()
        }
    }
}

sealed class SpeechState {
    object Ready : SpeechState()
    object Listening : SpeechState()
    object Processing : SpeechState()
    data class PartialResult(val text: String) : SpeechState()
    data class Result(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}
