package com.scamstop.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scamstop.app.CallDirection
import com.scamstop.app.R
import com.scamstop.app.ScreeningTrace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Debug builds only: the calls Android handed to the screening service on this phone. */
@Composable
internal fun DebugDiagnosticsRow() {
    var open by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.debug_diag_title))
    SettingRow(
        title = stringResource(R.string.debug_diag_title),
        supporting = stringResource(R.string.debug_diag_row_text),
        icon = R.drawable.ic_ms_info,
        onClick = { open = true },
        trailing = { ChevronIcon() },
    )
    if (open) DebugDiagnosticsSheet(onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugDiagnosticsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(ScreeningTrace.events(context)) }
    val copied = stringResource(R.string.debug_diag_copied)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.debug_diag_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.debug_diag_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            Text(
                stringResource(
                    R.string.debug_diag_counts,
                    events.count { it.direction == CallDirection.OUTGOING },
                    events.count { it.direction == CallDirection.INCOMING },
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.debug_diag_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            events.forEach { event ->
                SettingRow(title = eventTitle(event), supporting = eventText(event))
            }
            if (events.isNotEmpty()) {
                TextButton(
                    onClick = {
                        context.copyText(events.joinToString("\n") { context.reportLine(it) })
                        // Android 13 and later confirm a copy on their own.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.debug_diag_copy)) }
                TextButton(
                    onClick = {
                        ScreeningTrace.clear(context)
                        events = emptyList()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.debug_diag_clear)) }
            }
        }
    }
}

@Composable
private fun eventTitle(event: ScreeningTrace.Event): String = LocalContext.current.eventTitle(event)

@Composable
private fun eventText(event: ScreeningTrace.Event): String = LocalContext.current.eventText(event)

private fun Context.eventTitle(event: ScreeningTrace.Event): String = getString(
    R.string.debug_diag_event_title,
    SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(event.timeMillis)),
    getString(
        when (event.direction) {
            CallDirection.OUTGOING -> R.string.debug_diag_outgoing
            CallDirection.INCOMING -> R.string.debug_diag_incoming
            CallDirection.UNKNOWN -> R.string.debug_diag_other
        },
    ),
    event.number,
)

private fun Context.eventText(event: ScreeningTrace.Event): String = getString(
    R.string.debug_diag_event_text,
    getString(
        when (event.result) {
            ScreeningTrace.Result.OUTGOING_REMEMBERED -> R.string.debug_diag_outgoing_remembered
            ScreeningTrace.Result.OUTGOING_NOT_REMEMBERED -> R.string.debug_diag_outgoing_not_remembered
            ScreeningTrace.Result.NOT_A_CALL -> R.string.debug_diag_not_a_call
            ScreeningTrace.Result.UNFILTERED -> R.string.debug_diag_unfiltered
            ScreeningTrace.Result.CALLED_BACK -> R.string.debug_diag_called_back
            ScreeningTrace.Result.SECOND_CALL -> R.string.debug_diag_second_call
            ScreeningTrace.Result.REJECTED -> R.string.debug_diag_rejected
            ScreeningTrace.Result.REJECTED_MAY_CALL_AGAIN -> R.string.debug_diag_rejected_may_call_again
        },
    ),
    ScreeningTrace.verificationName(event.verification),
    getString(if (event.onFrenchNetwork) R.string.debug_diag_french_network else R.string.debug_diag_foreign_network),
    event.answerMillis,
)

/** One line of the copied report: numbers are stored masked, so it may be pasted anywhere. */
private fun Context.reportLine(event: ScreeningTrace.Event): String = eventTitle(event) + " · " + eventText(event)

private fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.debug_diag_title), text))
}
