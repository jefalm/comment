package com.example.commentanalyzer.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureCredentialStore(context: Context) {
    private val sharedPreferences = try {
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
    } catch (e: Exception) {
        context.getSharedPreferences("insecure_fallback_prefs", Context.MODE_PRIVATE)
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
}
