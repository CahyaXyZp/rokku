package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.w
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.base.models.Version
import java.util.Date
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    private val json: Json = Injekt.get(),
    private val networkService: NetworkHelper = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) {

    suspend fun checkForUpdate(context: Context, isUserPrompt: Boolean = false, doExtrasAfterNewUpdate: Boolean = true): AppUpdateResult {
        // Limit checks to once a day at most
        if (!isUserPrompt && Date().time < preferences.lastAppCheck().get() + TimeUnit.DAYS.toMillis(1)) {
            return AppUpdateResult.NoNewUpdate
        }

        return withIOContext {
            val result = if (BuildConfig.NIGHTLY) {
                checkNightly()
            } else if (preferences.checkForBetas().get()) {
                networkService.client
                    .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases"))
                    .awaitSuccess()
                    .parseAs<List<GithubRelease>>()
                    .let { githubReleases ->
                        val releases =
                            githubReleases.take(10).filter { isNewVersion(it.version) }
                        // Check if any of the latest versions are newer than the current version
                        val release = releases
                            .maxWithOrNull { r1, r2 ->
                                when {
                                    r1.version == r2.version -> 0
                                    isNewVersion(r2.version, r1.version) -> -1
                                    else -> 1
                                }
                            }
                        preferences.lastAppCheck().set(Date().time)

                        if (release != null) {
                            AppUpdateResult.NewUpdate(release)
                        } else {
                            AppUpdateResult.NoNewUpdate
                        }
                    }
            } else {
                networkService.client
                    .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases/latest"))
                    .awaitSuccess()
                    .parseAs<GithubRelease>()
                    .let {
                        preferences.lastAppCheck().set(Date().time)

                        // Check if latest version is newer than the current version
                        if (isNewVersion(it.version)) {
                            AppUpdateResult.NewUpdate(it)
                        } else {
                            AppUpdateResult.NoNewUpdate
                        }
                    }
            }
            if (doExtrasAfterNewUpdate && result is AppUpdateResult.NewUpdate) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    preferences.appShouldAutoUpdate().get() != AppDownloadInstallJob.NEVER &&
                    result.release.isApkDownloadable
                ) {
                    AppDownloadInstallJob.start(context, null, false, waitUntilIdle = true)
                }
                AppUpdateNotifier(context.localeContext).promptUpdate(result.release)
            }

            result
        }
    }

    /**
     * Nightly builds are only published as GitHub Actions artifacts. The latest successful run on
     * master is compared with the commit the installed build was made from, so a build that is
     * newer than the latest run is never offered a downgrade.
     */
    private suspend fun checkNightly(): AppUpdateResult {
        val run = networkService.client
            .newCall(
                GET(
                    "https://api.github.com/repos/$GITHUB_REPO/actions/workflows/$NIGHTLY_WORKFLOW_FILE/runs" +
                        "?branch=master&status=success&per_page=1",
                ),
            )
            .awaitSuccess()
            .parseAs<GithubWorkflowRuns>()
            .runs
            .firstOrNull()
        preferences.lastAppCheck().set(Date().time)
        run ?: return AppUpdateResult.NoNewUpdate

        val compare = try {
            networkService.client
                .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/compare/${BuildConfig.COMMIT_SHA}...${run.headSha}"))
                .awaitSuccess()
                .parseAs<GithubCompare>()
        } catch (e: Exception) {
            // The installed commit isn't on GitHub, e.g. a local build
            Logger.w(e)
            return AppUpdateResult.NoNewUpdate
        }

        val release = compare.toNightlyRelease(run.url, BuildConfig.COMMIT_COUNT.toIntOrNull())
        return if (release != null) AppUpdateResult.NewUpdate(release) else AppUpdateResult.NoNewUpdate
    }

    @VisibleForTesting
    fun isNewVersion(newVersion: String, currentVersion: String = BuildConfig.VERSION_NAME): Boolean =
        try {
            Version.parse(newVersion) > Version.parse(currentVersion)
        } catch (e: IllegalArgumentException) {
            false
        }
}

val RELEASE_TAG: String by lazy {
    if (BuildConfig.NIGHTLY) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val GITHUB_REPO: String by lazy {
    "CahyaXyZp/rokku"
}

val NIGHTLY_GITHUB_REPO: String by lazy {
    GITHUB_REPO
}

val RELEASE_URL = if (BuildConfig.NIGHTLY) {
    "https://github.com/$GITHUB_REPO/actions/workflows/$NIGHTLY_WORKFLOW_FILE"
} else {
    "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
}
