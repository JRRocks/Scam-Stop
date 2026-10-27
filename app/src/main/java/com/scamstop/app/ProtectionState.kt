package com.scamstop.app

import java.time.ZonedDateTime

internal enum class ProtectionKind {
    ACTIVE,
    PAUSED,
    OFF,
    NEEDS_ROLE,
    UNAVAILABLE,
}

internal data class ProtectionStatus(val kind: ProtectionKind, val pausedUntil: Long = 0L)

internal fun isPaused(pausedUntil: Long, now: Long): Boolean = now < pausedUntil

internal fun isBlocking(isEnabled: Boolean, pausedUntil: Long, now: Long): Boolean =
    isEnabled && !isPaused(pausedUntil, now)

internal fun protectionStatus(
    roleState: RoleState,
    isEnabled: Boolean,
    pausedUntil: Long,
    now: Long,
): ProtectionStatus = when {
    roleState == RoleState.UNAVAILABLE -> ProtectionStatus(ProtectionKind.UNAVAILABLE)
    roleState == RoleState.NOT_ACTIVE -> ProtectionStatus(ProtectionKind.NEEDS_ROLE)
    !isEnabled -> ProtectionStatus(ProtectionKind.OFF)
    isPaused(pausedUntil, now) -> ProtectionStatus(ProtectionKind.PAUSED, pausedUntil)
    else -> ProtectionStatus(ProtectionKind.ACTIVE)
}

internal enum class PauseOption {
    MINUTES_15,
    HOUR_1,
    HOURS_3,
    UNTIL_MORNING,
}

/** The quick settings tile pauses for this long: the "delivery driver" case from the README. */
internal val TILE_PAUSE = PauseOption.HOUR_1

internal const val MORNING_HOUR = 8

internal fun pauseEnd(option: PauseOption, now: ZonedDateTime): ZonedDateTime = when (option) {
    PauseOption.MINUTES_15 -> now.plusMinutes(15)
    PauseOption.HOUR_1 -> now.plusHours(1)
    PauseOption.HOURS_3 -> now.plusHours(3)
    PauseOption.UNTIL_MORNING -> nextMorning(now)
}

/** 8:00 today when it is still ahead, otherwise 8:00 tomorrow. */
internal fun nextMorning(now: ZonedDateTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(MORNING_HOUR, 0).atZone(now.zone)
    return if (now.isBefore(today)) today else today.toLocalDate().plusDays(1).atTime(MORNING_HOUR, 0).atZone(now.zone)
}

internal data class SetupProgress(
    val roleDone: Boolean,
    val notificationsDone: Boolean,
    val greetingDone: Boolean,
) {
    val doneCount: Int get() = listOf(roleDone, notificationsDone, greetingDone).count { it }
    val total: Int get() = 3
    val isComplete: Boolean get() = doneCount == total
}
