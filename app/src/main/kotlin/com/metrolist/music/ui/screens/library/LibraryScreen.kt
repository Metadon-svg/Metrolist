package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.ui.component.NavigationTabRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
) {
    val tabNames = listOf(
        R.string.songs,
        R.string.artists,
        R.string.albums,
        R.string.playlists,
    )

    val pagerState = rememberPagerState(pageCount = { tabNames.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        NavigationTabRow(
            titles = tabNames.map { stringResource(it) },
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> LibrarySongsScreen(
                    navController = navController,
                    onDeselect = { /* Handle deselect if needed */ }
                )
                1 -> LibraryArtistsScreen(navController = navController)
                2 -> LibraryAlbumsScreen(navController = navController)
                3 -> LibraryPlaylistsScreen(navController = navController)
            }
        }
    }
}
