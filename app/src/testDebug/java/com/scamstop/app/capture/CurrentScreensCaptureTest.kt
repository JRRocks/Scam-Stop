package com.scamstop.app.capture

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.scamstop.app.ScreenedCall
import com.scamstop.app.ui.AllowlistScreen
import com.scamstop.app.ui.HomeScreen
import com.scamstop.app.ui.RejectedCallsScreen
import com.scamstop.app.ui.SystemState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens as they are, with the same fictitious data as the preview page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h1100dp-xxhdpi")
class CurrentScreensCaptureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeJournalAndAllowedNumbers() {
        val captures = ScreenCaptures(compose)
        val store = captures.freshStore()
        store.setGreetingInstalled(true)
        store.setTileAdded(true)
        store.setNotify(true)
        store.allow("06 12 34 56 78", "Livreure")
        store.allow("07 11 22 33 44", "Plombier")
        val now = System.currentTimeMillis()
        listOf("+44 7700 900123", "+44 7700 900123", "09 87 65 43 21", "04 11 22 33 44", "01 23 45 67 89", "01 23 45 67 89")
            .forEachIndexed { index, number -> store.addToJournal(ScreenedCall(number, now - (6 - index) * 60_000L)) }
        captures.frame()

        captures.show { HomeScreen(store, SystemState(captures.application), false, {}, {}, {}, {}) }
        compose.onNodeWithText("Protection active").assertExists()
        captures.capture("actuel-accueil")

        captures.show { RejectedCallsScreen(store, onBack = {}) }
        compose.onNodeWithText("Appels rejetés").assertExists()
        captures.capture("actuel-journal")

        captures.show { AllowlistScreen(store, onBack = {}) }
        compose.onNodeWithText("Livreure").assertExists()
        captures.capture("actuel-numeros-autorises")

        captures.show(darkTheme = true) { HomeScreen(store, SystemState(captures.application), false, {}, {}, {}, {}) }
        captures.capture("actuel-accueil-sombre")
    }
}
