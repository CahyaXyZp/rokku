package eu.kanade.tachiyomi.data.connections.discord

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import uy.kohesive.injekt.injectLazy

/**
 * Confirms a token actually authenticates with Discord before [DiscordAccountManager] saves it.
 *
 * The reference implementation only checked the token against a regex
 * (`^[\w-]{24}\.[\w-]{6}\.[\w-]{27}\w+?$`) and hit `/users/@me` solely to *decorate* the account
 * with a username/avatar - a syntactically valid but revoked/expired/wrong token would still get
 * saved and silently fail later inside the gateway connection. This makes that same call the
 * actual gate: a non-2xx response is treated as an invalid login, nothing gets saved, and the
 * user sees a real error instead of a Rich Presence that quietly never connects.
 */
class DiscordUserValidator {

    private val networkHelper: NetworkHelper by injectLazy()
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Success(val id: String, val username: String, val avatarUrl: String?) : Result
        data object InvalidToken : Result
        data class NetworkError(val message: String?) : Result
    }

    suspend fun validate(token: String): Result {
        // Cheap shape check first so we don't burn a network call on obvious garbage (e.g. an
        // empty string from a failed WebView extraction) - not a substitute for the API call.
        if (!TOKEN_SHAPE.matches(token)) return Result.InvalidToken

        return try {
            val response = networkHelper.client.newCall(
                GET(
                    "$DISCORD_API_BASE/users/@me",
                    headers = Headers.Builder().add("Authorization", token).build(),
                ),
            ).await()

            response.use {
                if (!it.isSuccessful) {
                    return if (it.code == 401) Result.InvalidToken else Result.NetworkError("HTTP ${it.code}")
                }

                val body = it.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return Result.InvalidToken
                val username = obj["username"]?.jsonPrimitive?.content ?: return Result.InvalidToken
                val avatarHash = obj["avatar"]?.jsonPrimitive?.contentOrNull

                Result.Success(
                    id = id,
                    username = username,
                    avatarUrl = avatarHash?.let { hash -> "$DISCORD_CDN_BASE/avatars/$id/$hash.png" },
                )
            }
        } catch (e: Exception) {
            Result.NetworkError(e.message)
        }
    }

    private companion object {
        private val TOKEN_SHAPE = Regex("""^[\w-]{24,}\.[\w-]{6,}\.[\w-]{27,}$""")
        private const val DISCORD_API_BASE = "https://discord.com/api/v10"
        private const val DISCORD_CDN_BASE = "https://cdn.discordapp.com"
    }
}
