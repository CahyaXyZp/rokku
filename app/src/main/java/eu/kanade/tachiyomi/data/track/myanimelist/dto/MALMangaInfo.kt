package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALMangaInfo(
    val synopsis: String? = null,
    @SerialName("main_picture")
    val covers: MALMangaCovers? = null,
    val genres: List<Genre>? = null,
    val authors: List<Author>? = null,
    val status: String? = null,
) {

    @Serializable
    data class Genre(val name: String? = null)

    @Serializable
    data class Author(val node: Person? = null, val role: String? = null)

    @Serializable
    data class Person(
        @SerialName("first_name")
        val firstName: String? = null,
        @SerialName("last_name")
        val lastName: String? = null,
    )
}
