package com.metrolist.music.ui.screens.library

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime

data class LocalSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Int,
    val uri: String,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    navController: NavController,
    viewModel: LocalMusicViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var localSongs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Проверка разрешения
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            localSongs = scanLocalMusic(context)
        }
    }

    // Launcher для запроса разрешения
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            localSongs = scanLocalMusic(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.local_music)) },
                actions = {
                    if (hasPermission) {
                        IconButton(
                            onClick = {
                                isScanning = true
                                localSongs = scanLocalMusic(context)
                                isScanning = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !hasPermission -> {
                    // Экран запроса разрешения
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.permission_required),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.permission_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permission) }
                        ) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
                isScanning -> {
                    // Индикатор загрузки
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                localSongs.isEmpty() -> {
                    // Пустой список
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_local_music_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                else -> {
                    // Список песен
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(localSongs) { song ->
                            LocalSongItem(
                                song = song,
                                onClick = {
                                    // Импортировать песню в базу данных
                                    viewModel.importSong(song)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalSongItem(
    song: LocalSong,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(song.title) },
        supportingContent = { 
            Text("${song.artist} • ${formatDuration(song.duration)}")
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

fun scanLocalMusic(context: android.content.Context): List<LocalSong> {
    val songs = mutableListOf<LocalSong>()
    
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )
    
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    
    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )
    
    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        
        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val title = it.getString(titleColumn) ?: "Unknown"
            val artist = it.getString(artistColumn) ?: "Unknown Artist"
            val album = it.getString(albumColumn)
            val duration = (it.getLong(durationColumn) / 1000).toInt() // Convert to seconds
            val path = it.getString(dataColumn)
            
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            ).toString()
            
            songs.add(
                LocalSong(
                    id = "local_$id",
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = uri,
                    path = path
                )
            )
        }
    }
    
    return songs
}
