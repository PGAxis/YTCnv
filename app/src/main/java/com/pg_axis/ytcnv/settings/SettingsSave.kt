package com.pg_axis.ytcnv.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

class SettingsSave private constructor(context: Context) : ISettings {

    companion object {
        @Volatile
        private var instance: SettingsSave? = null

        fun getInstance(context: Context): SettingsSave =
            instance ?: synchronized(this) {
                instance ?: SettingsSave(context.applicationContext).also { instance = it }
            }
    }

    private val settingsPath = context.filesDir.resolve("settings.json")
    private val extraDataPath = context.filesDir.resolve("extra_data.json")
    private val gson = Gson()

    // ─── Settings ───
    override var use4K by mutableStateOf(false)

    override var quickDwnld by mutableStateOf(true)

    override var dontShowUpdate by mutableStateOf(false)

    override var termsAccepted by mutableStateOf(false)

    override var fileUri by mutableStateOf("")

    override var notifyOnFinish by mutableStateOf(true)

    override var notifyOnFail by mutableStateOf(true)

    override var addToMusicAxs by mutableStateOf(false)

    // ─── Extra data ───
    override var searchHistory by mutableStateOf<List<String>>(emptyList())

    override var downloadHistory by mutableStateOf<List<HistoryItem>>(emptyList())

    // ─── Singleton variables ───
    override var isDownloadRunning = false
    override var alreadyShown = false
    override var iHaveId = false
    override var id = ""

    // ─── Save/Load ───
    fun saveSettings() {
        val settings = SettingsClass(
            use4kDownload = use4K,
            quickDownload = quickDwnld,
            dontShowUpdatePopup = dontShowUpdate,
            termsAccepted = termsAccepted,
            savedFileUri = fileUri,
            notifyOnFinish = notifyOnFinish,
            notifyOnFail = notifyOnFail,
            addToMusicAxs = addToMusicAxs
        )
        settingsPath.writeText(gson.toJson(settings))
    }

    fun saveExtraData() {
        val extraData = ExtraData(
            searchHistory = searchHistory,
            downloadHistory = downloadHistory
        )
        extraDataPath.writeText(gson.toJson(extraData))
    }

    fun loadSettings() {
        if (settingsPath.exists()) {
            gson.fromJson(settingsPath.readText(), SettingsClass::class.java)?.let {
                use4K = it.use4kDownload
                quickDwnld = it.quickDownload
                dontShowUpdate = it.dontShowUpdatePopup
                termsAccepted = it.termsAccepted
                fileUri = it.savedFileUri ?: ""
                notifyOnFinish = it.notifyOnFinish
                notifyOnFail = it.notifyOnFail
                addToMusicAxs = it.addToMusicAxs
            }
        }
        if (extraDataPath.exists()) {
            try {
                gson.fromJson(extraDataPath.readText(), ExtraData::class.java)?.let {
                    searchHistory = it.searchHistory
                    downloadHistory = it.downloadHistory
                }
            } catch (_: Exception) {
                searchHistory = emptyList()
                downloadHistory = emptyList()
            }
        }
    }

    // ─── Data classes ───
    data class SettingsClass(
        val use4kDownload: Boolean = false,
        val quickDownload: Boolean = true,
        val dontShowUpdatePopup: Boolean = false,
        val termsAccepted: Boolean = false,
        val savedFileUri: String? = null,
        val notifyOnFinish: Boolean = true,
        val notifyOnFail: Boolean = true,
        val addToMusicAxs: Boolean = false,
    )

    data class ExtraData(
        val searchHistory: List<String> = emptyList(),
        val downloadHistory: List<HistoryItem> = emptyList()
    )

    data class HistoryItem(
        val title: String,
        val urlOrId: String
    )

    init {
        loadSettings()
    }
}