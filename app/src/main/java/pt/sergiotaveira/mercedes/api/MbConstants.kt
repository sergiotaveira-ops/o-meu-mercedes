package pt.sergiotaveira.mercedes.api

/**
 * Values copied from mbapi2020 (MIT) const.py / app_version.py, Europe profile.
 * When Mercedes changes something, this is the first file to update.
 */
object MbConstants {
    const val LOGIN_APP_ID = "62778dc4-1de3-44f4-af95-115f06a3a008"
    const val REDIRECT_URI = "rismycar://login-callback"
    const val SCOPE = "email profile ciam-uid phone openid offline_access"
    const val RIS_APPLICATION_VERSION = "1.73.0"
    const val RIS_SDK_VERSION = "4.20.1"
    const val RIS_OS_VERSION = "26.3"
    const val RIS_OS_NAME = "ios"
    const val APPLICATION_NAME = "mycar-store-ece"
    const val LOCALE = "en-GB"
    const val APP_USER_AGENT = "Mercedes-Benz/3044 CFNetwork/3860.400.22 Darwin/25.3.0"
    const val SAFARI_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 15_8_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6.6 Mobile/15E148 Safari/604.1"
}

data class MbUrls(val login: String, val rest: String, val websocket: String) {
    companion object {
        val EMEA = MbUrls(
            login = "https://id.mercedes-benz.com",
            rest = "https://bff.emea-prod.mobilesdk.mercedes-benz.com",
            websocket = "wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws",
        )
    }
}
