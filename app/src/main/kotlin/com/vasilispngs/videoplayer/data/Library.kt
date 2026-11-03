package com.vasilispngs.videoplayer.data

import android.content.Context
import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class Kind { Folder, Video, File }

data class Entry(val name: String, val uri: Uri, val kind: Kind, val size: Long)

data class Volume(val name: String, val uri: Uri)

internal fun List<Playback>.onMounted(volumes: List<Volume>): List<Playback> =
    filter { playback -> volumes.any { playback.uri.startsWith("${it.uri}/") } }

private val VIDEO_EXTENSIONS = setOf("avi", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "mts", "ts", "webm")

internal fun kindOf(name: String, directory: Boolean): Kind = when {
    directory -> Kind.Folder
    name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS -> Kind.Video
    else -> Kind.File
}

private val collator: Collator = (Collator.getInstance() as RuleBasedCollator).apply { numericCollation = true }.freeze()

val fileNameOrder: Comparator<String> = Comparator(collator::compare)

private val entryOrder = compareBy<Entry> { it.kind != Kind.Folder }.thenBy(fileNameOrder) { it.name }

class Library(private val context: Context) {
    val volumes: Flow<List<Volume>> = callbackFlow {
        val storage = context.getSystemService(StorageManager::class.java)
        fun update() {
            val _ = trySend(storage.storageVolumes.mapNotNull { it.toVolume() })
        }
        val callback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                update()
            }
        }
        storage.registerStorageVolumeCallback(context.mainExecutor, callback)
        update()
        awaitClose { storage.unregisterStorageVolumeCallback(callback) }
    }.conflate()

    suspend fun list(folder: Uri): List<Entry> = withContext(Dispatchers.IO) {
        val files = folder.toFile().listFiles() ?: throw IOException(folder.toString())
        files.filterNot(File::isHidden).map { Entry(it.name, Uri.fromFile(it), kindOf(it.name, it.isDirectory), it.length()) }
    }.sortedWith(entryOrder)

    suspend fun size(uri: Uri): Long = withContext(Dispatchers.IO) { uri.toFile().length() }

    suspend fun head(uri: Uri, bytes: Int): ByteArray = withContext(Dispatchers.IO) {
        uri.toFile().inputStream().use { it.readNBytes(bytes) }
    }

    private fun StorageVolume.toVolume(): Volume? {
        if (!isRemovable || state != Environment.MEDIA_MOUNTED) return null
        val root = directory ?: return null
        return Volume(getDescription(context), Uri.fromFile(root))
    }
}

val Uri.parent: Uri
    get() = checkNotNull(toFile().parentFile).toUri()

val Uri.fileName: String
    get() = lastPathSegment.orEmpty()
