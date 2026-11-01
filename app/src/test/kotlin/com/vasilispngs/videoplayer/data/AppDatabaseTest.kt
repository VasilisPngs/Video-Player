package com.vasilispngs.videoplayer.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private val context = RuntimeEnvironment.getApplication()
    private val path = context.getDatabasePath(DATABASE_NAME)

    @Test
    fun `keeps the USB progress and drops the computers when it upgrades from the first version`() = runTest {
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `computers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `host` TEXT NOT NULL, `share` TEXT NOT NULL, `user` TEXT NOT NULL, `password` BLOB NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_computers_host_share` ON `computers` (`host`, `share`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `playback` (`uri` TEXT NOT NULL, `size` INTEGER NOT NULL, `position_ms` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, `audio_track` TEXT, `text_chosen` INTEGER NOT NULL, `text_track` TEXT, `subtitle_delay_ms` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`uri`, `size`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ddc6d6bd191571de8617559612396c42')")
            db.execSQL("INSERT INTO computers (host, share, user, password) VALUES ('192.168.2.2', 'Downloads', 'tv', x'00')")
            db.execSQL("INSERT INTO playback VALUES ('$USB_MOVIE', 1, 60000, 7200000, NULL, 0, NULL, 0, 2)")
            db.execSQL("INSERT INTO playback VALUES ('smb://192.168.2.2/Downloads/Movie.mp4', 2, 60000, 9960000, NULL, 0, NULL, 0, 1)")
            db.version = 1
        }

        val database = AppDatabase.create(context)
        try {
            assertEquals(listOf(USB_MOVIE), database.playback().inProgress().first().map(Playback::uri))
        } finally {
            database.close()
        }

        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT name FROM sqlite_master WHERE name = 'computers'", null).use { assertEquals(0, it.count) }
        }
    }

    private companion object {
        const val USB_MOVIE = "file:///storage/1234-5678/Movie.mkv"
    }
}
