package eu.kanade.tachiyomi.data.connections.discord

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy

/**
 * Owns the list of saved Discord accounts and which one is active.
 *
 * Storage: the account list (including tokens) is serialized to JSON, encrypted with
 * [DiscordAccountCrypto], and kept behind a single string preference - never touched directly,
 * always through [accounts]/[addAccount]/[removeAccount]/[setActiveAccount]. See AGENTS.md:
 * this replaces the reference implementation's plaintext-JSON-in-preferences approach.
 *
 * [DiscordRpcService] (Agent 1) only ever sees [activeToken] - it has no reason to know about
 * multi-account at all.
 */
class DiscordAccountManager {

    private val preferenceStore: PreferenceStore by injectLazy()

    private val json = Json { ignoreUnknownKeys = true }

    private val accountsPref = preferenceStore.getString(PREF_KEY_ACCOUNTS_ENCRYPTED, "")

    fun accounts(): List<DiscordAccount> {
        val encrypted = accountsPref.get()
        if (encrypted.isBlank()) return emptyList()
        val plaintext = DiscordAccountCrypto.decrypt(encrypted) ?: return emptyList()
        return try {
            json.decodeFromString<List<DiscordAccount>>(plaintext)
        } catch (e: Exception) {
            Logger.w(LOG_TAG) { "Failed to parse stored accounts, treating as empty: ${e.message}" }
            emptyList()
        }
    }

    fun activeAccount(): DiscordAccount? = accounts().find { it.isActive }

    /** The active account's token, or null if there's no active (or no) account - the only thing Agent 1's service needs. */
    fun activeToken(): String? = activeAccount()?.token

    fun changes() = accountsPref.changes()

    /**
     * Adds [account], or replaces the existing entry with the same [DiscordAccount.id] if one
     * exists (e.g. re-logging in to refresh a token). If [account] is active, every other saved
     * account is marked inactive so there's always at most one active account.
     */
    fun addAccount(account: DiscordAccount) {
        val current = accounts().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == account.id }

        if (account.isActive) {
            for (i in current.indices) current[i] = current[i].copy(isActive = false)
        }

        if (existingIndex >= 0) {
            current[existingIndex] = account
        } else {
            current.add(account)
        }

        save(current)
    }

    fun removeAccount(accountId: String) {
        val current = accounts().toMutableList()
        val wasActive = current.find { it.id == accountId }?.isActive == true
        current.removeAll { it.id == accountId }

        // If we just removed the active account, fall back to the first remaining one (if any)
        // so the user isn't silently left with RPC enabled but no active account.
        if (wasActive && current.isNotEmpty()) {
            current[0] = current[0].copy(isActive = true)
        }

        save(current)
    }

    fun setActiveAccount(accountId: String) {
        val current = accounts().toMutableList()
        for (i in current.indices) current[i] = current[i].copy(isActive = current[i].id == accountId)
        save(current)
    }

    private fun save(accounts: List<DiscordAccount>) {
        val plaintext = json.encodeToString(accounts)
        accountsPref.set(DiscordAccountCrypto.encrypt(plaintext))
    }

    companion object {
        private const val LOG_TAG = "DiscordAccountManager"
        private const val PREF_KEY_ACCOUNTS_ENCRYPTED = "pref_discord_rpc_accounts_encrypted"
    }
}
