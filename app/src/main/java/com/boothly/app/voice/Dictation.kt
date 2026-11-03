package com.boothly.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.CancellationReason
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import java.util.concurrent.Executors
import com.microsoft.cognitiveservices.speech.SpeechRecognizer as AzureRecognizer

/** Callbacks always arrive on the main thread. */
interface DictationListener {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(message: String)
    fun onStopped()
}

interface Dictation {
    val engineName: String
    fun start(language: String)
    fun stop()
    fun release()
}

data class VoiceLanguage(val label: String, val code: String)

val VOICE_LANGUAGES = listOf(
    VoiceLanguage("English", "en-US"),
    VoiceLanguage("中文", "zh-CN"),
    VoiceLanguage("العربية", "ar-SA"),
)

/** Microsoft Azure Speech – reachable from China, needs a key + region from the Azure portal. */
class AzureDictation(
    private val key: String,
    private val region: String,
    private val listener: DictationListener,
) : Dictation {
    override val engineName = "Microsoft"
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var config: SpeechConfig? = null
    private var audio: AudioConfig? = null
    private var recognizer: AzureRecognizer? = null

    override fun start(language: String) {
        worker.execute {
            try {
                val c = SpeechConfig.fromSubscription(key.trim(), region.trim())
                c.speechRecognitionLanguage = language
                val a = AudioConfig.fromDefaultMicrophoneInput()
                val r = AzureRecognizer(c, a)
                r.recognizing.addEventListener { _, e ->
                    val t = e.result.text ?: ""
                    main.post { listener.onPartial(t) }
                }
                r.recognized.addEventListener { _, e ->
                    if (e.result.reason == ResultReason.RecognizedSpeech) {
                        val t = e.result.text ?: ""
                        if (t.isNotBlank()) main.post { listener.onFinal(t) }
                    }
                }
                r.canceled.addEventListener { _, e ->
                    if (e.reason == CancellationReason.Error) {
                        val msg = e.errorDetails ?: "Microsoft speech error"
                        main.post { listener.onError(friendly(msg)) }
                    }
                }
                r.sessionStopped.addEventListener { _, _ -> main.post { listener.onStopped() } }
                config = c
                audio = a
                recognizer = r
                r.startContinuousRecognitionAsync().get()
            } catch (t: Throwable) {
                main.post { listener.onError("Microsoft speech could not start: ${t.message}") }
            }
        }
    }

    private fun friendly(msg: String): String = when {
        msg.contains("401") || msg.contains("Authentication", ignoreCase = true) ->
            "Microsoft key or region is wrong – check Settings"
        msg.contains("connection", ignoreCase = true) || msg.contains("network", ignoreCase = true) ->
            "No internet connection for Microsoft speech"
        else -> msg.take(200)
    }

    override fun stop() {
        worker.execute {
            try {
                recognizer?.stopContinuousRecognitionAsync()?.get()
            } catch (_: Throwable) {
            }
            main.post { listener.onStopped() }
        }
    }

    override fun release() {
        worker.execute {
            try {
                recognizer?.close()
                audio?.close()
                config?.close()
            } catch (_: Throwable) {
            }
            recognizer = null
            audio = null
            config = null
        }
        worker.shutdown()
    }
}

/** The phone's own voice typing engine (Huawei / Xiaomi / Oppo / Google…). Keeps listening until Stop. */
class PhoneDictation(
    private val ctx: Context,
    private val listener: DictationListener,
) : Dictation {
    override val engineName = "Phone"
    private var sr: SpeechRecognizer? = null
    private var listening = false
    private var language = "en-US"

    companion object {
        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun restart() {
        if (!listening) return
        try {
            sr?.startListening(intent())
        } catch (t: Throwable) {
            listening = false
            listener.onError("Voice engine error: ${t.message}")
            listener.onStopped()
        }
    }

    override fun start(language: String) {
        this.language = language
        if (!available(ctx)) {
            listener.onError("This phone has no voice typing engine. Add a Microsoft key in Settings.")
            listener.onStopped()
            return
        }
        val r = sr ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { sr = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                listener.onPartial(t)
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) listener.onFinal(t)
                restart()
            }

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart()
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {}
                    else -> {
                        listening = false
                        listener.onError(
                            when (error) {
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                                    "The phone's voice engine needs internet (or is blocked). Try Microsoft in Settings."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing"
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                    "The phone's voice engine doesn't support this language"
                                else -> "Voice engine error ($error)"
                            }
                        )
                        listener.onStopped()
                    }
                }
            }
        })
        listening = true
        restart()
    }

    override fun stop() {
        listening = false
        try {
            sr?.stopListening()
        } catch (_: Throwable) {
        }
        listener.onStopped()
    }

    override fun release() {
        listening = false
        try {
            sr?.destroy()
        } catch (_: Throwable) {
        }
        sr = null
    }
}
