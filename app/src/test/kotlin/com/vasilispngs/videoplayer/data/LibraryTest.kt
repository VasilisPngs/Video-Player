package com.vasilispngs.videoplayer.data

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val library = Library(RuntimeEnvironment.getApplication())

    @Test
    fun `sorts episode numbers as numbers`() {
        assertEquals(
            listOf("s01e01.mkv", "S01E02.mkv", "S01E10.mkv"),
            listOf("S01E10.mkv", "S01E02.mkv", "s01e01.mkv").sortedWith(fileNameOrder)
        )
    }

    @Test
    fun `knows videos by their extension`() {
        assertEquals(Kind.Video, kindOf("Movie.MKV", false))
        assertEquals(Kind.Video, kindOf("Clip.m2ts", false))
        assertEquals(Kind.File, kindOf("Movie.srt", false))
        assertEquals(Kind.File, kindOf("Movie.nfo", false))
        assertEquals(Kind.Folder, kindOf("Season 1.mkv", true))
    }

    @Test
    fun `lists folders first, then every file in natural order, without hidden files`() = runTest {
        folder.newFolder("Extras")
        listOf("Movie 10.mkv", "Movie 2.mkv", "Movie 2.el.srt", "Movie 2.nfo", ".hidden").forEach(folder::newFile)

        val entries = library.list(Uri.fromFile(folder.root))

        assertEquals(listOf("Extras", "Movie 2.el.srt", "Movie 2.mkv", "Movie 2.nfo", "Movie 10.mkv"), entries.map(Entry::name))
        assertEquals(listOf(Kind.Folder, Kind.File, Kind.Video, Kind.File, Kind.Video), entries.map(Entry::kind))
    }

    @Test
    fun `finds the folder and the name of a file on a USB drive`() {
        val uri = Uri.fromFile(File("/storage/1234-5678/Σειρά/S01E01.mkv"))

        assertEquals(Uri.fromFile(File("/storage/1234-5678/Σειρά")), uri.parent)
        assertEquals("S01E01.mkv", uri.fileName)
    }
}
