package com.scamstop.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import com.scamstop.app.R
import com.scamstop.app.VoiceGreeting

// None of these need a permission: the dialer, messaging and contacts apps do the work,
// and Scam Stop still never reads the contacts.

internal fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

internal fun Context.dial(number: String) = tryStart(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

internal fun Context.openDialer() = tryStart(Intent(Intent.ACTION_DIAL))

internal fun Context.sendSms(number: String) = tryStart(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

internal fun Context.addToContacts(number: String) = tryStart(
    Intent(ContactsContract.Intents.Insert.ACTION)
        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
        .putExtra(ContactsContract.Intents.Insert.PHONE, number),
)

internal fun Context.shareGreeting(uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND)
        .setType(VoiceGreeting.MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    return tryStart(Intent.createChooser(send, getString(R.string.share_greeting_title)))
}

internal fun Context.openGreeting(uri: Uri) = tryStart(
    Intent(Intent.ACTION_VIEW).setDataAndType(uri, VoiceGreeting.MIME_TYPE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
)

/** The phone app's own blocking settings, where hidden and private numbers can be blocked. */
internal fun Context.openBlockedNumbers(): Boolean =
    getSystemService(TelecomManager::class.java)?.let { tryStart(it.createManageBlockedNumbersIntent()) } ?: false

internal fun Context.openAppNotificationSettings() = tryStart(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
