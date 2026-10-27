package com.scamstop.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scamstop.app.GreetingExport
import com.scamstop.app.GreetingPrefs
import com.scamstop.app.VoiceGreeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the speech engine for the greeting screen, so a rotation neither restarts the
 * engine nor drops an export in progress; the engine stops when the screen is left.
 */
internal class GreetingViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = GreetingPrefs(application)
    val voice = VoiceGreeting(application)

    private val _export = MutableStateFlow(prefs.export)
    val export: StateFlow<GreetingExport?> = _export.asStateFlow()

    private val _exportFailed = MutableStateFlow(false)
    val exportFailed: StateFlow<Boolean> = _exportFailed.asStateFlow()

    var savedText: String?
        get() = prefs.text
        set(value) {
            prefs.text = value
        }

    init {
        viewModelScope.launch {
            val saved = _export.value ?: return@launch
            if (!withContext(Dispatchers.IO) { VoiceGreeting.exists(application, saved.uri) }) {
                prefs.saveExport(null)
                _export.value = null
            }
        }
    }

    fun createFile(text: String) {
        _exportFailed.value = false
        voice.export(text, _export.value?.uri) { uri ->
            if (uri == null) {
                _exportFailed.value = true
            } else {
                viewModelScope.launch {
                    val app = getApplication<Application>()
                    val name = withContext(Dispatchers.IO) { VoiceGreeting.displayName(app, uri) } ?: VoiceGreeting.FILE_NAME
                    val saved = GreetingExport(uri, name, System.currentTimeMillis(), text)
                    prefs.saveExport(saved)
                    _export.value = saved
                }
            }
        }
    }

    override fun onCleared() = voice.shutdown()
}
