package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.system.exitProcess

@Composable
fun AppNavigation(initialUrl: String? = null, onFinish: () -> Unit) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            mainViewModel.urlEntryText = initialUrl
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            LockPortrait()
            MainScreen(
                viewModel = mainViewModel,
                onOpenSearch = { navController.navigate("search") },
                onOpenSettings = { navController.navigate("settings") },
                onTermsDeclined = {
                    onFinish()
                    exitProcess(0)
                }
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
                onBack = { navController.popBackStack() },
                onResultSelected = { url -> mainViewModel.urlEntryText = url },
                onPreviewVideo = { videoId -> navController.navigate("preview/$videoId") },
                viewModel = searchViewModel
            )
        }
        composable("settings") {
            LockPortrait()
            val context = androidx.compose.ui.platform.LocalContext.current
            val settingsViewModel = remember {
                SettingsViewModel(mainViewModel, context.applicationContext as Application)
            }
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsViewModel
            )
        }
        composable("preview/{videoId}") { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val previewViewModel = viewModel<PreviewViewModel>(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return PreviewViewModel(videoId) as T
                    }
                }
            )
            PreviewScreen(
                onBack = { navController.popBackStack() },
                viewModel = previewViewModel
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