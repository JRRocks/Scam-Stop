package com.scamstop.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scamstop.app.RoleState
import com.scamstop.app.ScamStopStore
import com.scamstop.app.canPostNotifications
import com.scamstop.app.currentRoleState
import com.scamstop.app.hasNotificationPermission

internal enum class LaunchRequest { REJECTED_CALLS, PAUSE }

private object Routes {
    const val HOME = "home"
    const val CALLS = "calls"
    const val ALLOWLIST = "allowlist"
    const val GREETING = "greeting"
}

/** What Android decides outside the app; read again each time the app comes back. */
@Stable
internal class SystemState(private val context: Context) {
    var role by mutableStateOf(context.currentRoleState())
        private set
    var notificationPermission by mutableStateOf(context.hasNotificationPermission())
        private set
    var notificationsAllowed by mutableStateOf(context.canPostNotifications())
        private set

    fun refresh() {
        role = context.currentRoleState()
        notificationPermission = context.hasNotificationPermission()
        notificationsAllowed = context.canPostNotifications()
    }

    val roleHeld: Boolean get() = role == RoleState.ACTIVE
}

@Composable
internal fun ScamStopApp(request: LaunchRequest?, onRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ScamStopStore.get(context) }
    val system = remember { SystemState(context.applicationContext) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { system.refresh() }
    val navController = rememberNavController()
    var pauseSheetRequested by rememberSaveable { mutableStateOf(false) }

    // dropUnlessResumed, created inside each destination, ignores a second tap while a
    // transition runs: a double tap on Back would otherwise pop the home screen too.
    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                store = store,
                system = system,
                pauseSheetRequested = pauseSheetRequested,
                onPauseSheetRequestHandled = { pauseSheetRequested = false },
                onOpenCalls = dropUnlessResumed { navController.navigate(Routes.CALLS) },
                onOpenAllowlist = dropUnlessResumed { navController.navigate(Routes.ALLOWLIST) },
                onOpenGreeting = dropUnlessResumed { navController.navigate(Routes.GREETING) },
            )
        }
        composable(Routes.CALLS) { RejectedCallsScreen(store, onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.ALLOWLIST) { AllowlistScreen(store, onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.GREETING) { GreetingScreen(store, onBack = dropUnlessResumed { navController.popBackStack() }) }
    }

    // After the NavHost so its graph is set before navigating.
    LaunchedEffect(request) {
        when (request) {
            LaunchRequest.REJECTED_CALLS -> navController.navigate(Routes.CALLS) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
            LaunchRequest.PAUSE -> {
                navController.popBackStack(Routes.HOME, inclusive = false)
                pauseSheetRequested = true
            }
            null -> return@LaunchedEffect
        }
        onRequestHandled()
    }
}
