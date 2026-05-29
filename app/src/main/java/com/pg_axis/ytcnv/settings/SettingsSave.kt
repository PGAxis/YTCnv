package com.pg_axis.ytcnv.settings

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pg_axis.ytcnv.services.Theme
import dev.pgaxis.axs.AxsBoundObject
import dev.pgaxis.axs.AxsFile
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

class SettingsSave private constructor(context: Context) : ISettings {

    companion object {
        @Volatile
        private var instance: SettingsSave? = null

        fun getInstance(context: Context): SettingsSave =
            instance ?: synchronized(this) {
                instance ?: SettingsSave(context.applicationContext).also { instance = it }
            }
    }

    private val axsPath = context.filesDir.resolve("settings.axs").path

    // --- AXS setup ---
    private val axsFile = AxsFile(axsPath)
    private lateinit var boundSettings: AxsBoundObject<SettingsClass>
    private lateinit var boundExtraData: AxsBoundObject<ExtraData>

    // --- Setting fun ---
    private fun <V : Any> setting(
        initial: V,
        prop: KMutableProperty1<SettingsClass, V>
    ): ReadWriteProperty<Any?, V> = object : ReadWriteProperty<Any?, V> {
        private var state by mutableStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): V = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            state = value
            if (::boundSettings.isInitialized) boundSettings.setValue(prop, value)
        }
    }

    private fun intSetting(
        initial: Int,
        prop: KMutableProperty1<SettingsClass, Int>
    ): ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
        private var state by mutableIntStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state = value
            if (::boundSettings.isInitialized) boundSettings.setValue(prop, value)
        }
    }

    private fun <V : Any> extraData(
        initial: V,
        prop: KMutableProperty1<ExtraData, V>
    ): ReadWriteProperty<Any?, V> = object : ReadWriteProperty<Any?, V> {
        private var state by mutableStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): V = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            state = value
            if (::boundExtraData.isInitialized) boundExtraData.setValue(prop, value)
        }
    }

    // --- Settings ---
    override var use4K by setting(false, SettingsClass::use4kDownload)
    override var quickDwnld by setting(true, SettingsClass::quickDownload)
    override var muxedFallback by setting(false, SettingsClass::muxedFallback)
    override var dontShowUpdate by setting(false, SettingsClass::dontShowUpdatePopup)
    override var termsAccepted by setting(false, SettingsClass::termsAccepted)
    override var fileUri by setting("", SettingsClass::savedFileUri)
    override var notifyOnFinish by setting(true, SettingsClass::notifyOnFinish)
    override var notifyOnFail by setting(true, SettingsClass::notifyOnFail)
    override var addToMusicAxs by setting(false, SettingsClass::addToMusicAxs)
    override var minResolution by intSetting(480, SettingsClass::minResolution)
    override var theme by setting(Theme.CYAN, SettingsClass::theme)

    // --- Extra data ---
    override var searchHistory by extraData(emptyList(), ExtraData::searchHistory)
    override var downloadHistory by extraData(emptyList(), ExtraData::downloadHistory)

    // --- Singleton variables ---
    override var isDownloadRunning = false
    override var alreadyShown = false
    override var iHaveId = false
    override var id = ""

    // --- Data classes ---
    @Keep
    data class SettingsClass(
        var use4kDownload: Boolean = false,
        var quickDownload: Boolean = true,
        var muxedFallback: Boolean = false,
        var dontShowUpdatePopup: Boolean = false,
        var termsAccepted: Boolean = false,
        var savedFileUri: String = "",
        var notifyOnFinish: Boolean = true,
        var notifyOnFail: Boolean = true,
        var addToMusicAxs: Boolean = false,
        var minResolution: Int = 480,
        var theme: Theme = Theme.CYAN,
    )

    @Keep
    data class HistoryItem(
        var title: String = "",
        var urlOrId: String = ""
    )

    @Keep
    data class ExtraData(
        var searchHistory: List<String> = emptyList(),
        var downloadHistory: List<HistoryItem> = emptyList()
    )

    init {
        axsFile.open()

        boundSettings = axsFile.bind(SettingsClass())

        // Load settings into delegates
        val s = boundSettings.get()
        use4K = s.use4kDownload
        quickDwnld = s.quickDownload
        dontShowUpdate = s.dontShowUpdatePopup
        termsAccepted = s.termsAccepted
        fileUri = s.savedFileUri
        notifyOnFinish = s.notifyOnFinish
        notifyOnFail = s.notifyOnFail
        addToMusicAxs = s.addToMusicAxs
        minResolution = s.minResolution
        theme = s.theme

        boundExtraData = axsFile.bind(ExtraData())

        val e = boundExtraData.get()
        searchHistory = e.searchHistory
        downloadHistory = e.downloadHistory
    }
}