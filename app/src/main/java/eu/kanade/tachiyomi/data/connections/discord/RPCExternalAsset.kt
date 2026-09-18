// Adapted from Komikku for Rokku
package eu.kanade.tachiyomi.data.connections.discord

import co.touchlab.kermit.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Resolves an external image URL into a Discord Rich Presence asset path ("mp:...") via
 * Discord's external-assets endpoint. Rich Presence can't display a raw image URL directly -
 * it has to be exchanged for one of these paths first, authenticated with an account token.
 */
class RPCExternalAsset(
    applicationId: String,
    private val token: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    @Serializable
    data class ExternalAsset(
        val url: String? = null,
        @SerialName("external_asset_path")
        val externalAssetPath: String? = null,
    )

    private val api = "https://discord.com/api/v10/applications/$applicationId/external-assets"

    suspend fun getDiscordUri(imageUrl: String): String? {
        if (imageUrl.startsWith("mp:")) return imageUrl
        val request = Request.Builder()
            .url(api)
            .addHeader("Authorization", token)
            .post("{\"urls\":[\"$imageUrl\"]}".toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            val response = client.newCall(request).await()
            if (!response.isSuccessful) {
                Logger.e { "Discord external-assets request failed: HTTP ${response.code}" }
                return null
            }
            val body = response.body?.string() ?: return null
            json.decodeFromString<List<ExternalAsset>>(body)
                .firstOrNull()?.externalAssetPath?.let { "mp:$it" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to resolve Discord external asset" }
            null
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            },
        )
        cont.invokeOnCancellation { cancel() }
    }
}
