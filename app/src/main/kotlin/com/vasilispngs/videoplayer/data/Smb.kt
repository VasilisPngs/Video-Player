package com.vasilispngs.videoplayer.data

import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.EnumSet

internal const val SMB_SCHEME = "smb"

class Smb(private val computers: ComputerDao, private val secrets: Secrets) {
    private val client = SMBClient()
    private val shares = HashMap<String, DiskShare>()

    val saved: Flow<List<Computer>> = computers.observe()

    suspend fun add(host: String, share: String, user: String, password: String) {
        val encrypted = withContext(Dispatchers.IO) {
            connect(host, share, user, password).list("")
            secrets.encrypt(password)
        }
        computers.insert(Computer(host = host, share = share, user = user, password = encrypted))
    }

    suspend fun remove(computer: Computer) {
        computers.delete(computer.id)
        synchronized(shares) { shares.remove(key(computer.host, computer.share)) }
    }

    fun list(folder: Uri): List<Entry> = share(folder).list(path(folder))
        .filter { it.fileName != "." && it.fileName != ".." && !it.fileAttributes.has(FileAttributes.FILE_ATTRIBUTE_HIDDEN) }
        .map { info ->
            val directory = info.fileAttributes.has(FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
            Entry(info.fileName, folder.buildUpon().appendPath(info.fileName).build(), kindOf(info.fileName, directory), info.endOfFile)
        }

    fun open(uri: Uri): File = share(uri).openFile(
        path(uri),
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null
    )

    private fun share(uri: Uri): DiskShare {
        val host = uri.authority ?: throw FileNotFoundException(uri.toString())
        val name = uri.pathSegments.firstOrNull() ?: throw FileNotFoundException(uri.toString())
        synchronized(shares) { shares[key(host, name)]?.takeIf { it.isConnected }?.let { return it } }
        val computer = runBlocking { computers.find(host, name) } ?: throw FileNotFoundException(uri.toString())
        return connect(host, name, computer.user, secrets.decrypt(computer.password))
    }

    private fun connect(host: String, name: String, user: String, password: String): DiskShare {
        val domain = user.substringBefore('\\', "").ifEmpty { null }
        val session = client.connect(host).authenticate(AuthenticationContext(user.substringAfter('\\'), password.toCharArray(), domain))
        val share = session.connectShare(name) as? DiskShare ?: throw IOException(name)
        synchronized(shares) { shares[key(host, name)] = share }
        return share
    }

    private fun path(uri: Uri): String = uri.pathSegments.drop(1).joinToString("\\")

    private fun key(host: String, share: String) = "$host/$share"
}

private fun Long.has(attribute: FileAttributes): Boolean = (this and attribute.value) != 0L

fun smbUri(host: String, share: String): Uri = Uri.Builder().scheme(SMB_SCHEME).authority(host).appendPath(share).build()
