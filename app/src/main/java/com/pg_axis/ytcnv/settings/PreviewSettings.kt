package com.pg_axis.ytcnv.settings

import com.pg_axis.ytcnv.services.Theme

class PreviewSettings : ISettings {
    override var use4K = false
    override var quickDwnld = true
    override var muxedFallback = false
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
    override var addToMusicAxs = false
    override var minResolution = 480
    override var theme = Theme.CYAN
}