package com.pg_axis.ytcnv.settings

interface ISettings {
    var use4K: Boolean
    var quickDwnld: Boolean
    var downloadHistory: List<SettingsSave.HistoryItem>
    var searchHistory: List<String>
    var fileUri: String
    var isDownloadRunning: Boolean
    var iHaveId: Boolean
    var id: String
    var dontShowUpdate: Boolean
    var termsAccepted: Boolean
    var alreadyShown: Boolean
    var notifyOnFinish: Boolean
    var notifyOnFail: Boolean
    var addToMusicAxs: Boolean
    var minResolution: Int
}