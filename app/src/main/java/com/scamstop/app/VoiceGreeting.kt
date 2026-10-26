package com.scamstop.app

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Synthesizes the voicemail greeting. Apps cannot inject audio into a call, so the
 * greeting reaches callers by being recorded as the carrier voicemail greeting.
 */
internal class VoiceGreeting(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) onEngineReady()
    }

    @Volatile
    private var ready = false

    @Volatile
    private var onSaved: ((Boolean) -> Unit)? = null

    fun speak(text: String): Boolean {
        if (!ready) return false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, PREVIEW_ID)
        return true
    }

    fun saveToDownloads(text: String, onSaved: (Boolean) -> Unit): Boolean {
        if (!ready) return false
        this.onSaved = onSaved
        if (tts.synthesizeToFile(text, null, cacheFile(), EXPORT_ID) != TextToSpeech.SUCCESS) {
            finishExport(false)
        }
        return true
    }

    fun shutdown() = tts.shutdown()

    private fun onEngineReady() {
        tts.setLanguage(Locale.FRANCE)
        // On Google's engine the best offline voices are the neural ones.
        tts.voices
            ?.filter { it.locale.language == "fr" && it.locale.country == "FR" && !it.isNetworkConnectionRequired }
            ?.maxByOrNull { it.quality }
            ?.let { tts.setVoice(it) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == EXPORT_ID) finishExport(copyToDownloads())
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == EXPORT_ID) finishExport(false)
            }
        })
        ready = true
    }

    private fun copyToDownloads(): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-wav")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
            output.use { out -> cacheFile().inputStream().use { it.copyTo(out) } }
            true
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun finishExport(saved: Boolean) {
        val callback = onSaved ?: return
        onSaved = null
        mainHandler.post { callback(saved) }
    }

    private fun cacheFile() = File(context.cacheDir, FILE_NAME)

    private companion object {
        const val FILE_NAME = "annonce-scam-stop.wav"
        const val PREVIEW_ID = "preview"
        const val EXPORT_ID = "export"
    }
}
