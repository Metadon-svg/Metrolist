package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.ui.screens.library.LocalSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val database: MusicDatabase
) : ViewModel() {

    fun importSong(localSong: LocalSong) {
        viewModelScope.launch(Dispatchers.IO) {
            // Проверяем, не импортирована ли уже эта песня
            val existingSong = database.query {
                getSongById(localSong.id)
            }
            
            if (existingSong == null) {
                // Создаём сущность для локальной песни
                val songEntity = SongEntity(
                    id = localSong.id,
                    title = localSong.title,
                    duration = localSong.duration,
                    thumbnailUrl = null, // Можно добавить логику извлечения обложки
                    albumId = null,
                    albumName = localSong.album,
                    isLocal = true,
                    inLibrary = LocalDateTime.now(),
                    dateModified = LocalDateTime.now()
                )
                
                // Добавляем в базу данных
                database.query {
                    insert(songEntity)
                }
            }
        }
    }
    
    fun importAllSongs(songs: List<LocalSong>) {
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                importSong(song)
            }
        }
    }
}
