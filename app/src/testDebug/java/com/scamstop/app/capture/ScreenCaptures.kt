package com.scamstop.app.capture

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.scamstop.app.ScamStopStore
import com.scamstop.app.ui.ScamStopTheme
import org.robolectric.Shadows.shadowOf
import java.io.File

internal typealias CaptureRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * Draws real screens into PNG files under app/build/captures/, without an emulator.
 * Fictitious data only: a PNG gets shared.
 */
internal class ScreenCaptures(private val compose: CaptureRule) {
    private var dark by mutableStateOf(false)
    private var screen by mutableIntStateOf(0)
    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    val application: Application = ApplicationProvider.getApplicationContext()

    /** A phone set up the way the app expects: screening role held, notifications allowed, empty store. */
    fun freshStore(): ScamStopStore {
        val roles = shadowOf(application.getSystemService(RoleManager::class.java))
        roles.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
        roles.addHeldRole(RoleManager.ROLE_CALL_SCREENING)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ScamStopStore.forgetInstance()
        return ScamStopStore.get(application)
    }

    fun frame() {
        compose.setContent {
            ScamStopTheme(darkTheme = dark) {
                // key(screen): each show() starts from a new screen, without remembered state.
                key(screen) {
                    Box(Modifier.fillMaxSize().testTag(TAG)) { content?.invoke() }
                }
            }
        }
    }

    fun show(darkTheme: Boolean = false, block: @Composable () -> Unit) {
        dark = darkTheme
        content = block
        screen++
        compose.waitForIdle()
    }

    fun capture(name: String) {
        compose.waitForIdle()
        val zone = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
        val view = compose.activity.window.decorView
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        val cropped = Bitmap.createBitmap(
            full,
            zone.left.toInt(),
            zone.top.toInt(),
            zone.width.toInt(),
            zone.height.toInt().coerceAtMost(full.height - zone.top.toInt()),
        )
        File("build/captures").mkdirs()
        File("build/captures/$name.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TAG = "capture"
    }
}
