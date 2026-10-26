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

internal fun screeningAction(
    direction: CallDirection,
    isBlockingEnabled: Boolean,
): ScreeningAction = when {
    direction != CallDirection.INCOMING -> ScreeningAction.IGNORE
    isBlockingEnabled -> ScreeningAction.BLOCK
    else -> ScreeningAction.ALLOW
}
