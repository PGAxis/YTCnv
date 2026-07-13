package com.pg_axis.ytcnv

import android.app.Application
import android.provider.DocumentsContract
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import com.pg_axis.ytcnv.services.Theme
import com.pg_axis.ytcnv.settings.SettingsSave
import java.util.Locale

class SettingsViewModel(val mainViewModel: MainViewModel, application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    val themeOptions = mapOf(
        Theme.CYAN to context.getString(R.string.set_vm_cyan),
        Theme.EMBER to context.getString(R.string.set_vm_ember),
        Theme.AETHER to context.getString(R.string.set_vm_aether),
        Theme.PHOSPHOR to context.getString(R.string.set_vm_phosphor),
        Theme.BORDO to context.getString(R.string.set_vm_bordo),
        Theme.VOID to context.getString(R.string.set_vm_void),
        Theme.CHALK to context.getString(R.string.set_vm_chalk),
        Theme.SUNSHINE to context.getString(R.string.set_vm_sunshine),
        Theme.GRAYSCALE to context.getString(R.string.set_vm_grayscale)
    )
    var selectedTheme by mutableStateOf(settings.theme)

    val langOptions = mapOf("en" to "English", "cs" to "Čeština", "de" to "Deutch", "tr" to "Türkçe", "ru" to "Русский")
    var selectedLang = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .ifEmpty { Locale.getDefault().language.ifEmpty { langOptions.keys.first() } }!!

    val resolutionOptions = mapOf(144 to "144p", 240 to "240p", 360 to "360p", 480 to "480p", 720 to "720p", 1080 to "1080p", 1440 to "1440p", 2160 to "4K")
    var selectedRes by mutableIntStateOf(settings.minResolution)

    var mainFolder by mutableStateOf(context.getString(R.string.internal_storage))
    var finalFolder by mutableStateOf(context.getString(R.string.downloads))

    var mainVidFolder by mutableStateOf(context.getString(R.string.internal_storage))
    var finalVidFolder by mutableStateOf(context.getString(R.string.downloads))

    fun initPaths() {
        if (!settings.fileUri.isBlank()) {
            mainFolder = getMainFolder(settings.fileUri)
            finalFolder = getFinalFolder(settings.fileUri)
        }
        if (!settings.fileVidUri.isBlank()) {
            mainVidFolder = getMainFolder(settings.fileVidUri)
            finalVidFolder = getFinalFolder(settings.fileVidUri)
        }
    }

    fun onUse4KChanged(value: Boolean) {
        settings.use4K = value
    }
    fun onQuickDwnldChanged(value: Boolean) {
        settings.quickDwnld = value
        mainViewModel.applyQuickDownloadState()
    }
    fun onMuxedChanged(value: Boolean) {
        settings.muxedFallback = value
    }
    fun onDontShowUpdateChanged(value: Boolean) {
        settings.dontShowUpdate = value
    }
    fun onFolderPicked(uri: String) {
        settings.fileUri = uri
        mainFolder = getMainFolder(uri)
        finalFolder = getFinalFolder(uri)
    }
    fun onVidFolderPicked(uri: String) {
        settings.fileVidUri = uri
        mainVidFolder = getMainFolder(uri)
        finalVidFolder = getFinalFolder(uri)
    }
    fun onNotifyOnFinishChanged(value: Boolean) {
        settings.notifyOnFinish = value
    }
    fun onNotifyOnFailChanged(value: Boolean) {
        settings.notifyOnFail = value
    }
    fun onMusicAxsChanged(value: Boolean) {
        settings.addToMusicAxs = value
    }
    fun onLanguageChange(key: String) {
        selectedLang = key
        Log.d("Locale Change", key)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(key)
        )
    }
    fun onResolutionChange(key: Int) {
        selectedRes = key
        settings.minResolution = key
    }
    fun onThemeChanged(key: Theme) {
        selectedTheme = key
        settings.theme = key
    }

    private fun getMainFolder(uri: String): String {
        val docId = DocumentsContract.getTreeDocumentId(uri.toUri())
        val parts = docId.split(':')

        return if (parts[0].equals("primary", true)) context.getString(R.string.internal_storage)
        else context.getString(R.string.sd_card)
    }

    private fun getFinalFolder(uri: String): String {
        return uri.toUri().lastPathSegment?.substringAfterLast(":")?.substringAfterLast("/")?: uri.toUri().toString()
    }
}