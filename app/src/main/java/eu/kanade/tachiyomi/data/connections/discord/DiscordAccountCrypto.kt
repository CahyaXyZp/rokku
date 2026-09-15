package eu.kanade.tachiyomi.data.connections.discord

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts the Discord accounts blob (which contains raw tokens) before it touches
 * [eu.kanade.tachiyomi.core.preference.PreferenceStore].
 *
 * The reference Kizzy-style implementation stores this as a plain JSON string preference -
 * that's explicitly called out as a gap in docs/discord-rpc/AGENTS.md, since a plaintext
 * pref is readable by anything with root/backup access to the app's storage. This wraps the
 * same string with AES/GCM using a key that never leaves the Android Keystore, so the token
 * can't be recovered without the device's keystore (e.g. from a raw backup file alone).
 *
 * This is encryption-at-rest for the preferences file, not a defense against a compromised/
 * rooted running instance of the app itself - nothing short of not storing the token at all
 * would be.
 */
internal object DiscordAccountCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "discord_rpc_accounts_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Encrypts [plaintext], returning a Base64 string of `iv || ciphertext` safe for a string preference. */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value produced by [encrypt]. Returns null (rather than throwing) if the value
     * is blank, malformed, or was encrypted with a key that no longer exists (e.g. the user
     * restored preferences onto a different device/keystore) - callers should treat that the
     * same as "no accounts saved" rather than crashing.
     */
    fun decrypt(encoded: String): String? {
        if (encoded.isBlank()) return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size < GCM_IV_LENGTH_BYTES) return null
            val iv = bytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH_BYTES, bytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
