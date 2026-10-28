package com.scamstop.app

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

internal fun Context.currentRoleState(): RoleState {
    val roleManager = getSystemService(RoleManager::class.java) ?: return RoleState.UNAVAILABLE
    val available = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    return roleState(available, available && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
}

internal fun Context.currentProtectionStatus(now: Long = System.currentTimeMillis()): ProtectionStatus {
    val settings = ScamStopStore.get(this).settings.value
    return protectionStatus(currentRoleState(), settings.isEnabled, settings.pausedUntil, now)
}

internal fun Context.hasSmsPermission(): Boolean =
    checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

internal fun Context.appNotificationsEnabled(): Boolean =
    getSystemService(NotificationManager::class.java).areNotificationsEnabled()

/** Android delivers them: permission granted, app and "Appels rejetés" category not switched off. */
internal fun Context.canPostNotifications(): Boolean =
    hasNotificationPermission() && appNotificationsEnabled() && !RejectedCallNotifications.isChannelBlocked(this)

internal fun Context.formatPhoneNumber(number: String?): String {
    if (number.isNullOrBlank()) return getString(R.string.unknown_number)
    return PhoneNumberUtils.formatNumber(number, countryIso()) ?: number
}

private fun Context.countryIso(): String {
    val telephony = getSystemService(TelephonyManager::class.java)
    return listOf(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
        .firstOrNull { !it.isNullOrBlank() }
        ?.uppercase(Locale.ROOT)
        ?: "FR"
}

/** "15:30", or "demain 8:00" when the time falls on the next day. */
internal fun Context.formatPauseEnd(endMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val time = formatTime(endMillis)
    val zone = ZoneId.systemDefault()
    val endDay = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (endDay == today) time else getString(R.string.time_tomorrow, time)
}

internal fun Context.formatTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))
