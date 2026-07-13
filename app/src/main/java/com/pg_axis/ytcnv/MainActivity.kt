package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pg_axis.ytcnv.ui.theme.YTCnvTheme
import org.schabi.newpipe.extractor.NewPipe
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.pg_axis.ytcnv.ui.theme.*
import com.pg_axis.ytcnv.services.Theme
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.utils.NewPipeDownloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        NewPipe.init(
            NewPipeDownloader(),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
        enableEdgeToEdge()

        val sharedUrl = extractSharedUrl(intent)

        setContent {
            val settings = remember { SettingsSave.getInstance(this) }
            val colorScheme = when (settings.theme) {
                Theme.CYAN -> YTCnvCyanScheme
                Theme.GRAYSCALE -> YTCnvGrayscaleScheme
                Theme.EMBER -> YTCnvEmberScheme
                Theme.AETHER -> YTCnvAetherScheme
                Theme.PHOSPHOR -> YTCnvPhosphorScheme
                Theme.CHALK -> YTCnvChalkScheme
                Theme.SUNSHINE -> YTCnvSoleilScheme
                Theme.BORDO ->YTCnvBordoScheme
                Theme.VOID -> YTCnvVoidScheme
            }
            YTCnvTheme(colorScheme = colorScheme) {
                AppNavigation(initialUrl = sharedUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
    
    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun YTCnvPreview() {
    YTCnvTheme(colorScheme = YTCnvCyanScheme) {
        MainScreen(viewModel = MainViewModel(Application()), {}, {}, {})
    }
}