package com.scamstop.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamstop.app.AllowedNumber
import com.scamstop.app.PhoneNumbers
import com.scamstop.app.R
import com.scamstop.app.ScamStopStore
import com.scamstop.app.formatPhoneNumber
import kotlinx.coroutines.launch

@Composable
internal fun AllowlistScreen(store: ScamStopStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val allowlist by store.allowlist.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingNumber?.let { number -> allowlist.firstOrNull { it.number == number } }
    val undoLabel = stringResource(R.string.undo)

    ScreenScaffold(
        title = stringResource(R.string.allowlist_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            // The content overload keeps the label in the button's semantics; the text/icon
            // overload clears it, and TalkBack would only say "Bouton". Blue: the screen's main action.
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(painterResource(R.drawable.ic_ms_add), contentDescription = null)
                Text(stringResource(R.string.allowlist_add), modifier = Modifier.padding(start = 12.dp))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                // Room under the last row for the floating button.
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.allowlist_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (allowlist.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            R.drawable.ic_ms_how_to_reg,
                            stringResource(R.string.allowlist_empty_title),
                            stringResource(R.string.allowlist_empty_text),
                        )
                    }
                }
                itemsIndexed(allowlist, key = { index, entry -> "$index-${entry.number}" }) { _, entry ->
                    val formatted = context.formatPhoneNumber(entry.number)
                    SettingRow(
                        title = entry.label.ifBlank { formatted },
                        supporting = if (entry.label.isBlank()) null else formatted,
                        icon = R.drawable.ic_ms_how_to_reg,
                        onClick = { editingNumber = entry.number },
                        trailing = {
                            Icon(
                                painterResource(R.drawable.ic_ms_edit),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }

    if (adding) {
        AddNumberDialog(
            onDismiss = { adding = false },
            onConfirm = { number, label ->
                store.allow(number, label)
                adding = false
                val name = label.ifBlank { context.formatPhoneNumber(number) }
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.allowed_snackbar, name)) }
            },
        )
    }

    editing?.let { entry ->
        EditNumberDialog(
            entry = entry,
            onDismiss = { editingNumber = null },
            onSave = { label ->
                store.allow(entry.number, label)
                editingNumber = null
            },
            onRemove = {
                store.disallow(entry.number)
                editingNumber = null
                val name = entry.label.ifBlank { context.formatPhoneNumber(entry.number) }
                scope.launch {
                    if (snackbar.showSnackbar(resources.getString(R.string.removed_snackbar, name), undoLabel) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        store.allow(entry.number, entry.label)
                    }
                }
            },
        )
    }
}

/** Confirms allowing a number seen in the rejected calls, with an optional name. */
@Composable
internal fun AllowNumberDialog(number: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_ms_how_to_reg), contentDescription = null) },
        title = { Text(stringResource(R.string.allow_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.allow_dialog_text, context.formatPhoneNumber(number)))
                NameField(label, onValueChange = { label = it }, onDone = { onConfirm(label.trim()) })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(label.trim()) }) { Text(stringResource(R.string.allow_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AddNumberDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (PhoneNumbers.isDialable(number)) onConfirm(number.trim(), label.trim()) else showError = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.allowlist_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = {
                        number = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.number_label)) },
                    isError = showError,
                    supportingText = if (showError) ({ Text(stringResource(R.string.number_error)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                NameField(label, onValueChange = { label = it }, onDone = submit)
            }
        },
        confirmButton = { TextButton(onClick = submit) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EditNumberDialog(entry: AllowedNumber, onDismiss: () -> Unit, onSave: (String) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var label by rememberSaveable(entry.number) { mutableStateOf(entry.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(context.formatPhoneNumber(entry.number), style = MaterialTheme.typography.titleMedium)
                NameField(label, onValueChange = { label = it }, onDone = { onSave(label.trim()) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.name_label)) },
        placeholder = { Text(stringResource(R.string.name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

