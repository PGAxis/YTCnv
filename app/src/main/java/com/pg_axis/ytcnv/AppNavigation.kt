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
import com.pg_axis.ytcnv.side_pages.FormatInfoScreen
import com.pg_axis.ytcnv.side_pages.HistoryScreen

@Composable
fun AppNavigation(initialUrl: String? = null) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val application = LocalContext.current.applicationContext as Application

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            mainViewModel.onUrlChanged(initialUrl)
        }
    }

    fun popBack() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    fun popBackToMain() {
        navController.popBackStack("main", inclusive = false)
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            LockPortrait()
            MainScreen(
                viewModel = mainViewModel,
                onOpenSearch = { navController.navigate("search") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenHistory = { navController.navigate("history") },
                onOpenInfo = { navController.navigate("info") },
            )
        }
        composable("search") {
            LockPortrait()
            SearchScreen(
                onBack = { popBack() },
                onEnqueue = { video -> mainViewModel.enqueueVideo(video) },
                onPreviewVideo = { videoId -> navController.navigate("preview/$videoId") }
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
            val previewViewModel = remember {
                PreviewViewModel(videoId, application)
            }
            PreviewScreen(
                onBack = { popBack() },
                viewModel = previewViewModel
            )
        }
        composable("history") {
            HistoryScreen(
                onBack = { popBack() },
                onResultSelected = { url ->
                    mainViewModel.onUrlChanged(url)
                    popBackToMain()
                }
            )
        }
        composable("info") {
            FormatInfoScreen {
                popBack()
            }
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