package com.scamstop.app.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamstop.app.GreetingPrefs
import com.scamstop.app.PauseOption
import com.scamstop.app.ProtectionKind
import com.scamstop.app.ProtectionStatus
import com.scamstop.app.ProtectionTileService
import com.scamstop.app.R
import com.scamstop.app.RejectedCallNotifications
import com.scamstop.app.appNotificationsEnabled
import com.scamstop.app.ScamStopStore
import com.scamstop.app.SetupProgress
import com.scamstop.app.formatPauseEnd
import com.scamstop.app.formatTime
import com.scamstop.app.pauseEnd
import com.scamstop.app.protectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
internal fun HomeScreen(
    store: ScamStopStore,
    system: SystemState,
    pauseSheetRequested: Boolean,
    onPauseSheetRequestHandled: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenGreeting: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by store.settings.collectAsStateWithLifecycle()
    val calls by store.calls.collectAsStateWithLifecycle()
    val allowlist by store.allowlist.collectAsStateWithLifecycle()
    val greetingPrefs = remember { GreetingPrefs(context) }
    var greetingSaved by remember { mutableStateOf(greetingPrefs.export != null) }

    // Re-evaluated when the app resumes and when a pause runs out.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = System.currentTimeMillis()
        greetingSaved = greetingPrefs.export != null
    }
    LaunchedEffect(settings) { now = System.currentTimeMillis() }
    LaunchedEffect(settings.pausedUntil, now) {
        val remaining = settings.pausedUntil - System.currentTimeMillis()
        if (remaining > 0) {
            delay(remaining + 250)
            now = System.currentTimeMillis()
        }
    }
    val status = protectionStatus(system.role, settings.isEnabled, settings.pausedUntil, now)

    val roleDenied = stringResource(R.string.role_denied)
    val openSettings = stringResource(R.string.open_settings)
    // After "Ne plus demander", Android answers at once without showing anything:
    // point to the default apps settings, where the role can still be given.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        system.refresh()
        if (!system.roleHeld) {
            scope.launch {
                if (snackbar.showSnackbar(roleDenied, openSettings, duration = SnackbarDuration.Long) ==
                    SnackbarResult.ActionPerformed
                ) {
                    context.tryStart(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }
        }
    }
    val requestRole: () -> Unit = {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true) {
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }
    val enableNotifications = rememberNotificationToggle(store, system, settings.notificationPermissionAsked, snackbar)

    var showPauseSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pauseSheetRequested) {
        if (pauseSheetRequested) {
            if (system.roleHeld) showPauseSheet = true
            onPauseSheetRequestHandled()
        }
    }

    ScreenScaffold(title = stringResource(R.string.app_name), onBack = null, snackbarHostState = snackbar) { padding ->
        ScrollingContent(padding) {
            StatusBanner(status, now, onGrantRole = requestRole, onResume = { store.resume() })

            val setup = SetupProgress(
                roleDone = system.roleHeld,
                notificationsDone = settings.notificationsDecided || system.notificationsAllowed,
                greetingDone = settings.greetingInstalled,
            )
            if (!setup.isComplete && !settings.setupHidden && status.kind != ProtectionKind.UNAVAILABLE) {
                SetupCard(
                    progress = setup,
                    onGrantRole = requestRole,
                    onEnableNotifications = { enableNotifications(true) },
                    greetingAction = stringResource(if (greetingSaved) R.string.setup_greeting_open else R.string.setup_greeting_action),
                    onOpenGreeting = onOpenGreeting,
                    onHide = { store.hideSetup() },
                )
            }

            if (system.roleHeld) {
                SwitchRow(
                    title = stringResource(R.string.block_switch_title),
                    supporting = stringResource(R.string.block_switch_text),
                    icon = R.drawable.ic_ms_verified_user,
                    checked = settings.isEnabled,
                    onCheckedChange = { store.setEnabled(it) },
                )
                when (status.kind) {
                    ProtectionKind.ACTIVE -> {
                        RowDivider()
                        SettingRow(
                            title = stringResource(R.string.pause_title),
                            supporting = stringResource(R.string.pause_text),
                            icon = R.drawable.ic_ms_pause,
                            onClick = { showPauseSheet = true },
                        )
                    }
                    ProtectionKind.PAUSED -> {
                        RowDivider()
                        SettingRow(
                            title = stringResource(R.string.pause_change_title),
                            supporting = stringResource(R.string.pause_ends, context.formatPauseEnd(status.pausedUntil, now)),
                            icon = R.drawable.ic_ms_timer,
                            onClick = { showPauseSheet = true },
                        )
                    }
                    else -> Unit
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !settings.tileAdded) {
                    RowDivider()
                    SettingRow(
                        title = stringResource(R.string.tile_add_title),
                        supporting = stringResource(R.string.tile_add_text),
                        icon = R.drawable.ic_ms_add,
                        onClick = { requestAddTile(context, store) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.section_calls))
            SettingRow(
                title = stringResource(R.string.calls_title),
                supporting = callsSummary(context, calls.map { it.timeMillis }, now),
                icon = R.drawable.ic_ms_history,
                onClick = onOpenCalls,
                trailing = { ChevronIcon() },
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.allowlist_title),
                supporting = if (allowlist.isEmpty()) {
                    stringResource(R.string.allowlist_none)
                } else {
                    pluralStringResource(R.plurals.allowlist_count, allowlist.size, allowlist.size)
                },
                icon = R.drawable.ic_ms_how_to_reg,
                onClick = onOpenAllowlist,
                trailing = { ChevronIcon() },
            )
            RowDivider()
            val hiddenFallback = stringResource(R.string.hidden_fallback)
            SettingRow(
                title = stringResource(R.string.hidden_title),
                supporting = stringResource(R.string.hidden_text),
                icon = R.drawable.ic_ms_info,
                onClick = {
                    if (!context.openBlockedNumbers()) scope.launch { snackbar.showSnackbar(hiddenFallback) }
                },
                trailing = {
                    Icon(
                        painterResource(R.drawable.ic_ms_open_in_new),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SectionHeader(stringResource(R.string.section_voicemail))
            SettingRow(
                title = stringResource(R.string.greeting_title),
                supporting = stringResource(
                    when {
                        settings.greetingInstalled -> R.string.greeting_row_installed
                        greetingSaved -> R.string.greeting_row_saved
                        else -> R.string.greeting_row_todo
                    },
                ),
                icon = R.drawable.ic_ms_voicemail,
                onClick = onOpenGreeting,
                trailing = { ChevronIcon() },
            )

            SectionHeader(stringResource(R.string.section_notifications))
            val notifyChecked = settings.notify && system.notificationsAllowed
            // "Blocked" only once Android was asked; a fresh install has simply not chosen yet.
            val blocked = settings.notify && (settings.notificationPermissionAsked || system.notificationPermission)
            SwitchRow(
                title = stringResource(R.string.notify_title),
                supporting = stringResource(
                    when {
                        notifyChecked -> R.string.notify_text_on
                        blocked -> R.string.notify_text_blocked
                        else -> R.string.notify_text_off
                    },
                ),
                icon = R.drawable.ic_ms_notifications,
                checked = notifyChecked,
                onCheckedChange = enableNotifications,
            )
        }
    }

    if (showPauseSheet) {
        PauseSheet(onDismiss = { showPauseSheet = false }, onPause = { end -> store.pauseUntil(end) })
    }
}

private fun callsSummary(context: Context, times: List<Long>, now: Long): String {
    if (times.isEmpty()) return context.getString(R.string.calls_none)
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val todayCount = times.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today }
    if (todayCount > 0) return context.resources.getQuantityString(R.plurals.calls_today, todayCount, todayCount)
    val last = times.max()
    val day = android.text.format.DateUtils.formatDateTime(
        context,
        last,
        android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_ABBREV_MONTH,
    )
    return context.getString(R.string.calls_last, "$day, ${context.formatTime(last)}")
}

/**
 * Turning notifications on asks Android for the permission once; when Android will no
 * longer show that prompt, or notifications are blocked in settings, it opens those settings.
 */
@Composable
private fun rememberNotificationToggle(
    store: ScamStopStore,
    system: SystemState,
    permissionAsked: Boolean,
    snackbar: SnackbarHostState,
): (Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refused = stringResource(R.string.notifications_refused)
    val openSettings = stringResource(R.string.open_settings)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        system.refresh()
        val activity = context.findActivity()
        // Refused for good (or before this version tracked it): Android will not ask again.
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            scope.launch {
                if (snackbar.showSnackbar(refused, openSettings) == SnackbarResult.ActionPerformed) {
                    context.openAppNotificationSettings()
                }
            }
        }
    }
    return { enable ->
        store.setNotify(enable)
        if (enable) {
            val activity = context.findActivity()
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !system.notificationPermission -> {
                    val blocked = permissionAsked && activity != null &&
                        !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    if (blocked) {
                        context.openAppNotificationSettings()
                    } else {
                        store.markNotificationPermissionAsked()
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                !context.appNotificationsEnabled() -> context.openAppNotificationSettings()
                RejectedCallNotifications.isChannelBlocked(context) -> context.openRejectedCallsChannelSettings()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context, store: ScamStopStore) {
    context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
        ProtectionTileService.component(context),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_ms_verified_user),
        context.mainExecutor,
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        ) {
            store.setTileAdded(true)
        }
    }
}

private data class BannerStyle(
    val container: Color,
    val content: Color,
    @param:DrawableRes val icon: Int,
    val title: String,
    val text: String,
)

@Composable
private fun StatusBanner(status: ProtectionStatus, now: Long, onGrantRole: () -> Unit, onResume: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val state = LocalStatusColors.current
    val style = when (status.kind) {
        ProtectionKind.ACTIVE -> BannerStyle(
            state.ok, state.onOk, R.drawable.ic_ms_verified_user,
            stringResource(R.string.status_active_title), stringResource(R.string.status_active_text),
        )
        ProtectionKind.PAUSED -> BannerStyle(
            state.warn, state.onWarn, R.drawable.ic_ms_pause_circle,
            stringResource(R.string.status_paused_title, context.formatPauseEnd(status.pausedUntil, now)),
            stringResource(R.string.status_paused_text),
        )
        ProtectionKind.OFF -> BannerStyle(
            colors.errorContainer, colors.onErrorContainer, R.drawable.ic_ms_gpp_bad,
            stringResource(R.string.status_off_title), stringResource(R.string.status_off_text),
        )
        ProtectionKind.NEEDS_ROLE -> BannerStyle(
            colors.errorContainer, colors.onErrorContainer, R.drawable.ic_ms_gpp_bad,
            stringResource(R.string.status_role_title), stringResource(R.string.status_role_text),
        )
        ProtectionKind.UNAVAILABLE -> BannerStyle(
            colors.surfaceContainerHigh, colors.onSurface, R.drawable.ic_ms_gpp_maybe,
            stringResource(R.string.status_unavailable_title), stringResource(R.string.status_unavailable_text),
        )
    }
    Surface(
        color = style.container,
        contentColor = style.content,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(style.icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(style.title, style = MaterialTheme.typography.titleMedium)
                    Text(style.text, style = MaterialTheme.typography.bodyMedium)
                }
                when (status.kind) {
                    ProtectionKind.NEEDS_ROLE -> BannerButton(stringResource(R.string.grant_call_screening), onGrantRole)
                    ProtectionKind.PAUSED -> BannerButton(stringResource(R.string.resume_now), onResume)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun BannerButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(label) }
}

@Composable
private fun SetupCard(
    progress: SetupProgress,
    onGrantRole: () -> Unit,
    onEnableNotifications: () -> Unit,
    greetingAction: String,
    onOpenGreeting: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.setup_title, progress.doneCount, progress.total),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onHide) { Text(stringResource(R.string.setup_hide)) }
            }
            LinearProgressIndicator(
                progress = { progress.doneCount / progress.total.toFloat() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
            )
            SetupItem(progress.roleDone, stringResource(R.string.setup_role), stringResource(R.string.setup_role_action), onGrantRole)
            SetupItem(
                progress.notificationsDone,
                stringResource(R.string.setup_notifications),
                stringResource(R.string.setup_notifications_action),
                onEnableNotifications,
            )
            SetupItem(
                progress.greetingDone,
                stringResource(R.string.setup_greeting),
                greetingAction,
                onOpenGreeting,
            )
        }
    }
}

