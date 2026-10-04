package eu.kanade.tachiyomi.data.track

import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaDetailsResult
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListInterceptor
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * What a tracker knows about a manga, in the form Rokku stores it.
 *
 * @property status one of the [eu.kanade.tachiyomi.source.model.SManga] status constants.
 */
data class TrackerMangaDetails(
    val coverUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
    val status: Int? = null,
)

fun TrackService.supportsMangaDetails(): Boolean = this is Anilist || this is MyAnimeList

/**
 * Fetches the details the tracker has for the manga that [track] is linked to, or null when this
 * tracker can't provide them.
 */
suspend fun TrackService.fetchMangaDetails(track: Track): TrackerMangaDetails? = when (this) {
    is Anilist -> fetchAnilistMangaDetails(client, track.media_id)
    is MyAnimeList -> fetchMyAnimeListMangaDetails(this, track.media_id)
    else -> null
}

private const val ANILIST_API_URL = "https://graphql.anilist.co/"
private const val MAL_API_URL = "https://api.myanimelist.net/v2/manga"

private val ANILIST_DETAILS_QUERY =
    """
    query MangaDetails(${'$'}id: Int!) {
        Media(id: ${'$'}id, type: MANGA) {
            coverImage {
                extraLarge
                large
            }
            description(asHtml: false)
            genres
            status
            staff(perPage: 25, sort: RELEVANCE) {
                edges {
                    role
                    node {
                        name {
                            full
                        }
                    }
                }
            }
        }
    }
    """.trimIndent()

// Reading a manga's public details doesn't need the user to be logged in on AniList
private suspend fun fetchAnilistMangaDetails(client: OkHttpClient, mediaId: Long): TrackerMangaDetails =
    withIOContext {
        val payload = buildJsonObject {
            put("query", ANILIST_DETAILS_QUERY)
            putJsonObject("variables") {
                put("id", mediaId)
            }
        }
        client.newCall(POST(ANILIST_API_URL, body = payload.toString().toRequestBody(jsonMime)))
            .awaitSuccess()
            .parseAs<ALMangaDetailsResult>()
            .data.media
            .toTrackerDetails()
    }

private suspend fun fetchMyAnimeListMangaDetails(service: MyAnimeList, id: Long): TrackerMangaDetails =
    withIOContext {
        val client = service.client.newBuilder()
            .addInterceptor(MyAnimeListInterceptor(service))
            .build()
        val url = "$MAL_API_URL/$id".toUri().buildUpon()
            .appendQueryParameter("fields", "synopsis,main_picture,genres,authors{first_name,last_name},status")
            .build()
        client.newCall(GET(url.toString()))
            .awaitSuccess()
            .parseAs<MALMangaInfo>()
            .toTrackerDetails()
    }
