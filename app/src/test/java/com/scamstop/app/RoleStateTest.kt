package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleStateTest {
    @Test
    fun heldAvailableRoleIsActive() {
        assertEquals(RoleState.ACTIVE, roleState(isRoleAvailable = true, isRoleHeld = true))
    }

    @Test
    fun availableRoleThatIsNotHeldIsNotActive() {
        assertEquals(RoleState.NOT_ACTIVE, roleState(isRoleAvailable = true, isRoleHeld = false))
    }

    @Test
    fun unavailableRoleIsUnavailable() {
        assertEquals(RoleState.UNAVAILABLE, roleState(isRoleAvailable = false, isRoleHeld = false))
    }
}
