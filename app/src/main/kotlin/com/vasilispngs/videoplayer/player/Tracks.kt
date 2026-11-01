package com.vasilispngs.videoplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.vasilispngs.videoplayer.data.GREEK
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SubtitleLanguage

data class TrackChoice(val audio: Tracks.Group?, val text: Tracks.Group?)

val Tracks.audio: List<Tracks.Group>
    get() = groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }

val Tracks.text: List<Tracks.Group>
    get() = groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

val Tracks.Group.id: String
    @OptIn(UnstableApi::class)
    get() = mediaTrackGroup.id

val Tracks.Group.language: String?
    get() = getTrackFormat(0).language?.substringBefore('-')

val Tracks.Group.forced: Boolean
    get() = (getTrackFormat(0).selectionFlags and C.SELECTION_FLAG_FORCED) != 0

fun chooseTracks(tracks: Tracks, settings: PlaybackSettings, saved: Playback?): TrackChoice {
    val audio = tracks.audio
    val text = tracks.text
    val chosenAudio = saved?.audioTrack?.let { id -> audio.firstOrNull { it.id == id } }
        ?: settings.audioLanguage.code?.let { code -> audio.firstOrNull { it.language == code } }
        ?: audio.firstOrNull()
    val chosenText = if (saved?.textChosen == true) {
        saved.textTrack?.let { id -> text.firstOrNull { it.id == id } }
    } else {
        when (settings.subtitleLanguage) {
            SubtitleLanguage.Off -> null
            SubtitleLanguage.Greek if chosenAudio?.language == GREEK -> text.firstOrNull { it.language == GREEK && it.forced }
            else -> text.filter { it.language == settings.subtitleLanguage.code }.let { matches -> matches.firstOrNull { !it.forced } ?: matches.firstOrNull() }
        }
    }
    return TrackChoice(chosenAudio, chosenText)
}

fun Player.select(choice: TrackChoice) {
    trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
        choice.audio?.let { setOverrideForType(it.override()) }
        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, choice.text == null)
        choice.text?.let { setOverrideForType(it.override()) }
    }.build()
}

fun Player.selectAudio(group: Tracks.Group) {
    trackSelectionParameters = trackSelectionParameters.buildUpon().setOverrideForType(group.override()).build()
}

fun Player.selectText(group: Tracks.Group?) {
    trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, group == null)
        group?.let { setOverrideForType(it.override()) }
    }.build()
}

private fun Tracks.Group.override() =
    TrackSelectionOverride(mediaTrackGroup, (0 until length).firstOrNull(::isTrackSupported) ?: 0)
