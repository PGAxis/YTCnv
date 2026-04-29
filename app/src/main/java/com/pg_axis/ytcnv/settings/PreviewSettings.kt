package com.pg_axis.ytcnv.settings

class PreviewSettings : ISettings {
    override var use4K = false
    override var quickDwnld = true
    override var downloadHistory = emptyList<SettingsSave.HistoryItem>()
    override var searchHistory = emptyList<String>()
    override var fileUri = ""
    override var isDownloadRunning = false
    override var iHaveId = false
    override var id = ""
    override var dontShowUpdate = false
    override var termsAccepted = true
    override var alreadyShown = false
    override var notifyOnFinish = true
    override var notifyOnFail = true
}