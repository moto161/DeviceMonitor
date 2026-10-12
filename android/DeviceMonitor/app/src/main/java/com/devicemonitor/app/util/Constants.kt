package com.devicemonitor.app.util

object Constants {
    const val DEFAULT_SERVER_URL = "http://YOUR_SERVER_IP:8443/"
    const val API_KEY = "YOUR_API_KEY"
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val LIST_REFRESH_INTERVAL_MS = 30_000L
    const val DETAIL_REFRESH_INTERVAL_MS = 15_000L
    const val HISTORY_DEFAULT_HOURS = 24
    const val HISTORY_DEFAULT_LIMIT = 288
    const val HISTORY_CHART_POINTS = 120
    const val ALERTS_DEFAULT_LIMIT = 500
    const val DEVICE_TYPE_SERVER = "server"
    const val DEVICE_TYPE_ROUTER = "router"
    const val DEVICE_TYPE_PHONE = "phone"
    const val DEVICE_TYPE_PC = "pc"
    const val DEVICE_TYPE_TABLET = "tablet"
    const val STATUS_ONLINE = "online"
    const val STATUS_OFFLINE = "offline"
    const val PREFS_NAME = "device_monitor"
    const val PREF_SERVER_URL = "server_url"
    const val PAGE_PADDING_DP = 16
    const val CARD_SPACING_DP = 12
    const val CARD_PADDING_DP = 16
    const val CARD_CORNER_RADIUS_DP = 12
}
