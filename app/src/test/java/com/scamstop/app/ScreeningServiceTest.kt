package com.scamstop.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/**
 * The real service, driven the way Telecom drives it: through its binder, with a call built
 * like `ParcelableCallUtils.toParcelableCallForScreening` builds one. Storage is the real
 * store on Robolectric's SharedPreferences. These framework classes are hidden: reflection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreeningServiceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val answers = mutableListOf<String>()
    private lateinit var binder: IBinder

    @Before
    fun bindService() {
        ScamStopStore.forgetInstance()
        val service = Robolectric.buildService(NonContactCallScreeningService::class.java).create().get()
        binder = service.onBind(Intent(CallScreeningService.SERVICE_INTERFACE))!!
    }

    @Test
    fun firstCallIsRejectedAndTheSecondRings() {
        screen(Call.Details.DIRECTION_INCOMING, "+33123456789")
        screen(Call.Details.DIRECTION_INCOMING, "0123456789")
        assertEquals(listOf("reject", "ring"), answers)

        val journal = ScamStopStore.get(context).calls.value
        assertEquals(listOf(CallOutcome.RANG_SECOND_CALL, CallOutcome.REJECTED), journal.map { it.outcome })
        assertEquals(listOf("0123456789", "+33123456789"), journal.map { it.number })
        assertEquals(journal[1].timeMillis, journal[0].sinceMillis)
    }

    @Test
    fun aNumberTheUserDialledRingsBackEvenAfterARestart() {
        screen(Call.Details.DIRECTION_OUTGOING, "01 23 45 67 89")
        // Android answers by itself for an outgoing call: nothing to block.
        assertEquals(listOf("no answer needed"), answers)
        assertEquals(listOf("01 23 45 67 89"), ScamStopStore.get(context).calledNumbers.value.map { it.number })

        // A new process only has what was written.
        ScamStopStore.forgetInstance()
        screen(Call.Details.DIRECTION_INCOMING, "+33123456789")
        assertEquals(listOf("no answer needed", "ring"), answers)
        assertEquals(listOf(CallOutcome.RANG_CALLED_BACK), ScamStopStore.get(context).calls.value.map { it.outcome })
    }

    @Test
    fun foreignSpoofedAndWithheldCallsAreRejectedEachTime() {
        screen(Call.Details.DIRECTION_INCOMING, "+447700900123")
        screen(Call.Details.DIRECTION_INCOMING, "+447700900123")
        screen(Call.Details.DIRECTION_INCOMING, "0612345678", Connection.VERIFICATION_STATUS_FAILED)
        screen(Call.Details.DIRECTION_INCOMING, "0612345678")
        screen(Call.Details.DIRECTION_INCOMING, null)
        assertEquals(listOf("reject", "reject", "reject", "reject", "reject"), answers)
    }

    @Test
    fun pausedProtectionRingsAndWritesNothing() {
        val store = ScamStopStore.get(context)
        store.setCalledBack(true) // any settings write, to check the flags survive it
        screen(Call.Details.DIRECTION_INCOMING, "0612345678", pausedUntil = Long.MAX_VALUE)
        assertEquals(listOf("ring"), answers)
        assertEquals(emptyList<ScreenedCall>(), store.calls.value)
    }

    @Test
    fun turningAnExceptionOffErasesWhatItRemembered() {
        val store = ScamStopStore.get(context)
        screen(Call.Details.DIRECTION_OUTGOING, "0123456789")
        screen(Call.Details.DIRECTION_INCOMING, "0612345678")
        store.setCalledBack(false)
        store.setSecondCall(false)
        assertEquals(emptyList<CalledNumber>(), store.calledNumbers.value)

        ScamStopStore.forgetInstance()
        screen(Call.Details.DIRECTION_INCOMING, "0123456789")
        screen(Call.Details.DIRECTION_INCOMING, "0612345678")
        assertEquals(listOf("no answer needed", "reject", "reject", "reject"), answers)
    }

    /** Hands one call to the service and runs the main thread until it has dealt with it. */
    private fun screen(direction: Int, number: String?, verification: Int = 0, pausedUntil: Long = 0L) {
        if (pausedUntil != 0L) {
            context.createDeviceProtectedStorageContext().getSharedPreferences("blocking_policy", 0)
                .edit().putLong("paused_until", pausedUntil).commit()
            ScamStopStore.forgetInstance()
        }
        val before = answers.size
        val adapterType = Class.forName("com.android.internal.telecom.ICallScreeningAdapter")
        val adapter = Proxy.newProxyInstance(adapterType.classLoader, arrayOf(adapterType)) { _, method, args ->
            if (method.name == "onScreeningResponse") {
                val response = args[2]
                answers += when {
                    response == null -> "no answer needed"
                    response.call("shouldDisallowCall") == true && response.call("shouldRejectCall") == true -> "reject"
                    response.call("shouldDisallowCall") == false && response.call("shouldRejectCall") == false -> "ring"
                    else -> "unexpected answer"
                }
            }
            null
        }
        val serviceType = Class.forName("com.android.internal.telecom.ICallScreeningService")
        serviceType.getMethod("screenCall", adapterType, Class.forName("android.telecom.ParcelableCall"))
            .invoke(binder, adapter, parcelableCall(direction, number, verification))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("one answer per call", before + 1, answers.size)
    }

    /** The fields Telecom fills for a screening service; the rest stays empty, as there. */
    private fun parcelableCall(direction: Int, number: String?, verification: Int): Any {
        val builder = Class.forName("android.telecom.ParcelableCall\$ParcelableCallBuilder").getConstructor().newInstance()
        fun set(name: String, type: Class<*>, value: Any?) {
            builder.javaClass.getMethod(name, type).invoke(builder, value)
        }
        set("setId", String::class.java, "TC@${answers.size}")
        set("setState", Int::class.java, Call.STATE_RINGING)
        set("setDisconnectCause", DisconnectCause::class.java, DisconnectCause(DisconnectCause.UNKNOWN))
        set("setHandle", Uri::class.java, number?.let { Uri.fromParts("tel", it, null) })
        set(
            "setHandlePresentation",
            Int::class.java,
            if (number != null) TelecomManager.PRESENTATION_ALLOWED else TelecomManager.PRESENTATION_RESTRICTED,
        )
        set("setConferenceableCallIds", List::class.java, emptyList<String>())
        set("setExtras", Bundle::class.java, Bundle())
        set("setCreationTimeMillis", Long::class.java, System.currentTimeMillis())
        set("setCallDirection", Int::class.java, direction)
        set("setCallerNumberVerificationStatus", Int::class.java, verification)
        return builder.javaClass.getMethod("createParcelableCall").invoke(builder)!!
    }

    private fun Any.call(name: String): Any? = javaClass.getMethod(name).invoke(this)
}
