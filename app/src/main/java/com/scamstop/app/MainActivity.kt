package com.scamstop.app

import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scamstop.app.ui.LaunchRequest
import com.scamstop.app.ui.ScamStopApp
import com.scamstop.app.ui.ScamStopTheme

class MainActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        RejectedCallNotifications.ensureChannel(this)
        // Reopening from Recents replays the original intent: do not reopen the calls or the pause sheet.
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromRecents) launchRequest = requestFor(intent)
        addOnNewIntentListener { launchRequest = requestFor(it) }
        setContent {
            ScamStopTheme {
                ScamStopApp(request = launchRequest, onRequestHandled = { launchRequest = null })
            }
        }
    }

    private fun requestFor(intent: Intent?): LaunchRequest? = when (intent?.action) {
        ACTION_SHOW_REJECTED_CALLS -> LaunchRequest.REJECTED_CALLS
        TileService.ACTION_QS_TILE_PREFERENCES -> LaunchRequest.PAUSE
        else -> null
    }

    companion object {
        /** Sent by the rejected-call notification. */
        const val ACTION_SHOW_REJECTED_CALLS = "com.scamstop.app.action.SHOW_REJECTED_CALLS"
    }
}
