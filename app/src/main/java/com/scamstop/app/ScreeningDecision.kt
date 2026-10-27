package com.scamstop.app

internal enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN,
}

internal enum class ScreeningAction {
    BLOCK,
    ALLOW,
    IGNORE,
}

/**
 * Android only hands this service calls from numbers outside the contacts, so every
 * incoming call reaching it is unknown: rejected while blocking is on, unless the user
 * allowed that number in the app.
 */
internal fun screeningAction(
    direction: CallDirection,
    isBlocking: Boolean,
    isAllowlisted: Boolean = false,
): ScreeningAction = when {
    direction != CallDirection.INCOMING -> ScreeningAction.IGNORE
    isBlocking && !isAllowlisted -> ScreeningAction.BLOCK
    else -> ScreeningAction.ALLOW
}
