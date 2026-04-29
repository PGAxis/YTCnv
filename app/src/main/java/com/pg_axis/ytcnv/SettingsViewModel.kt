package com.pg_axis.ytcnv

import android.app.Application
import android.provider.DocumentsContract
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import com.pg_axis.ytcnv.settings.SettingsSave
import java.util.Locale

class SettingsViewModel(val mainViewModel: MainViewModel, application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    val langOptions = mapOf("en" to "English", "cs" to "Čeština", "de" to "Deutch", "tr" to "Türkçe")
    var selectedLang = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .ifEmpty { Locale.getDefault().language.ifEmpty { langOptions.keys.first() } }!!

    var mainFolder by mutableStateOf(context.getString(R.string.internal_storage))
    var finalFolder by mutableStateOf(" - ${context.getString(R.string.downloads)}")

    fun initPaths() {
        if (!settings.fileUri.isBlank()) {
            mainFolder = getMainFolder(settings.fileUri)
            finalFolder = getFinalFolder(settings.fileUri)
        }
    }

    fun onUse4KChanged(value: Boolean) {
        settings.use4K = value
        settings.saveSettings()
    }
    fun onQuickDwnldChanged(value: Boolean) {
        settings.quickDwnld = value
        settings.saveSettings()
        mainViewModel.applyQuickDownloadState()
    }
    fun onDontShowUpdateChanged(value: Boolean) {
        settings.dontShowUpdate = value
        settings.saveSettings()
    }
    fun onFolderPicked(uri: String) {
        settings.fileUri = uri
        mainFolder = getMainFolder(uri)
        finalFolder = getFinalFolder(uri)
        settings.saveSettings()
    }
    fun onNotifyOnFinishChanged(value: Boolean) {
        settings.notifyOnFinish = value
        settings.saveSettings()
    }
    fun onNotifyOnFailChanged(value: Boolean) {
        settings.notifyOnFail = value
        settings.saveSettings()
    }
    fun onLanguageChange(key: String) {
        selectedLang = langOptions.getValue(key)
        Log.d("Locale Change", key)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(key)
        )
    }

    private fun getMainFolder(uri: String): String {
        val docId = DocumentsContract.getTreeDocumentId(uri.toUri())
        val parts = docId.split(':')

        return if (parts[0].equals("primary", true)) context.getString(R.string.internal_storage)
        else context.getString(R.string.sd_card)
    }

    private fun getFinalFolder(uri: String): String {
        return " - ${uri.toUri().lastPathSegment?.substringAfterLast(":")?.substringAfterLast("/")?: uri.toUri().toString()}"
    }
}