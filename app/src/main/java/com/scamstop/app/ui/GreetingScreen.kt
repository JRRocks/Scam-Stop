package com.scamstop.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamstop.app.GreetingExport
import com.scamstop.app.GreetingPrefs
import com.scamstop.app.R
import com.scamstop.app.ScamStopStore
import com.scamstop.app.VoiceGreeting
import com.scamstop.app.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GreetingScreen(store: ScamStopStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { GreetingPrefs(context) }
    val voice = remember { VoiceGreeting(context.applicationContext) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    val engine by voice.engine.collectAsStateWithLifecycle()
    val speaking by voice.speaking.collectAsStateWithLifecycle()
    val exporting by voice.exporting.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()

    val defaultText = stringResource(R.string.default_greeting)
    var text by rememberSaveable { mutableStateOf(prefs.text?.takeIf { it.isNotBlank() } ?: defaultText) }
    var export by remember { mutableStateOf(prefs.export) }
    var exportFailed by rememberSaveable { mutableStateOf(false) }
    val greeting = text.trim().ifEmpty { defaultText }

    // Keep what is typed, without writing on every keystroke.
    val latestText by rememberUpdatedState(text)
    LaunchedEffect(text) {
        delay(400)
        prefs.text = text
    }
    DisposableEffect(Unit) { onDispose { prefs.text = latestText } }

    LaunchedEffect(Unit) {
        val saved = export ?: return@LaunchedEffect
        if (!withContext(Dispatchers.IO) { VoiceGreeting.exists(context, saved.uri) }) {
            prefs.saveExport(null)
            export = null
        }
    }

    val ready = engine == VoiceGreeting.Engine.READY
    val unavailableMessage = stringResource(R.string.action_unavailable)
    val showUnavailable: () -> Unit = { scope.launch { snackbar.showSnackbar(unavailableMessage) } }
    val share: () -> Unit = { export?.let { if (!context.shareGreeting(it.uri)) showUnavailable() } }

    ScreenScaffold(title = stringResource(R.string.greeting_title), onBack = onBack, snackbarHostState = snackbar) { padding ->
        ScrollingContent(padding) {
            Text(
                stringResource(R.string.greeting_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.greeting_label)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            )

            when (engine) {
                VoiceGreeting.Engine.STARTING -> StatusLine(stringResource(R.string.tts_starting))
                VoiceGreeting.Engine.UNAVAILABLE ->
                    StatusLine(stringResource(R.string.tts_unavailable), MaterialTheme.colorScheme.error)
                VoiceGreeting.Engine.READY -> Unit
            }

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { if (speaking) voice.stop() else voice.speak(greeting) },
                    enabled = ready && !exporting,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painterResource(if (speaking) R.drawable.ic_ms_stop else R.drawable.ic_ms_play_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        stringResource(if (speaking) R.string.stop_greeting else R.string.speak_greeting),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
                OutlinedButton(
                    onClick = {
                        exportFailed = false
                        val exportedText = greeting
                        voice.export(exportedText, export?.uri) { uri ->
                            if (uri == null) {
                                exportFailed = true
                            } else {
                                val saved = GreetingExport(uri, System.currentTimeMillis(), exportedText)
                                prefs.saveExport(saved)
                                export = saved
                            }
                        }
                    },
                    enabled = ready && !exporting,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ms_download),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(stringResource(R.string.save_greeting), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }

            if (exporting) {
                LinearProgressIndicator(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
                StatusLine(stringResource(R.string.greeting_exporting))
            }
            if (exportFailed) StatusLine(stringResource(R.string.greeting_save_failed), MaterialTheme.colorScheme.error)

            export?.let { saved ->
                ExportedFile(
                    export = saved,
                    outdated = saved.text != greeting,
                    onShare = share,
                    onOpen = { if (!context.openGreeting(saved.uri)) showUnavailable() },
                )
            }

            SectionHeader(stringResource(R.string.install_title))
            InstallStep(
                number = 1,
                text = stringResource(if (export == null) R.string.install_step_share_missing else R.string.install_step_share),
            ) {
                FilledTonalButton(onClick = share, enabled = export != null) { Text(stringResource(R.string.share)) }
            }
            InstallStep(number = 2, text = stringResource(R.string.install_step_call)) {
                FilledTonalButton(onClick = { if (!context.openDialer()) showUnavailable() }) {
                    Text(stringResource(R.string.install_step_call_action))
                }
            }
            val installedMessage = stringResource(R.string.install_done)
            val undoLabel = stringResource(R.string.undo)
            InstallStep(number = 3, text = stringResource(R.string.install_step_record), done = settings.greetingInstalled) {
                if (settings.greetingInstalled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_ms_check_circle),
                            contentDescription = null,
                            tint = LocalStatusColors.current.okIcon,
                        )
                        Text(installedMessage, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    FilledTonalButton(onClick = {
                        store.setGreetingInstalled(true)
                        scope.launch {
                            if (snackbar.showSnackbar(installedMessage, undoLabel) == SnackbarResult.ActionPerformed) {
                                store.setGreetingInstalled(false)
                            }
                        }
                    }) { Text(stringResource(R.string.install_step_record_action)) }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ExportedFile(export: GreetingExport, outdated: Boolean, onShare: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val savedAt = if (DateUtils.isToday(export.timeMillis)) {
        stringResource(R.string.greeting_file_saved, context.formatTime(export.timeMillis))
    } else {
        stringResource(
            R.string.greeting_file_saved_on,
            DateUtils.formatDateTime(
                context,
                export.timeMillis,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            ),
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_ms_voicemail),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.greeting_file_name), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (outdated) stringResource(R.string.greeting_file_outdated) else savedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (outdated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(start = 28.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onShare, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(
                        painterResource(R.drawable.ic_ms_share),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(stringResource(R.string.share), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
            }
        }
    }
}

/** Numbered because the order matters: the file must reach the other device before the call. */
@Composable
private fun InstallStep(number: Int, text: String, done: Boolean = false, action: @Composable () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .background(
                    if (done) LocalStatusColors.current.ok else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = if (done) LocalStatusColors.current.onOk else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}
