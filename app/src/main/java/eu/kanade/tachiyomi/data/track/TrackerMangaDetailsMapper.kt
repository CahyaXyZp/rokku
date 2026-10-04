package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaDetailsResult
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaInfo
import eu.kanade.tachiyomi.source.model.SManga

private val BREAK_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]+>")
private val MAL_SIGNATURE = Regex("\\s*\\[Written by MAL Rewrite]\\s*$")
private val BLANK_LINES = Regex("\n{3,}")

/**
 * Trackers return descriptions with a bit of HTML in them. This turns them into plain text.
 */
internal fun cleanTrackerDescription(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
        .replace(BREAK_TAG, "\n")
        .replace(ANY_TAG, "")
        .replace(MAL_SIGNATURE, "")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(BLANK_LINES, "\n\n")
        .trim()
        .ifBlank { null }
}

/**
 * Splits credits into the author (story) and the artist (art) by their role. "Story & Art" counts
 * for both.
 */
internal fun splitCreators(credits: List<Pair<String, String?>>): Pair<String?, String?> {
    fun names(matches: (String) -> Boolean) = credits
        .filter { (_, role) -> role != null && matches(role.lowercase()) }
        .map { it.first }
        .distinct()
        .joinToString(", ")
        .ifBlank { null }

    val author = names { it.contains("story") }
    val artist = names { it.contains("art") || it.contains("illustrat") }
    return author to artist
}

internal fun anilistStatusToSManga(status: String?): Int? = when (status) {
    "RELEASING" -> SManga.ONGOING
    "FINISHED" -> SManga.COMPLETED
    "CANCELLED" -> SManga.CANCELLED
    "HIATUS" -> SManga.ON_HIATUS
    "NOT_YET_RELEASED" -> SManga.UNKNOWN
    else -> null
}

internal fun malStatusToSManga(status: String?): Int? = when (status) {
    "currently_publishing" -> SManga.ONGOING
    "finished" -> SManga.COMPLETED
    "discontinued" -> SManga.CANCELLED
    "on_hiatus" -> SManga.ON_HIATUS
    "not_yet_published" -> SManga.UNKNOWN
    else -> null
}

internal fun ALMangaDetailsResult.Media.toTrackerDetails(): TrackerMangaDetails {
    val credits = staff?.edges.orEmpty().mapNotNull { edge ->
        edge.node?.name?.full?.takeIf { it.isNotBlank() }?.let { it to edge.role }
    }
    val (author, artist) = splitCreators(credits)
    return TrackerMangaDetails(
        coverUrl = coverImage?.extraLarge ?: coverImage?.large,
        description = cleanTrackerDescription(description),
        genres = genres.orEmpty().filter { it.isNotBlank() },
        author = author,
        artist = artist,
        status = anilistStatusToSManga(status),
    )
}

internal fun MALMangaInfo.toTrackerDetails(): TrackerMangaDetails {
    val credits = authors.orEmpty().mapNotNull { entry ->
        val person = entry.node ?: return@mapNotNull null
        val name = listOfNotNull(person.firstName, person.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        name.takeIf { it.isNotBlank() }?.let { it to entry.role }
    }
    val (author, artist) = splitCreators(credits)
    return TrackerMangaDetails(
        coverUrl = covers?.large ?: covers?.medium,
        description = cleanTrackerDescription(synopsis),
        genres = genres.orEmpty().mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } },
        author = author,
        artist = artist,
        status = malStatusToSManga(status),
    )
}
