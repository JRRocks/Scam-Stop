package com.scamstop.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var roleManager: RoleManager
    private lateinit var roleWarningText: TextView
    private lateinit var requestRoleButton: Button
    private lateinit var notificationStatusText: TextView
    private lateinit var requestNotificationsButton: Button
    private lateinit var blockingSwitch: Switch
    private var isUpdatingBlockingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roleManager = getSystemService(RoleManager::class.java)
        roleWarningText = findViewById(R.id.role_warning)
        requestRoleButton = findViewById(R.id.request_role)
        notificationStatusText = findViewById(R.id.notification_status)
        requestNotificationsButton = findViewById(R.id.request_notifications)
        blockingSwitch = findViewById(R.id.blocking_switch)
        requestRoleButton.setOnClickListener { requestCallScreeningRole() }
        requestNotificationsButton.setOnClickListener { requestNotificationAccess() }
        blockingSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!isUpdatingBlockingSwitch) BlockingPreference.setEnabled(this, enabled)
        }
    }

    override fun onResume() {
        super.onResume()
        updateScreeningAccess()
        updateNotificationStatus()
    }

    @Suppress("DEPRECATION")
    private fun requestCallScreeningRole() {
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
        startActivityForResult(
            roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
            ROLE_REQUEST_CODE,
        )
    }

    @Deprecated("The platform role request API still uses activity results on API 29")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ROLE_REQUEST_CODE) updateScreeningAccess()
    }

    private fun updateScreeningAccess() {
        val available = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
        val state = roleState(
            isRoleAvailable = available,
            isRoleHeld = available && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
        )
        val active = state == RoleState.ACTIVE
        roleWarningText.visibility = if (active) View.GONE else View.VISIBLE
        requestRoleButton.visibility = if (active) View.GONE else View.VISIBLE
        requestRoleButton.isEnabled = available
        requestRoleButton.setText(
            if (available) R.string.grant_call_screening_access else R.string.role_unavailable,
        )

        isUpdatingBlockingSwitch = true
        blockingSwitch.isEnabled = active
        blockingSwitch.isChecked = active && BlockingPreference.isEnabled(this)
        isUpdatingBlockingSwitch = false
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
            return
        }

        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST_CODE) updateNotificationStatus()
    }

    private fun updateNotificationStatus() {
        val manager = getSystemService(NotificationManager::class.java)
        val enabled = manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        notificationStatusText.setText(
            if (enabled) R.string.notifications_enabled else R.string.notifications_disabled,
        )
        requestNotificationsButton.setText(
            if (enabled) R.string.manage_notifications else R.string.enable_notifications,
        )
    }

    private companion object {
        const val ROLE_REQUEST_CODE = 1
        const val NOTIFICATION_REQUEST_CODE = 2
    }
}
