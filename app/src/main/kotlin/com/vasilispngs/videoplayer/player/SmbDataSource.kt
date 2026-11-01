@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.share.File
import com.vasilispngs.videoplayer.data.Smb
import java.io.InputStream

class SmbDataSource(private val smb: Smb) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var file: File? = null
    private var input: InputStream? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val size = guarded { smb.open(dataSpec.uri).also { file = it }.fileInformation.standardInformation.endOfFile }
        if (dataSpec.position > size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        input = guarded { checkNotNull(file).inputStream.apply { skip(dataSpec.position) } }
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - dataSpec.position
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val read = guarded { checkNotNull(input).read(buffer, offset, minOf(length.toLong(), bytesRemaining).toInt()) }
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        val _ = runCatching { input?.close() }
        val _ = runCatching { file?.close() }
        input = null
        file = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (exception: DataSourceException) {
        throw exception
    } catch (exception: Exception) {
        throw DataSourceException(exception, exception.reason())
    }

    private fun Exception.reason(): Int = when ((this as? SMBApiException)?.status) {
        NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        NtStatus.STATUS_ACCESS_DENIED, NtStatus.STATUS_LOGON_FAILURE -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        else -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

    class Factory(private val smb: Smb) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(smb)
    }
}
