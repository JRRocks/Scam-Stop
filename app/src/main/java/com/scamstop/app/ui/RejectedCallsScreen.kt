package com.scamstop.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamstop.app.AllowedNumber
import com.scamstop.app.R
import com.scamstop.app.RejectedCall
import com.scamstop.app.ScamStopStore
import com.scamstop.app.formatPhoneNumber
import com.scamstop.app.formatTime
import com.scamstop.app.groupByDay
import com.scamstop.app.matching
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun RejectedCallsScreen(store: ScamStopStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val calls by store.calls.collectAsStateWithLifecycle()
    val allowlist by store.allowlist.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<RejectedCall?>(null) }
    var allowTarget by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val clearedMessage = stringResource(R.string.calls_cleared)
    val undoLabel = stringResource(R.string.undo)
    val unavailableMessage = stringResource(R.string.action_unavailable)
    val showUnavailable: () -> Unit = { scope.launch { snackbar.showSnackbar(unavailableMessage) } }

    ScreenScaffold(
        title = stringResource(R.string.calls_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (calls.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_ms_more_vert), stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.calls_clear)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_ms_delete), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val cleared = store.clearCalls()
                                scope.launch {
                                    val result = snackbar.showSnackbar(clearedMessage, undoLabel, duration = SnackbarDuration.Long)
                                    if (result == SnackbarResult.ActionPerformed) store.restoreCalls(cleared)
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (calls.isEmpty()) {
            ScrollingContent(padding) {
                EmptyState(
                    R.drawable.ic_ms_history,
                    stringResource(R.string.calls_empty_title),
                    stringResource(R.string.calls_empty_text),
                )
            }
        } else {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val days = groupByDay(calls, zone)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(), contentPadding = padding) {
                    days.forEach { (day, dayCalls) ->
                        item(key = "day-$day") {
                            SectionHeader(
                                when (day) {
                                    today -> stringResource(R.string.day_today)
                                    today.minusDays(1) -> stringResource(R.string.day_yesterday)
                                    else -> DateUtils.formatDateTime(
                                        context,
                                        dayCalls.first().timeMillis,
                                        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE,
                                    )
                                },
                            )
                        }
                        items(dayCalls, key = { "${it.timeMillis}-${it.number}-${it.hashCode()}" }) { call ->
                            CallRow(call, allowlist.matching(call.number), onClick = { selected = call })
                        }
                    }
                }
            }
        }
    }

    selected?.let { call ->
        CallActionsSheet(
            call = call,
            allowed = allowlist.matching(call.number),
            onDismiss = { selected = null },
            onUnavailable = showUnavailable,
            onAllow = { allowTarget = it },
            onDisallow = { number ->
                val entry = allowlist.matching(number)
                store.disallow(number)
                if (entry != null) {
                    val message = resources.getString(R.string.removed_snackbar, entry.label.ifBlank { context.formatPhoneNumber(number) })
                    scope.launch {
                        if (snackbar.showSnackbar(message, undoLabel) == SnackbarResult.ActionPerformed) {
                            store.allow(entry.number, entry.label)
                        }
                    }
                }
            },
        )
    }

    allowTarget?.let { number ->
        AllowNumberDialog(
            number = number,
            onDismiss = { allowTarget = null },
            onConfirm = { label ->
                store.allow(number, label)
                allowTarget = null
                val name = label.ifBlank { context.formatPhoneNumber(number) }
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.allowed_snackbar, name)) }
            },
        )
    }
}

@Composable
private fun CallRow(call: RejectedCall, allowed: AllowedNumber?, onClick: () -> Unit) {
    val context = LocalContext.current
    val time = context.formatTime(call.timeMillis)
    SettingRow(
        title = context.formatPhoneNumber(call.number),
        supporting = when {
            allowed == null -> stringResource(R.string.call_rejected_at, time)
            allowed.label.isBlank() -> stringResource(R.string.call_allowed_since_unnamed)
            else -> stringResource(R.string.call_allowed_since, allowed.label)
        },
        icon = if (allowed == null) R.drawable.ic_ms_phone_disabled else R.drawable.ic_ms_how_to_reg,
        onClickLabel = stringResource(R.string.more_options),
        onClick = onClick,
        trailing = { Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallActionsSheet(
    call: RejectedCall,
    allowed: AllowedNumber?,
    onDismiss: () -> Unit,
    onUnavailable: () -> Unit,
    onAllow: (String) -> Unit,
    onDisallow: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: (() -> Unit) -> Unit = { after ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            after()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            context.formatPhoneNumber(call.number),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(R.string.call_rejected_at, context.formatTime(call.timeMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        )
        val number = call.number
        if (number.isNullOrBlank()) {
            Text(
                stringResource(R.string.hidden_number_note),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            SettingRow(stringResource(R.string.action_call), icon = R.drawable.ic_ms_call, onClick = {
                close { if (!context.dial(number)) onUnavailable() }
            })
            SettingRow(stringResource(R.string.action_sms), icon = R.drawable.ic_ms_sms, onClick = {
                close { if (!context.sendSms(number)) onUnavailable() }
            })
            SettingRow(stringResource(R.string.action_add_contact), icon = R.drawable.ic_ms_person_add, onClick = {
                close { if (!context.addToContacts(number)) onUnavailable() }
            })
            if (allowed == null) {
                SettingRow(
                    stringResource(R.string.action_allow),
                    supporting = stringResource(R.string.action_allow_text),
                    icon = R.drawable.ic_ms_how_to_reg,
                    onClick = { close { onAllow(number) } },
                )
            } else {
                SettingRow(
                    stringResource(R.string.action_disallow),
                    icon = R.drawable.ic_ms_person_remove,
                    onClick = { close { onDisallow(number) } },
                )
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
