/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.metrolist.music.viewmodels

import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.AddToPlaylistSortDescendingKey
import com.metrolist.music.constants.AddToPlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Suspend function that waits for sync to complete
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }

    fun importLocalMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = mutableListOf<SongEntity>()
            val artists = mutableListOf<ArtistEntity>()
            val albums = mutableListOf<AlbumEntity>()
            val songArtistMaps = mutableListOf<SongArtistMap>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol).toString()
                        val title = cursor.getString(titleCol) ?: "Unknown"
                        val artistName = cursor.getString(artistCol) ?: "Unknown Artist"
                        val albumName = cursor.getString(albumCol) ?: "Unknown Album"
                        val duration = (cursor.getLong(durationCol) / 1000).toInt()
                        val artistIdRaw = cursor.getLong(artistIdCol)
                        val albumIdRaw = cursor.getLong(albumIdCol)
                        val path = cursor.getString(dataCol)

                        val songId = "local_song_$id"
                        val artistId = "local_artist_$artistIdRaw"
                        val albumId = "local_album_$albumIdRaw"

                        val songEntity = SongEntity(
                            id = songId,
                            title = title,
                            duration = duration,
                            thumbnailUrl = null,
                            albumId = albumId,
                            albumName = albumName,
                            isLocal = true,
                            inLibrary = LocalDateTime.now(),
                            totalPlayTime = 0
                        )

                        val artistEntity = ArtistEntity(
                            id = artistId,
                            name = artistName,
                            isLocal = true,
                            lastUpdateTime = LocalDateTime.now()
                        )

                        if (albums.none { it.id == albumId }) {
                            val albumEntity = AlbumEntity(
                                id = albumId,
                                title = albumName,
                                songCount = 1,
                                duration = 0,
                                isLocal = true,
                                lastUpdateTime = LocalDateTime.now()
                            )
                            albums.add(albumEntity)
                        }

                        if (artists.none { it.id == artistId }) {
                            artists.add(artistEntity)
                        }

                        songs.add(songEntity)
                        songArtistMaps.add(SongArtistMap(songId, artistId, 0))
                    }
                }

                if (songs.isNotEmpty()) {
                    database.transaction {
                        artists.forEach { insert(it) }
                        albums.forEach { insert(it) }
                        songs.forEach { insert(it) }
                        songArtistMaps.forEach { insert(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
