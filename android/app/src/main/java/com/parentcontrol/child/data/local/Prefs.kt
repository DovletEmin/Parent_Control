package com.parentcontrol.child.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "parentcontrol_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, null) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var lastAppUsageSyncMs: Long
        get() = prefs.getLong(KEY_LAST_APP_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_APP_SYNC, value).apply()

    var lastCallSyncMs: Long
        get() = prefs.getLong(KEY_LAST_CALL_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CALL_SYNC, value).apply()

    var lastSmsSyncMs: Long
        get() = prefs.getLong(KEY_LAST_SMS_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SMS_SYNC, value).apply()

    var lastMediaSyncMs: Long
        get() = prefs.getLong(KEY_LAST_MEDIA_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_MEDIA_SYNC, value).apply()

    val isPaired: Boolean
        get() = deviceToken != null

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_LAST_APP_SYNC = "last_app_sync"
        private const val KEY_LAST_CALL_SYNC = "last_call_sync"
        private const val KEY_LAST_SMS_SYNC = "last_sms_sync"
        private const val KEY_LAST_MEDIA_SYNC = "last_media_sync"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
    }
}
