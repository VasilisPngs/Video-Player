package com.vasilispngs.videoplayer.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.DeleteTable
import androidx.room3.Entity
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playback", primaryKeys = ["uri", "size"])
data class Playback(
    val uri: String,
    val size: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "audio_track") val audioTrack: String? = null,
    @ColumnInfo(name = "text_chosen") val textChosen: Boolean = false,
    @ColumnInfo(name = "text_track") val textTrack: String? = null,
    @ColumnInfo(name = "subtitle_delay_ms") val subtitleDelayMs: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0
)

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback WHERE position_ms > 0 ORDER BY updated_at DESC")
    fun inProgress(): Flow<List<Playback>>

    @Query("SELECT * FROM playback WHERE uri = :uri AND size = :size")
    suspend fun find(uri: String, size: Long): Playback?

    @Upsert
    suspend fun save(playback: Playback)
}

@Database(
    entities = [Playback::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.RemoveComputers::class)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playback(): PlaybackDao

    @DeleteTable(tableName = "computers")
    class RemoveComputers : AutoMigrationSpec {
        override suspend fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("DELETE FROM playback WHERE uri LIKE 'smb:%'")
        }
    }

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder<AppDatabase>(context, DATABASE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
}

internal const val DATABASE_NAME = "video-player.db"
