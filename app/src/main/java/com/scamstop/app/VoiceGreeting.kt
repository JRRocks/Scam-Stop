package com.scamstop.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/**
 * Synthesizes the voicemail greeting. Apps cannot inject audio into a call, so the
 * greeting reaches callers by being recorded as the carrier voicemail greeting.
 */
internal class VoiceGreeting(private val context: Context) {
    enum class Engine { STARTING, READY, UNAVAILABLE }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _engine = MutableStateFlow(Engine.STARTING)
    val engine: StateFlow<Engine> = _engine.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /** The engine works but has no French voice: it would read the greeting with another accent. */
    private val _frenchMissing = MutableStateFlow(false)
    val frenchMissing: StateFlow<Boolean> = _frenchMissing.asStateFlow()

    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) onEngineReady() else _engine.value = Engine.UNAVAILABLE
    }

    @Volatile
    private var pendingExport: PendingExport? = null

    private class PendingExport(val target: Uri?, val onDone: (Uri?) -> Unit)

    /** Id of the preview being played; callbacks for an older, flushed preview are ignored. */
    @Volatile
    private var previewId: String? = null
    private var previewCount = 0

    fun speak(text: String): Boolean {
        // QUEUE_FLUSH would also discard an export being rendered.
        if (_engine.value != Engine.READY || pendingExport != null) return false
        val id = "$PREVIEW_PREFIX${++previewCount}"
        previewId = id
        _speaking.value = true
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            endPreview(id)
            return false
        }
        return true
    }

    fun stop() {
        if (pendingExport != null) return
        previewId = null
        tts.stop()
        _speaking.value = false
    }

    private fun endPreview(utteranceId: String?) {
        if (utteranceId != null && utteranceId == previewId) {
            previewId = null
            _speaking.value = false
        }
    }

    /**
     * Writes the greeting to Downloads. [previous] is the file this app exported last time:
     * it is overwritten when it still exists, so repeated exports do not pile up copies.
     */
    fun export(text: String, previous: Uri?, onDone: (Uri?) -> Unit): Boolean {
        if (_engine.value != Engine.READY || pendingExport != null) return false
        // A preview still playing would hold the export back until it ends.
        if (previewId != null) {
            previewId = null
            tts.stop()
            _speaking.value = false
        }
        pendingExport = PendingExport(previous, onDone)
        _exporting.value = true
        if (tts.synthesizeToFile(text, null, cacheFile(), EXPORT_ID) != TextToSpeech.SUCCESS) {
            finishExport(null)
        }
        return true
    }

    fun shutdown() {
        pendingExport = null
        tts.shutdown()
    }

    /**
     * Picks the French voice again: the user may just have installed it from the
     * "Installer la voix" button. Skipped while something is playing or being written.
     */
    fun refreshLanguage() {
        if (_engine.value == Engine.READY && pendingExport == null && previewId == null) selectFrenchVoice()
    }

    private fun selectFrenchVoice() {
        _frenchMissing.value = tts.setLanguage(Locale.FRANCE) < TextToSpeech.LANG_AVAILABLE
        // On Google's engine the best offline voices are the neural ones.
        tts.voices
            ?.filter { it.locale.language == "fr" && it.locale.country == "FR" && !it.isNetworkConnectionRequired }
            ?.maxByOrNull { it.quality }
            ?.let { tts.setVoice(it) }
    }

    private fun onEngineReady() {
        selectFrenchVoice()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != EXPORT_ID) return endPreview(utteranceId)
                // Nothing to copy when the screen gave up on this export (shutdown).
                val export = pendingExport ?: return
                finishExport(copyToDownloads(export.target))
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == EXPORT_ID) finishExport(null) else endPreview(utteranceId)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == EXPORT_ID) finishExport(null) else endPreview(utteranceId)
            }
        })
        _engine.value = Engine.READY
    }

    private fun copyToDownloads(previous: Uri?): Uri? {
        if (previous != null && overwrite(previous)) return previous
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
            output.use { out -> cacheFile().inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun overwrite(uri: Uri): Boolean = try {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException()
        output.use { out -> cacheFile().inputStream().use { it.copyTo(out) } }
        true
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun finishExport(uri: Uri?) {
        val export = pendingExport ?: return
        pendingExport = null
        mainHandler.post {
            _exporting.value = false
            export.onDone(uri)
        }
    }

    private fun cacheFile() = File(context.cacheDir, FILE_NAME)

    companion object {
        const val MIME_TYPE = "audio/x-wav"
        const val FILE_NAME = "annonce-scam-stop.wav"
        private const val PREVIEW_PREFIX = "preview-"
        private const val EXPORT_ID = "export"

        /** MediaStore may have renamed the file ("annonce-scam-stop (1).wav") to avoid a clash. */
        fun displayName(context: Context, uri: Uri): String? = try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

        /** False when the user deleted the file or it belongs to a previous install. */
        fun exists(context: Context, uri: Uri): Boolean = try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}

/** Greeting text and last export, in the preferences file the first version already used. */
internal class GreetingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var text: String?
        get() = prefs.getString(TEXT_KEY, null)
        set(value) = prefs.edit { putString(TEXT_KEY, value) }

    val export: GreetingExport?
        get() = prefs.getString(EXPORT_URI_KEY, null)?.let {
            GreetingExport(
                uri = it.toUri(),
                name = prefs.getString(EXPORT_NAME_KEY, null) ?: VoiceGreeting.FILE_NAME,
                timeMillis = prefs.getLong(EXPORT_TIME_KEY, 0L),
                text = prefs.getString(EXPORT_TEXT_KEY, "").orEmpty(),
            )
        }

    fun saveExport(export: GreetingExport?) {
        prefs.edit {
            if (export == null) {
                remove(EXPORT_URI_KEY)
                remove(EXPORT_NAME_KEY)
                remove(EXPORT_TIME_KEY)
                remove(EXPORT_TEXT_KEY)
            } else {
                putString(EXPORT_URI_KEY, export.uri.toString())
                putString(EXPORT_NAME_KEY, export.name)
                putLong(EXPORT_TIME_KEY, export.timeMillis)
                putString(EXPORT_TEXT_KEY, export.text)
            }
        }
    }

    private companion object {
        // Activity.getPreferences() of the first version wrote to "MainActivity.xml".
        const val PREFERENCES_NAME = "MainActivity"
        const val TEXT_KEY = "greeting"
        const val EXPORT_URI_KEY = "export_uri"
        const val EXPORT_NAME_KEY = "export_name"
        const val EXPORT_TIME_KEY = "export_time"
        const val EXPORT_TEXT_KEY = "export_text"
    }
}

internal data class GreetingExport(val uri: Uri, val name: String, val timeMillis: Long, val text: String)
