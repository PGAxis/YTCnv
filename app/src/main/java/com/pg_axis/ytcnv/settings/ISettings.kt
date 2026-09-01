package com.pg_axis.ytcnv.settings
import com.pg_axis.ytcnv.services.Theme
import com.pg_axis.ytcnv.settings.SettingsSave.DefaultDO
interface ISettings {
    var use4K: Boolean
    var quickDwnld: Boolean
    var muxedFallback: Boolean
    var downloadHistory: List<SettingsSave.HistoryItem>
    var searchHistory: List<String>
    var fileUri: String
    var fileVidUri: String
    var isDownloadRunning: Boolean
    var iHaveId: Boolean
    var id: String
    var dontShowUpdate: Boolean
    var alreadyShown: Boolean
    var notifyOnFinish: Boolean
    var notifyOnFail: Boolean
    var addToMusicAxs: Boolean
    var minResolution: Int
    var storageMarginMb: Int
    var theme: Theme
    var defaultDO: DefaultDO
}