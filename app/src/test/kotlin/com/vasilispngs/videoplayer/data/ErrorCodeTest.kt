package com.vasilispngs.videoplayer.data

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
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
    fun `names the status a Windows computer answers`() {
        assertEquals(ErrorCode.Named("STATUS_LOGON_FAILURE"), logonFailure().errorCode())
    }

    @Test
    fun `finds the Windows status behind a wrapping exception`() {
        assertEquals(ErrorCode.Named("STATUS_LOGON_FAILURE"), IOException(logonFailure()).errorCode())
    }

    @Test
    fun `names the platform exception at the root of the failure`() {
        assertEquals(ErrorCode.Named("ConnectException"), IOException(ConnectException()).errorCode())
    }

    @Test
    fun `names the platform exception that a renamed exception extends`() {
        assertEquals(ErrorCode.Named("IOException"), Renamed().errorCode())
    }

    private fun logonFailure() = SMBApiException(NtStatus.STATUS_LOGON_FAILURE.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null)

    private class Renamed : IOException()
}
