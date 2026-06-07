package com.linguawonder.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * End-to-End Encryption handler for conversation mode
 * Implements AES-256-GCM for message encryption and RSA for key exchange
 */
class ConversationSecurity {

    companion object {
        private const val KEYSTORE_ALIAS = "LinguaLinkE2EKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val RSA_MODE = "RSA/ECB/PKCS1Padding"
        private const val AES_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Initialize encryption keys in Android Keystore
     * This ensures keys are hardware-backed on supported devices
     */
    fun initializeE2EKeys() {
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            generateAESKey()
            generateRSAKeyPair()
        }
    }

    /**
     * Generate AES-256 key for symmetric encryption
     */
    private fun generateAESKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            "$KEYSTORE_ALIAS-AES",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE)
            .setUserAuthenticationRequired(false) // Set to true for additional security
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    /**
     * Generate RSA key pair for key exchange
     */
    private fun generateRSAKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE
        )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            "$KEYSTORE_ALIAS-RSA",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setKeySize(2048)
            .build()

        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Encrypt a conversation message for E2E transmission
     */
    fun encryptMessage(plaintext: String, sessionKey: ByteArray? = null): EncryptedMessage {
        try {
            // Use session key or generate one
            val secretKey = sessionKey?.let { 
                SecretKeySpec(it, "AES") 
            } ?: generateSessionKey()

            // Generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // Initialize cipher
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            // Encrypt the message
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            // Create HMAC for integrity
            val hmac = generateHMAC(ciphertext, secretKey.encoded)

            return EncryptedMessage(
                ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                hmac = Base64.encodeToString(hmac, Base64.NO_WRAP),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt message: ${e.message}")
        }
    }

    /**
     * Decrypt a received conversation message
     */
    fun decryptMessage(encryptedMessage: EncryptedMessage, sessionKey: ByteArray): String {
        try {
            // Decode from Base64
            val ciphertext = Base64.decode(encryptedMessage.ciphertext, Base64.NO_WRAP)
            val iv = Base64.decode(encryptedMessage.iv, Base64.NO_WRAP)
            val receivedHmac = Base64.decode(encryptedMessage.hmac, Base64.NO_WRAP)

            // Verify HMAC
            val calculatedHmac = generateHMAC(ciphertext, sessionKey)
            if (!receivedHmac.contentEquals(calculatedHmac)) {
                throw SecurityException("Message integrity check failed")
            }

            // Check timestamp (prevent replay attacks)
            val messageAge = System.currentTimeMillis() - encryptedMessage.timestamp
            if (messageAge > 300000) { // 5 minutes
                throw SecurityException("Message too old, possible replay attack")
            }

            // Decrypt
            val secretKey = SecretKeySpec(sessionKey, "AES")
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt message: ${e.message}")
        }
    }

    /**
     * Generate session key for conversation
     */
    fun generateSessionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE, SecureRandom())
        return keyGenerator.generateKey()
    }

    /**
     * Exchange session keys using RSA (for initial handshake)
     */
    fun encryptSessionKey(sessionKey: ByteArray, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(RSA_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedKey = cipher.doFinal(sessionKey)
        return Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
    }

    /**
     * Decrypt received session key
     */
    fun decryptSessionKey(encryptedKey: String): ByteArray {
        val privateKey = keyStore.getKey("$KEYSTORE_ALIAS-RSA", null) as PrivateKey
        val cipher = Cipher.getInstance(RSA_MODE)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedBytes = Base64.decode(encryptedKey, Base64.NO_WRAP)
        return cipher.doFinal(encryptedBytes)
    }

    /**
     * Generate HMAC for message integrity
     */
    private fun generateHMAC(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }

    /**
     * Get public key for sharing with other conversation participant
     */
    fun getPublicKey(): String {
        val publicKey = keyStore.getCertificate("$KEYSTORE_ALIAS-RSA").publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Secure WebSocket URL with authentication token
     */
    fun getSecureWebSocketUrl(baseUrl: String, userId: String): String {
        val timestamp = System.currentTimeMillis()
        val token = generateAuthToken(userId, timestamp)
        return "$baseUrl?user=$userId&token=$token&timestamp=$timestamp"
    }

    /**
     * Generate authentication token for WebSocket connection
     */
    private fun generateAuthToken(userId: String, timestamp: Long): String {
        // Use a secure key - in production, use BuildConfig or secure storage
        val secretKey = try {
            // Try to use BuildConfig if available
            javaClass.classLoader?.loadClass("com.lingualink.linguagt.BuildConfig")
                ?.getField("ENCRYPTION_KEY")?.get(null) as? String
        } catch (e: Exception) {
            null
        } ?: "DEFAULT_ENCRYPTION_KEY_CHANGE_IN_PRODUCTION"

        val data = "$userId:$timestamp:$secretKey"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /**
     * Data class for encrypted messages
     */
    data class EncryptedMessage(
        val ciphertext: String,
        val iv: String,
        val hmac: String,
        val timestamp: Long,
        val conversationId: String? = null,
        val senderId: String? = null,
        val recipientId: String? = null
    )

    /**
     * Secure conversation session management
     */
    class ConversationSession(
        val sessionId: String,
        val sessionKey: ByteArray,
        val participants: List<String>,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        private var messageCounter = 0L

        fun getNextSequenceNumber(): Long {
            return ++messageCounter
        }

        fun isExpired(): Boolean {
            val sessionAge = System.currentTimeMillis() - createdAt
            return sessionAge > 3600000 // 1 hour session timeout
        }

        fun clearSession() {
            // Securely clear session key from memory
            sessionKey.fill(0)
        }
    }
}

