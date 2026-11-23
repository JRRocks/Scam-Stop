package com.scamstop.app

import android.content.Context

/** Release builds keep no trace of screened calls; debug builds have a real one. */
internal object ScreeningTrace {
    @Suppress("UNUSED_PARAMETER")
    fun record(
        context: Context,
        direction: CallDirection,
        number: String?,
        verification: Int?,
        onFrenchNetwork: Boolean,
        verdict: ScreeningVerdict,
        now: Long,
        answerMillis: Long,
    ) = Unit
}
