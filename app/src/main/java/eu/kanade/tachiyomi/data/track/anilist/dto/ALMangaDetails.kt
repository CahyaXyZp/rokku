package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALMangaDetailsResult(val data: Data) {

    @Serializable
    data class Data(@SerialName("Media") val media: Media)

    @Serializable
    data class Media(
        val coverImage: Cover? = null,
        val description: String? = null,
        val genres: List<String>? = null,
        val status: String? = null,
        val staff: Staff? = null,
    )

    @Serializable
    data class Cover(val extraLarge: String? = null, val large: String? = null)

    @Serializable
    data class Staff(val edges: List<Edge>? = null)

    @Serializable
    data class Edge(val role: String? = null, val node: Node? = null)

    @Serializable
    data class Node(val name: Name? = null)

    @Serializable
    data class Name(val full: String? = null)
}
