package com.vasilispngs.videoplayer.data

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "computers", indices = [Index(value = ["host", "share"], unique = true)])
class Computer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val share: String,
    val user: String,
    val password: ByteArray
)

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
interface ComputerDao {
    @Query("SELECT * FROM computers ORDER BY host, share")
    fun observe(): Flow<List<Computer>>

    @Query("SELECT * FROM computers WHERE host = :host AND share = :share")
    suspend fun find(host: String, share: String): Computer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(computer: Computer)

    @Query("DELETE FROM computers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback WHERE position_ms > 0 ORDER BY updated_at DESC")
    fun inProgress(): Flow<List<Playback>>

    @Query("SELECT * FROM playback WHERE uri = :uri AND size = :size")
    suspend fun find(uri: String, size: Long): Playback?

    @Upsert
    suspend fun save(playback: Playback)
}

@Database(entities = [Computer::class, Playback::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun computers(): ComputerDao

    abstract fun playback(): PlaybackDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder<AppDatabase>(context, "video-player.db")
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
}