@Composable
private fun SetupItem(done: Boolean, title: String, action: String, onAction: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Icon(
                painterResource(if (done) R.drawable.ic_ms_check_circle else R.drawable.ic_ms_radio_button_unchecked),
                contentDescription = if (done) stringResource(R.string.setup_done) else null,
                tint = if (done) LocalStatusColors.current.okIcon else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (done) null else ({ TextButton(onClick = onAction) { Text(action) } }),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseSheet(onDismiss: () -> Unit, onPause: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = remember { ZonedDateTime.now() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Scrolls at large font sizes, where the options no longer fit.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.pause_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.pause_sheet_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            val nowMillis = start.toInstant().toEpochMilli()
            PauseOption.entries.forEach { option ->
                val endLabel = context.formatPauseEnd(pauseEnd(option, start).toInstant().toEpochMilli(), nowMillis)
                val title = when (option) {
                    PauseOption.MINUTES_15 -> stringResource(R.string.pause_15_minutes)
                    PauseOption.HOUR_1 -> stringResource(R.string.pause_1_hour)
                    PauseOption.HOURS_3 -> stringResource(R.string.pause_3_hours)
                    PauseOption.UNTIL_MORNING -> stringResource(R.string.pause_until_morning, endLabel)
                }
                SettingRow(
                    title = title,
                    supporting = if (option == PauseOption.UNTIL_MORNING) null else stringResource(R.string.pause_ends, endLabel),
                    icon = if (option == PauseOption.UNTIL_MORNING) R.drawable.ic_ms_bedtime else R.drawable.ic_ms_timer,
                    onClick = {
                        // The label was computed when the sheet opened; the pause starts now.
                        onPause(pauseEnd(option, ZonedDateTime.now()).toInstant().toEpochMilli())
                        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
                    },
                )
            }
        }
    }
}
