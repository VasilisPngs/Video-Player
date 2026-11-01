package com.vasilispngs.videoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.ConnectException

@RunWith(RobolectricTestRunner::class)
class ErrorCodeTest {
    @Test
    fun `names the HTTP status of a failed request`() {
        assertEquals(ErrorCode.Http(404), HttpStatusException(404).errorCode())
    }

    @Test
    fun `names the platform exception at the root of the failure`() {
        assertEquals(ErrorCode.Named("ConnectException"), IOException(ConnectException()).errorCode())
    }

    @Test
    fun `names the platform exception that a renamed exception extends`() {
        assertEquals(ErrorCode.Named("IOException"), Renamed().errorCode())
    }

    private class Renamed : IOException()
}
