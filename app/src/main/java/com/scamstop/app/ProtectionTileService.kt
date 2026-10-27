package com.scamstop.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.time.ZonedDateTime

/**
 * Quick settings tile. A tap pauses protection for an hour, resumes it, or turns it back on;
 * a long press opens the app on the pause durations (ACTION_QS_TILE_PREFERENCES).
 */
class ProtectionTileService : TileService() {
    override fun onTileAdded() = ScamStopStore.get(this).setTileAdded(true)

    override fun onTileRemoved() = ScamStopStore.get(this).setTileAdded(false)

    override fun onStartListening() = updateTile()

    override fun onClick() {
        // Pausing call screening must not be possible from a locked phone in someone else's hands.
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val store = ScamStopStore.get(this)
        when (currentProtectionStatus().kind) {
            ProtectionKind.ACTIVE ->
                store.pauseUntil(pauseEnd(TILE_PAUSE, ZonedDateTime.now()).toInstant().toEpochMilli())
            ProtectionKind.PAUSED -> store.resume()
            ProtectionKind.OFF -> store.setEnabled(true)
            ProtectionKind.NEEDS_ROLE, ProtectionKind.UNAVAILABLE -> openApp()
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val status = currentProtectionStatus()
        val (state, icon, subtitle) = when (status.kind) {
            ProtectionKind.ACTIVE -> Triple(Tile.STATE_ACTIVE, R.drawable.ic_ms_verified_user, getString(R.string.tile_active))
            ProtectionKind.PAUSED -> Triple(
                Tile.STATE_INACTIVE,
                R.drawable.ic_ms_pause_circle,
                getString(R.string.tile_paused, formatPauseEnd(status.pausedUntil)),
            )
            ProtectionKind.OFF -> Triple(Tile.STATE_INACTIVE, R.drawable.ic_ms_gpp_bad, getString(R.string.tile_off))
            ProtectionKind.NEEDS_ROLE -> Triple(Tile.STATE_INACTIVE, R.drawable.ic_ms_gpp_bad, getString(R.string.tile_needs_role))
            ProtectionKind.UNAVAILABLE ->
                Triple(Tile.STATE_UNAVAILABLE, R.drawable.ic_ms_gpp_maybe, getString(R.string.tile_unavailable))
        }
        tile.state = state
        tile.icon = Icon.createWithResource(this, icon)
        tile.label = getString(R.string.tile_label)
        tile.subtitle = subtitle
        tile.contentDescription = "${getString(R.string.tile_label)}, $subtitle"
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) = startActivityAndCollapse(intent)

    companion object {
        fun component(context: Context) = ComponentName(context, ProtectionTileService::class.java)

        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, component(context))
            } catch (e: IllegalArgumentException) {
                // The tile is not added or the component is unavailable: nothing to refresh.
            } catch (e: SecurityException) {
                // Same as above on some OEM builds.
            }
        }
    }
}
