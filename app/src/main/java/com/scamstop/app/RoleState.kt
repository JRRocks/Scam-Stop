package com.scamstop.app

internal enum class RoleState {
    ACTIVE,
    NOT_ACTIVE,
}

internal fun roleState(isRoleAvailable: Boolean, isRoleHeld: Boolean): RoleState =
    if (isRoleAvailable && isRoleHeld) RoleState.ACTIVE else RoleState.NOT_ACTIVE
