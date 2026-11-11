package com.example.commentanalyzer.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureCredentialStore(context: Context) {
    private val sharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_credentials_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(keyName: String, keyVal: String) {
        sharedPreferences.edit().putString(keyName, keyVal).apply()
    }

    fun getApiKey(keyName: String): String? {
        return sharedPreferences.getString(keyName, null)
    }

    fun clearApiKey(keyName: String) {
        sharedPreferences.edit().remove(keyName).apply()
    }

    fun saveBoolean(keyName: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(keyName, value).apply()
    }

    fun getBoolean(keyName: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(keyName, defaultValue)
    }
}
