package com.example.commentanalyzer.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterCredentialAndConsentTest {

    private class FakeCredentialStore {
        private val store = mutableMapOf<String, String>()
        private val booleans = mutableMapOf<String, Boolean>()

        fun saveApiKey(keyName: String, keyVal: String) {
            store[keyName] = keyVal
        }

        fun getApiKey(keyName: String): String? {
            return store[keyName]
        }

        fun clearApiKey(keyName: String) {
            store.remove(keyName)
        }

        fun saveBoolean(keyName: String, value: Boolean) {
            booleans[keyName] = value
        }

        fun getBoolean(keyName: String, defaultValue: Boolean): Boolean {
            return booleans[keyName] ?: defaultValue
        }
    }

    private class ReadinessEvaluator(private val credentialStore: FakeCredentialStore) {
        fun hasKey(keyName: String): Boolean {
            return !credentialStore.getApiKey(keyName).isNullOrBlank()
        }

        fun getConsent(consentKey: String): Boolean {
            return credentialStore.getBoolean(consentKey, false)
        }

        fun isReady(keyName: String, consentKey: String): Boolean {
            return hasKey(keyName) && getConsent(consentKey)
        }

        fun getMask(keyName: String): String {
            return if (hasKey(keyName)) "Configured (••••)" else "Not configured"
        }
    }

    @Test
    fun testReadinessRequiresBothKeyAndConsent() {
        val store = FakeCredentialStore()
        val evaluator = ReadinessEvaluator(store)

        val keyName = "openrouter_api_key"
        val consentKey = "openrouter_remote_consent"

        // Initial: neither key nor consent
        assertFalse(evaluator.hasKey(keyName))
        assertFalse(evaluator.getConsent(consentKey))
        assertFalse(evaluator.isReady(keyName, consentKey))
        assertEquals("Not configured", evaluator.getMask(keyName))

        // Key present alone -> NOT ready
        store.saveApiKey(keyName, "sk-or-v1-fakekey123456789")
        assertTrue(evaluator.hasKey(keyName))
        assertFalse(evaluator.getConsent(consentKey))
        assertFalse(evaluator.isReady(keyName, consentKey))
        assertEquals("Configured (••••)", evaluator.getMask(keyName))

        // Consent enabled alone (if key absent) -> NOT ready
        store.clearApiKey(keyName)
        store.saveBoolean(consentKey, true)
        assertFalse(evaluator.hasKey(keyName))
        assertTrue(evaluator.getConsent(consentKey))
        assertFalse(evaluator.isReady(keyName, consentKey))

        // Both key present AND consent enabled -> READY
        store.saveApiKey(keyName, "sk-or-v1-fakekey123456789")
        assertTrue(evaluator.hasKey(keyName))
        assertTrue(evaluator.getConsent(consentKey))
        assertTrue(evaluator.isReady(keyName, consentKey))

        // Revoking consent -> NOT ready
        store.saveBoolean(consentKey, false)
        assertTrue(evaluator.hasKey(keyName))
        assertFalse(evaluator.getConsent(consentKey))
        assertFalse(evaluator.isReady(keyName, consentKey))

        // Deleting key -> NOT ready
        store.saveBoolean(consentKey, true)
        store.clearApiKey(keyName)
        assertFalse(evaluator.hasKey(keyName))
        assertTrue(evaluator.getConsent(consentKey))
        assertFalse(evaluator.isReady(keyName, consentKey))
        assertEquals("Not configured", evaluator.getMask(keyName))
    }

    @Test
    fun testSecretRedactionAndSafeMask() {
        val store = FakeCredentialStore()
        val evaluator = ReadinessEvaluator(store)
        val keyName = "openrouter_api_key"
        val secretKey = "sk-or-v1-supersecretkeythutmose99"

        store.saveApiKey(keyName, secretKey)
        val mask = evaluator.getMask(keyName)

        // Ensure secret key is not in mask or stored raw in UI representation
        assertFalse(mask.contains(secretKey))
        assertEquals("Configured (••••)", mask)
    }
}
