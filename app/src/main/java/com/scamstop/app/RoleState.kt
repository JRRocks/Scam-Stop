package com.scamstop.app

internal enum class RoleState {
    ACTIVE,
    NOT_ACTIVE,
    UNAVAILABLE,
}

internal fun roleState(isRoleAvailable: Boolean, isRoleHeld: Boolean): RoleState = when {
    !isRoleAvailable -> RoleState.UNAVAILABLE
    isRoleHeld -> RoleState.ACTIVE
    else -> RoleState.NOT_ACTIVE
}
