package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pg_axis.ytcnv.side_pages.HistoryScreen

@Composable
fun AppNavigation(initialUrl: String? = null) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val application = LocalContext.current.applicationContext as Application

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            mainViewModel.urlEntryText = initialUrl
        }
    }

    fun popBack() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            LockPortrait()
            MainScreen(
                viewModel = mainViewModel,
                onOpenSearch = { navController.navigate("search") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenHistory = { navController.navigate("history") }
            )
        }
        composable("search") {
            LockPortrait()
            val searchViewModel = viewModel<SearchViewModel>(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SearchViewModel(mainViewModel.settings) as T
                    }
                }
            )
            SearchScreen(
                onBack = { popBack() },
                onResultSelected = { url ->
                    mainViewModel.urlEntryText = url
                    if (mainViewModel.downloadButtonIsVisible && !mainViewModel.settings.quickDwnld) {
                        mainViewModel.downloadButtonIsVisible = false
                        mainViewModel.loadButtonIsVisible = true
                        mainViewModel.loadButtonIsEnabled = true
                    }
               },
                onPreviewVideo = { videoId -> navController.navigate("preview/$videoId") },
                viewModel = searchViewModel
            )
        }
        composable("settings") {
            LockPortrait()
            val settingsViewModel = remember {
                SettingsViewModel(mainViewModel, application)
            }
            SettingsScreen(
                onBack = { popBack() },
                viewModel = settingsViewModel
            )
        }
        composable("preview/{videoId}") { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val previewViewModel = viewModel<PreviewViewModel>(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return PreviewViewModel(videoId, application) as T
                    }
                }
            )
            PreviewScreen(
                onBack = { popBack() },
                viewModel = previewViewModel
            )
        }
        composable("history") {
            HistoryScreen(
                onBack = { popBack() }
            )
        }
    }
}

@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun LockPortrait() {
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }
}