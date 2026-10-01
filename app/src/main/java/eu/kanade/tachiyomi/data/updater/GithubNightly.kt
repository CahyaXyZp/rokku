package eu.kanade.tachiyomi.data.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val NIGHTLY_WORKFLOW_FILE = "nightly_build.yml"

private const val MAX_NIGHTLY_NOTES = 10

/**
 * Workflow runs of the nightly build, as returned by the GitHub API.
 */
@Serializable
data class GithubWorkflowRuns(@SerialName("workflow_runs") val runs: List<Run>) {

    @Serializable
    data class Run(
        @SerialName("head_sha") val headSha: String,
        @SerialName("html_url") val url: String,
    )
}

/**
 * Comparison between the commit of the installed build and the commit of a nightly run.
 */
@Serializable
data class GithubCompare(
    val status: String,
    @SerialName("ahead_by") val aheadBy: Int,
    val commits: List<Commit> = emptyList(),
) {

    @Serializable
    data class Commit(val sha: String, val commit: Detail)

    @Serializable
    data class Detail(val message: String)
}

/**
 * Nightly APKs only exist as GitHub Actions artifacts, which can't be downloaded without logging in,
 * so a nightly update links to the run page instead of an APK.
 */
internal fun String.isActionsRunUrl(): Boolean = contains("/actions/runs/")

/**
 * Turns the comparison into an update, or null when the nightly run isn't ahead of the installed build.
 *
 * @param runUrl page of the workflow run that holds the artifacts.
 * @param currentCommitCount commit count of the installed build, used to name the new version.
 */
internal fun GithubCompare.toNightlyRelease(runUrl: String, currentCommitCount: Int?): GithubRelease? {
    if (status != "ahead" || aheadBy <= 0) return null

    val version = currentCommitCount?.let { "r${it + aheadBy}" }
        ?: commits.lastOrNull()?.sha?.take(7)
        ?: "nightly"
    val notes = commits
        .takeLast(MAX_NIGHTLY_NOTES)
        .asReversed()
        .joinToString("\n") { "- ${it.commit.message.lineSequence().first()} (${it.sha.take(7)})" }

    return GithubRelease(
        version = version,
        info = notes,
        releaseLink = runUrl,
        preRelease = true,
        assets = emptyList(),
    )
}
