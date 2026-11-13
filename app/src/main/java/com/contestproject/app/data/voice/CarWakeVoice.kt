package com.contestproject.app.data.voice

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * Pleos TTS SDK를 ON_DEVICE 모드로 쓴다(앱 등록·네트워크 불필요, 졸음쉼터 통신 음영 대비).
 * 초기화에 실패하면 Android 기본 TTS로 대신한다.
 * 깨우는 소리라 AudioAttributes는 USAGE_ALARM, 재생 동안만 오디오 포커스를 잡는다.
 */
class CarWakeVoice(context: Context) : WakeVoice {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(alarmAttributes)
        .build()

    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null
    private var fallbackReady = false
    private var pendingText: String? = null
    private var lastText: String? = null

    private val pleosEvents = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = abandonFocus()
        override fun onStop() = abandonFocus()
        override fun onError(type: ErrorType) {
            Log.w(TAG, "pleos tts error $type")
            abandonFocus()
            // 기기 내 엔진이 없거나 한국어를 못 읽는 차(에뮬레이터 포함)는 이후 Android TTS로 말한다.
            if (type in ENGINE_UNAVAILABLE) mainHandler.post {
                pendingText = lastText
                useFallback()
            }
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override fun prepare() {
        if (pleos != null || fallback != null) return
        try {
            val tts = PleosTts(appContext, Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    mainHandler.post {
                        runCatching {
                            tts.setLocale(Locale.KOREAN)
                            tts.setAudioAttributes(alarmAttributes)
                            tts.addEventListener(pleosEvents)
                        }
                        pleosReady = true
                        flushPending()
                    }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "pleos tts init failed $type, using android tts")
                    mainHandler.post { useFallback() }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "pleos tts unavailable", t)
            useFallback()
        }
    }

    private fun useFallback() {
        pleos?.let { runCatching { it.release() } }
        pleos = null
        pleosReady = false
        if (fallback != null) return
        fallback = AndroidTts(appContext) { status ->
            mainHandler.post {
                if (status == AndroidTts.SUCCESS) {
                    fallback?.language = Locale.KOREAN
                    fallback?.setAudioAttributes(alarmAttributes)
                    fallbackReady = true
                    flushPending()
                } else {
                    Log.w(TAG, "android tts init failed $status")
                }
            }
        }
    }

    private fun flushPending() {
        val text = pendingText ?: return
        pendingText = null
        speak(text)
    }

    override fun announce(text: String, level: Int) {
        mainHandler.post {
            chime(level)
            // 알림음이 끝난 뒤 말하도록 살짝 늦춘다.
            mainHandler.postDelayed({ speak(text) }, CHIME_MS + 150)
        }
    }

    private fun speak(text: String) {
        lastText = text
        requestFocus()
        when {
            pleosReady -> runCatching { pleos?.speak(text, true) }.onFailure {
                Log.w(TAG, "pleos speak failed", it)
                pendingText = text
                useFallback()
            }
            fallbackReady -> fallback?.speak(text, AndroidTts.QUEUE_FLUSH, Bundle(), UTTERANCE_ID)
            else -> pendingText = text
        }
    }

    private fun chime(level: Int) {
        val volume = (BASE_VOLUME + level * VOLUME_STEP).coerceAtMost(ToneGenerator.MAX_VOLUME)
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, volume)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, CHIME_MS.toInt())
            mainHandler.postDelayed({ tone.release() }, CHIME_MS + 100)
        }.onFailure { Log.w(TAG, "chime failed", it) }
    }

    override fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        pendingText = null
        runCatching { pleos?.stop() }
        runCatching { fallback?.stop() }
        abandonFocus()
    }

    override fun release() {
        stop()
        runCatching { pleos?.removeEventListener(pleosEvents); pleos?.release() }
        runCatching { fallback?.shutdown() }
        pleos = null
        fallback = null
        pleosReady = false
        fallbackReady = false
    }

    private fun requestFocus() {
        runCatching { audioManager.requestAudioFocus(focusRequest) }
    }

    private fun abandonFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    private companion object {
        const val TAG = "WakeVoice"
        const val UTTERANCE_ID = "shimpyo-wake"
        const val CHIME_MS = 600L
        const val BASE_VOLUME = 50
        const val VOLUME_STEP = 10
        val ENGINE_UNAVAILABLE = setOf(
            ErrorType.ON_DEVICE_ENGINE_ERROR,
            ErrorType.UNAVAILABLE_LANGUAGE,
            ErrorType.NOT_SUPPORTED_API,
        )
    }
}
