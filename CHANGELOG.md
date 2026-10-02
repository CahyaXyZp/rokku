# Changelog

All notable changes to this project will be documented in this file.

The format is simplified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):
- `Additions` - New features
- `Changes` - Behaviour/visual changes
- `Fixes` - Bugfixes
- `Other` - Technical changes/updates

## [Unreleased]

## [1.8.0]

### Additions
- Added Discord Rich Presence with multi-account support, OAuth login, Discord Social SDK authentication, manga cover and app icon support, and per-account settings ([@Hiirbaf](https://github.com/CahyaXyZp/rokku/pull/1))
- The manga details screen background can now pick up a subtle tint from the cover, extending the accent already used for the header/buttons through the rest of the screen (off by default, toggle separately under Settings > Appearance > Details page > Theme background based on cover)
- Local source now reads Year, Month, and Day fields from a chapter's ComicInfo.xml to set its displayed date, and downloaded chapters now write these fields when generating ComicInfo.xml
- Browse and Global Search now keep a recent search history and let you save searches (scoped to one source or all sources), with incognito-aware suggestions ([@Hiirbaf](https://github.com/Hiirbaf))
- Added support for additional chapters: chapters with a decimal number (like 1.1 or 151.5) are now counted separately from main chapters, so the chapter count reads "153 chapters · 3 additional chapters", and the Resume/Start button picks the next unread main chapter before any unread additional one
- Added a "Sync chapters with tracker progress" option (Settings > Tracking, off by default): when linking a tracker, and when pulling to refresh a manga, chapters you already have are marked as read up to the progress the tracker has. Additional chapters are never marked and the tracker's progress is never lowered
- Added auto-scroll to the reader: a chevron under the toolbar title opens a speed slider and a Start/Stop button. Long strip readers scroll smoothly, paged readers turn the page at an interval, scrolling pauses while the menu is open and stops at the end of the last chapter

### Changes
- An automatic backup that fails because its saved location is no longer accessible (folder deleted, permission revoked, storage removed) now shows a notification telling you to pick a new one, instead of failing silently
- Trackers now only receive main chapters as progress: additional chapters (like 1.1 or 151.5) are no longer sent as the last chapter read, and the two-way sync for Komga, Kavita and Suwayomi no longer lowers progress already on the tracker
- The in-app update checker now uses the GitHub releases of CahyaXyZp/rokku for stable and beta builds, and the latest successful nightly workflow run for nightly builds (the update opens the run page in the browser, since Actions artifacts can't be downloaded without logging in)

### Fixes
- Fixed the manga details screen's unmarked "Add to Library"/"Tracking" buttons and the "More" fade-out keeping the plain theme background instead of the cover-based page tint, leaving a visible seam when "Theme background based on cover" was on
- Fixed cool-hued covers (blue/cyan/green) barely tinting the manga details page compared to warm ones, caused by HSL's saturation not being perceptually uniform across hues; the cover-tint blend now uses HCT instead
- Fixed extension loading failing when a repository published its lib version or content warning metadata as a numeric type instead of a string ([@pacoa-kdbg](https://github.com/pacoa-kdbg))
- Fixed "Show content in cutout area" doing nothing on Android 15+ (content still drew into the camera cutout/notch when the option was turned off)
- Fixed a crash when updating all extensions with many updates pending (the work request's input data exceeded its size limit)
- Fixed a rare crash in Recents ("Two different ViewHolders have the same stable ID") caused by a section header's id colliding with a chapter row's
- Fixed a page failing to render in the paged reader when a double-page spread couldn't be decoded for merging
- Fixed the library update job silently failing (and spamming Crashlytics) when the OS refused to promote it to a foreground service
- Fixed a crash opening a chapter in the webtoon reader when its saved resume position was out of range
- Fixed a crash sharing a reader page when its cached image had already been evicted from disk
- Fixed a crash when saving reading history for a chapter that was removed from the library in the meantime (chapter list refreshed, manga removed)
- Fixed an extension install wrongly reporting a failure (and never installing) when Android's DownloadManager returned no content URI for a download that actually completed
- Fixed the app being killed while in the background after a while on newer Android versions (the file logger kept an open handle to the storage provider, so the app died whenever the system reclaimed it)
- Fixed a crash when range-selecting chapters to download/read/mark on the manga details screen (the selection could run past the end of the chapter list)
- Fixed a crash while browsing a source whose filter list threw an error as it loaded in the background
- Fixed a rare non-fatal error when cancelling an extension download after the app's process had been recreated
- Fixed extension updates getting stuck showing "Downloading" forever when Android's DownloadManager silently failed a download or stalled without ever reaching a terminal state
- Fixed a crash copying a manga cover to the clipboard (a raw `file://` URI was exposed outside the app instead of a `content://` one)
- Fixed a source's text filters swapping or losing their typed values when scrolling the filter list, caused by recycled rows accumulating listeners from earlier filters ([@Hiirbaf](https://github.com/Hiirbaf))
- Fixed saving a reader page (or a merged double-page spread) to storage failing with a confusing error when the destination file couldn't be created
- Fixed the backup restore file picker relying on an outdated file selection API that could fail to open correctly on some devices
- Fixed reading history occasionally double-counting a session's read time when a chapter was saved while another save for the same chapter was still in flight
- Fixed the about page's build time never actually showing a formatted date (the parser didn't match the timestamp format Rokku itself produces)
- Fixed the date format preference not showing a live preview of each option, and some other list settings not refreshing their summary right after a change
- Fixed the full-size cover viewer's replace button showing even for manga not in your library, where it can't actually be used
- Added the app ID to the debug info included in crash log dumps
- Fixed the library's app bar sometimes landing in the wrong position after returning to the Library tab when "show all categories" was off and the library wasn't scrolled to the top
- Fixed the action mode toolbar (shown while selecting items) overlapping a display cutout/notch in landscape
- Fixed a source showing without its language tag in lists/search results when that language was disabled in settings

### Other
- Migrated FlexibleAdapter from JitPack to its MavenCentral release, removing a source of transient CI build failures when JitPack was unavailable
- `HttpSource`'s template methods (`popularMangaRequest`/`Parse`, `searchMangaRequest`/`Parse`, `latestUpdatesRequest`/`Parse`, `mangaDetailsParse`, `chapterListParse`, `chapterPageParse`, `pageListParse`, `imageUrlParse`) are no longer required to be implemented by extensions, and a new `getHomeUrl()` lets a source report a home page different from its `baseUrl` for "Open in WebView"
- Reduced Crashlytics noise by no longer reporting a dead or misconfigured extension repo (HTTP 404 on its `repo.json` or index) as a non-fatal error
- Reduced Crashlytics noise by no longer reporting the extension repo/store being rate-limited (HTTP 429) as a non-fatal error (it already falls back to the legacy index)
- Reduced Crashlytics noise by no longer reporting cover-loading, reader, browse, and backup failures that only reflect a source, the network, or the device misbehaving rather than a Rokku bug
- Reduced Crashlytics noise further: handled extension-repo fetch failures, call timeouts/cancellations, dropped connections, unresolved WebView challenges, and broken local-library folders are no longer reported
- Reduced Crashlytics noise by no longer reporting a JSON parse failure caused by a source answering with an HTML page (Cloudflare interstitial or error page) instead of data
- Bumped compileSdk to 37.2

## [1.7.1]

### Fixes
- Fixed laggy, stuttering scrolling and covers intermittently failing to appear in the library, updates, and history
- Fixed checked checkboxes rendering with an invisible checkmark on the Yin Yang theme
- Fixed extension loading blocking the main thread during startup, causing ANRs with many extensions installed
- Fixed the file logger blocking the app, including the UI, during slow disk I/O
- Fixed a crash opening a manga's chapter list when a "missing chapters" gap indicator was shown
- Fixed a tracker refresh/removal failure showing a raw "HTTP error 401" toast instead of an expired-session message
- Fixed the manga details screen still theming the follow/download buttons from the cover after disabling "Theme buttons based on cover"
- Fixed a download crashing and getting stuck instead of erroring out when its temporary chapter folder failed to be created
- Fixed the full cover viewer's buttons and image being misaligned with the display cutout/system bars on Android 15+ ([@Hiirbaf](https://github.com/Hiirbaf))

### Other
- Added support for Android 16's Live Updates: the library update progress notification can now be promoted to a status bar chip
- Reduced Crashlytics noise by no longer reporting non-fatal errors that reflect expected conditions rather than real bugs (network/server issues, expired sessions, self-healing races, and similar)

## [1.7.0]

### Additions
- Added a per-extension incognito toggle (Extension details) to pause history/tracking for just that extension's sources
- Added a "Cover theme style" option (Settings > Appearance) for the Material You palette used for cover-derived accent colors, including a "Legacy" choice for the pre-Material You look ([@Hiirbaf](https://github.com/Hiirbaf))
- Added a "Load suggestions automatically" option (Settings > Browse) to fetch related-manga suggestions on demand instead of automatically
- Added a "Show 'Duplicate' badge" option (Settings > Browse) to flag library entries already added from a different extension
- Added a button to open an available extension's website in a WebView before installing it

### Changes
- Related-manga suggestions are now cached instead of re-fetched every time they're shown
- Renamed "Security" settings to "Security and Privacy"
- The cover-derived accent color now defaults to the "Legacy" style (the pre-Material You look) instead of Material You ([@Hiirbaf](https://github.com/Hiirbaf))

### Fixes
- Fixed a possible race condition in download notifications updated from multiple threads at once
- Fixed download/error notifications collapsing under a generic icon instead of Rokku's when grouped in the shade
- Fixed a restored backup's theme not applying until the app was manually restarted
- Fixed a crash in the source filter spinner when its entries shrink past a stale selected position
- Fixed a possible crash from duplicate extension entries available from more than one repo
- Fixed extension installs hanging indefinitely when started offline or the network drops mid-download
- Fixed the manga description opening expanded when navigating from browse instead of staying collapsed like from the library
- Fixed "Check for updates" failing on nightly builds when the beta-releases preference was off
- Fixed the "Missing N chapters" summary and divider not counting chapters missing before the earliest available one
- Fixed scanlator filtering causing a full library scan on every chapter list/backup/recents query
- Fixed the library still flickering to the wrong covers while scrolling during an active backup restore
- Fixed manga covers never loading, then flickering to the wrong one, in the migration source list
- Fixed a crash when updating a single library category whose queued manga list came back empty
- Fixed a settings crash when a stored preference value had been overwritten with an incompatible type
- Fixed a rare crash when switching chapters while a local archive page was still loading
- Fixed a recycled source filter spinner briefly showing the wrong item, left over from its previous binding
- Fixed an empty notification staying in the shade after chapter downloads finished
- Fixed the library update skipped notification's "open log" action opening the help page instead
- Fixed a crash opening a source's browse page (e.g. a shortcut) when its extension was no longer installed
- Fixed adjacent chapters not preloading when the current chapter has only a single page
- Fixed the library grid/list losing its row count and collapsing to a single column after toggling uniform grid
- Fixed library swipe gestures silently breaking when a view they check against wasn't laid out yet
- Fixed the manga details cover and its blurred backdrop loading independently, showing different covers momentarily
- Fixed the manga details palette request caching an oversized cover bitmap under the library grid's cache key
- Fixed reading a chapter silently reverting AniList custom list membership to stale local data, including changes made directly on AniList (see #115)
- Fixed fetching an AniList entry's tracking info failing with a JSON parsing error when it belonged to no custom list
- Fixed a manga's tracking info never actually refreshing when reopening its details page

### Other
- Bumped Voyager to 2.2.21-1.10.3
- Moved cover ratio/color decoding to a background-priority thread to compete less with the UI thread

## [1.6.1]

### Fixes
- Fixed the "Missing N chapters" chapter-count summary staying hidden when a manga has decimal-numbered chapters
- Fixed manga covers flickering to the wrong cover, or reloading repeatedly, while scrolling the library or Recents
- Fixed covers in Recents disappearing when leaving the tab and coming back
- Fixed manga covers staying blank in Recents when opening the tab, until scrolling
- Fixed MangaBaka scores being recorded against the wrong scale when using a score step size other than 1 ([@Hiirbaf](https://github.com/Hiirbaf))

## [1.6.0]

### Additions
- Added a "Missing N chapters" separator in the chapter list, plus a summary below the chapter count (Settings > Library > Behavior)
- Added a "Select all" option to a manga's genre/tag chips, to search by every tag at once
- Added MangaBaka and Hikka tracker support, including OAuth login ([@Hiirbaf](https://github.com/Hiirbaf))
- Added private tracking: mark a tracked entry as private on services that support it (AniList, Bangumi, Kitsu, MangaBaka)
- Added a "Flash the screen on page turn" reader option, for e-ink displays
- Added a "Custom" option to Settings > Library > Global updates, to anchor updates to a specific time of day
- Added inline editing for extension repo URLs; long-press a repo to copy its URL
- Added a configurable free-space floor (Settings > Downloads) that pauses downloads instead of erroring when storage runs low
- Added a "With 'Cancelled' status" library update restriction (Settings > Library)
- Added AniList custom list support to the tracking sheet

### Fixes
- Fixed manga covers staying blank in list view (library and source browse) on release/nightly builds
- Fixed source browse covers sometimes staying blank in list view after scrolling
- Fixed MangaBaka and Hikka tracker sync IDs being swapped, breaking MangaBaka tracking on backup restore
- Fixed library updates re-downloading a chapter already marked read via "mark duplicate read chapters as read"
- Fixed started/finished reading dates never being set on trackers
- Fixed memory leaks on theme/night-mode/side-nav changes and app shortcut refreshes
- Fixed nested filter groups (e.g. Publisher/Genre) not rendering
- Fixed genre/publisher filters staying empty for sources that fetch filters in the background
- Fixed the filter sheet flickering when expanding a group with many sub-groups
- Fixed MyAnimeList erroring on list entries with partial start/finish reading dates
- Fixed the reader getting stuck loading indefinitely in some scenarios
- Fixed a manga's memo not being saved to or restored from backups
- Fixed free-space checks never triggering when the download directory is a SAF tree/document URI
- Fixed pasting a manga URL into Global Search failing for some sources
- Fixed a source's toggle in extension settings sometimes staying visually off after enabling its language
- Fixed extension icons in the browse list showing the wrong icon, staying blank, or stuck mid-fade ([@Hiirbaf](https://github.com/Hiirbaf))

### Other
- Synced with upstream (yokai): added proguard rules to keep `Serializable` `writeReplace`/`readResolve`, and bumped `okio` to 3.18.1

## [1.5.0]

### Additions
- Add configurable download concurrency (Settings > Downloads): number of simultaneous chapter downloads and simultaneous page downloads per chapter are no longer hardcoded
- Overhaul library search: support `&&`/`||` boolean operators, `-term` negation, `title:`/`author:`/`artist:`/`genre:` field prefixes (in addition to the existing `src:`), and numeric comparators (`>`, `<`, `=`) on `chapters`, `unread`, and `read`
- Pasting a manga URL into Global Search now opens that manga directly if the URL matches an already-installed source, instead of running a full text search across every source
- Add category filtering to the Updates tab (Recents > Updates > display options)

### Changes
- Debounce library search input (250ms) so refiltering no longer runs on every keystroke
- Spoof `Sec-CH-UA` client hints in WebView to match the user agent
- Update default user agent to Chrome 149

### Fixes
- Fix slow backup restore with large libraries by batching manga restoration into chunked database transactions instead of committing every manga individually
- Fix backup restore inserting duplicate chapters when a backup contains the same chapter URL more than once
- Fix `X-Requested-With` spoofing leaking to unrelated callers
- Fix backup restore posting a system notification update on every single manga (throttled by Android and further slowing large restores); it now only updates once per batch
- Fix backup restore re-querying the full category list from the database once per manga instead of once for the whole restore
- Fix backup restore querying the database once per reading-history entry instead of once per manga, which made restoring a backup with a lot of reading history disproportionately slow
- Fix the same one-query-per-history-entry issue when *creating* a backup, which made backing up a library with a lot of reading history disproportionately slow
- Fix backup creation dispatching every manga's queries individually (one dispatcher context-switch each) instead of batching them into chunked transactions, which was the dominant cost for large libraries
- Fix backup creation's chapter queries needlessly joining the internal scanlator-filter view (a recursive query with no per-manga predicate pushdown, so it re-scanned the whole library on every call) even when the scanlator filter isn't used, which was the actual dominant cost for large libraries
- Harden the mitigation for a rare crash when bulk-removing/migrating library entries (a sqldelight race between a library query and a concurrent write): retry with a short delay until it clears instead of giving up after a single immediate retry
- Fix "Check for updates" never finding new nightly builds: it was querying the stable release repo with a stale GitHub username, so nightly checks silently failed and update links pointed to the wrong repo

## [1.4.0]

### Additions
- Add a `src:` search prefix to filter the library by source name or ID (e.g. `src:MangaDex` or `src:2499283573021220255`)
- Add options to immediately fetch metadata and/or the chapter list from the source when adding a manga to the library, instead of waiting for the next library update (Settings > Library, both off by default)
- Add a "Use legacy decoder for long strip reader" option (Settings > Advanced) to force the older SSIV decoder for webtoon pages when lowering the hardware bitmap threshold doesn't fix blank images (contributed by [@Hiirbaf](https://github.com/Hiirbaf))

### Fixes
- Fix a manga's cover sometimes staying blank in the library grid after adding it, until the item was rebound (e.g. by long-pressing it)
- Fix covers never loading in Global Search and Migration, caused by the list rebinding/resorting fast enough to keep cancelling each cover's own in-flight load before it could finish; both now render covers the same Coil/Compose way as the Library and Browse screens
- Fix a broken-image icon showing while a Global Search/Migration cover was still loading or searching, instead of a neutral placeholder
- Fix the migration screen's toolbar title staying stuck at "(0/0)" instead of showing the real manga count
- Fix "Migrate All"'s progress dialog not showing the final count for a moment before closing, most noticeable when migrating a single manga
- Fix "Migrate All"'s progress counting every manga it looked at instead of only the ones actually migrated (e.g. migrating 3 of 4 manga showed "4/4" instead of "3/4")
- Fix automatic migration matching only trying the manga's exact full title against a source's search, missing manga a manual search could still find; it now also tries shorter word combinations as a fallback, with a stricter similarity requirement so a coincidentally shared generic word can't match a completely unrelated manga

### Other
- Enable and fix Kotlin style checks (`kotlinter`) project-wide; it was declared but never actually wired up, so it had never run
- Add CI workflows for Kotlin style checks and CodeQL security analysis
- Document the commit message convention in CONTRIBUTING.md
- Publish nightly build releases to a separate `rokku-nightly` repo instead of the main one, keeping its tags/releases list to actual versions

## [1.3.2]

### Fixes
- Fix the app icon showing as a generic system icon instead of Rokku's own artwork in condensed/bundled notification stacks on some OEM launchers (e.g. MIUI lock screen), caused by the themed/monochrome launcher icon never being wired up since the Yokai fork
- Fix the app's own self-update getting stuck showing "Installing" forever if the install confirmation notification was missed, the same issue fixed for extension updates in 1.3.1

## [1.3.1]

### Additions
- Support the `mihon://extension-store` deep link for adding extension repos, alongside the existing `tachiyomi://add-repo` one (contributed by [@Hiirbaf](https://github.com/Hiirbaf))

### Fixes
- Fix extension updates getting stuck showing "Installing" forever if the OS silently blocked the install confirmation dialog (common when updating an already-installed extension), leaving no way out except uninstalling and reinstalling the extension
- Fix the reader not exiting fullscreen/immersive mode correctly in split-screen or multi-window mode
- Fix the app bar's toolbar mode not resyncing after entering/exiting split screen
- Fix the floating browse toolbar recalculating its bottom margin (including keyboard insets) on every scroll on Android 10 and below
- Fix the favorite button's long-press category picker letting a stray tap open a menu underneath it
- Fix a crash when exiting the reader back to manga details with no shared element to animate

### Other
- Upgrade to Android Gradle Plugin 9.3.1 and Gradle 9.6.1, matching Mihon

## [1.3.0]

### Additions
- Add a persistent "Library update errors" screen listing manga that failed to update, with select-all/individual selection and bulk migration to another source (ported from Komikku)

### Fixes
- Fix a "ghost" download group summary notification surviving if the app process was killed while downloads were still queued (e.g. swiping the app away from recents)

## [1.2.5]

### Fixes
- Fix a "ghost" download notification staying behind after all downloads finished

## [1.2.4]

### Fixes
- Fix library update discarding a source's updated manga details (cover, description, status, etc.) whenever it returned them together with the chapter list, instead of only when explicitly requested, and doing a redundant extra request for details it already had
- Fix the download icon in the chapter list getting stuck instead of animating while a chapter downloads, if the row scrolled off-screen and back mid-download
- Fix the "New chapters found" notification sometimes showing no content when only one manga had an update, and its "Skipped" counterpart opening the help page instead of the skip log when tapped
- Restore the animated download icon (matching upstream) instead of a static one, and stop the download notification's group summary from re-posting on every single downloaded page, which could make it visibly flicker/reshuffle in the notification shade

## [1.2.3]

### Fixes
- Fix "Data and storage" opening the older, less polished settings screen; it's now the primary screen reached from Settings
- Fix "Data and storage" (and every other Settings screen using the large collapsible app bar) freezing mid-scroll until the screen was reopened
- Fix the "In library" badge not showing on the comfortable grid layout when browsing a source or viewing related manga
- Fix extension installs/updates sometimes getting stuck showing "Downloading" or the install button after finishing, until the extensions sheet was reopened
- Fix the manga's own source being dropped from the migration search when more than one source was available
- Fix the app version showing a redundant "Release" prefix in About
- Fix grouped download notifications sometimes showing a generic icon instead of Rokku's

## [1.2.2]

### Additions
- Add an option to pick what to restore from a backup (library, categories, app settings, source settings, extension repos), instead of it being all-or-nothing
- Back up and restore custom extension repos along with the rest of your data

### Fixes
- Fix "Data and storage" defaulting to the experimental settings screen instead of the stable one (single tap now opens the stable screen; long-press still opens the experimental one, matching upstream)
- Fix in-app updates sometimes getting stuck on "Installing" forever, by falling back to a tap-to-confirm notification if the install dialog is blocked by the OS
- Fix "Help translate" linking to Yokai's translation project instead of Rokku's

## [1.2.1]

### Fixes
- Fix chapters from some sources (e.g. Asura Scans, Hive Scans, Kayn, Vortex) failing to open with a "Refresh Chapter List" error, caused by the local database silently discarding the `memo` metadata some sources rely on to validate a chapter before loading it

## [1.2.0]

### Additions
- Add a "Suggestions" section to the manga details page and a dedicated full-list screen, showing related titles from the same source (Settings → Browse, off by default)

### Fixes
- Fix the webtoon reader visibly resizing/jumping when transitioning between pages split from a long strip image
- Fix source search/browse results not reflecting a manga's library status if it was favorited a few screens deeper in the navigation (e.g. via a suggested manga)

### Other
- Standardize Settings descriptions to not end with a trailing period

## [1.1.8]

### Fixes
- Fix manga details sometimes only loading info or chapters (never both) until a manual reload, caused by two concurrent calls to the same source request racing against its concurrency guard (contributed by [@Hiirbaf](https://github.com/Hiirbaf))
- Fix in-app updates sometimes getting stuck on "Installing" forever on some OEM devices, by falling back to a manual install prompt if the install confirmation never appears

## [1.1.7]

### Fixes
- Fix migration screen crashing (double-unlock) and getting progressively slower with each migrated manga
- Fix manga description showing blank when opening from the library until a manual refresh
- Fix library selection being cleared every time a background chapter download completed
- Fix chapter list stutter and a stuck download-completion animation while other manga download in the background
- Fix a ~1s freeze and missed taps when marking a single chapter as read/bookmarked
- Fix the per-category library update spinner not animating, or getting stuck off
- Fix a UI freeze when starting a category update while another one was already running
- Add progress feedback to Migrate All / Copy All

## [1.1.6]

### Fixes
- Fix editing an extension's settings (e.g. a custom URL) crashing with a `ClassCastException`
- Fix extensions targeting the current `tachiyomix 1.6` protocol failing to load correctly: the `SManga`/`SChapter` interfaces were missing the `memo` field some extensions rely on (contributed by [@Sacha1016](https://github.com/Sacha1016))
- Fix `SManga.copy()` silently dropping the `update_strategy` field
- Fix the About screen's Discord/website links pointing at Mihon's own community, which this fork isn't affiliated with
- Fix download notifications using generic Android system icons instead of this app's own

### Other
- CI: docs-only pushes (README/CHANGELOG) no longer trigger a nightly build/release

## [1.1.5]

### Fixes
- Fix a data-loss bug where toggling "Download with ID" could cause already-downloaded chapters to be misidentified as orphaned and deleted during download cleanup
- Fix a slight shake/readjustment while scrolling down in the webtoon (continuous scroll) reader, caused by pages reflowing right as they finish decoding into view
- Fix remaining links/identifiers still pointing at the original Yokai maintainer: the About screen's GitHub link, the debug build's "view source" link, and the User-Agent sent to tracker services (Anilist, Bangumi, Kavita, Kitsu, MangaUpdates, Shikimori)
- Manga details FAB now shrinks/extends on scroll, and the chapter list no longer sits behind it (contributed by [@Hiirbaf](https://github.com/Hiirbaf))

### Changes
- Removed the BETA tag from "Use staggered grid", "Download with ID", and "Scan external storages for entries" after testing and, where needed, fixing the underlying issues that justified the tag

### Other
- CI: release/beta builds now fail early with a clear error if `CHANGELOG.md` has no matching version section, instead of silently publishing with an empty body
- CI: PR builds now boot the release APK on an emulator and fail if it crashes on launch, to catch R8-shrinking issues (like the one fixed in 1.1.4) before they reach a release
- Removed `.github/FUNDING.yml`, which pointed GitHub's Sponsor button at the original Yokai maintainer's accounts

## [1.1.4]

### Fixes
- Fix extensions that decompress zstd/brotli-encoded responses crashing the app outright (native abort, no error/log shown) in release/nightly builds; R8 was stripping the native decoder classes since they're only reachable via JNI

## [1.1.3]

### Other
- Raise `minSdk` to 26 (matching Mihon) to fix `AbstractMethodError` crashes in extensions (e.g. MangaDotNet) compiled assuming native Java 8 default-interface-method dispatch, which Android doesn't support below API 24; this drops support for Android 6.0/7.0/7.1

## [1.1.2]

### Fixes
- Fix extensions that decompress zstd-encoded responses crashing with `NoClassDefFoundError: okhttp3.zstd.Zstd` (the fix in 1.1.1 restored the brotli dependency but missed zstd)

## [1.1.1]

### Fixes
- Fix extensions that reference `okhttp3.brotli.Brotli` or `okhttp3.zstd.Zstd` directly (e.g. WeebCentral, MangaDotNet) crashing with `NoClassDefFoundError` since 1.1.0

### Other
- CI: make the manual release/beta build actually set the app's version (previously it only renamed the GitHub tag/release, the installed app kept showing an unrelated version number)
- CI: normalize and quote the version input so a stray space no longer breaks the whole build
- CI: push the version tag before creating the GitHub release, avoiding a race that could leave the release "untagged"
- CI: grant `GITHUB_TOKEN` write permissions for releases and test check runs (newer repos default to read-only)

## [1.1.0]

### Fixes
- Fix in-app update checker pointing at the original Yokai's GitHub repo instead of this fork's (was permanently showing a fake "update available")
- Removed forced Brotli/gzip-bypass network interceptors (fixed some sources but broke others that treat them as a bot-detection signal; see the known issue below, fixed in 1.1.1)

### Changes
- Replaced the Yokai notification icon with a Rokku one

### Known issues (fixed in 1.1.1)
- Some extensions that reference `okhttp3.brotli.Brotli` directly crash on load

## [1.0.1]

### Changes
- Own `applicationId` (`app.rokku`) instead of reusing the original Yokai's, so this fork can be installed alongside it without conflicting
- Removed Firebase Crashlytics/Analytics (was still pointing at the original Yokai maintainer's project)

## [1.0.0]

First release under the Rokku name. Everything below was inherited as broken from Yokai/pre-fork and fixed here; see the README for full fork history and credits.

### Fixes
- Fix Keiyoushi extension compatibility: extension lib version window was capped below what current extensions ship as, rejecting all of them
- Fix `Source`/`CatalogueSource` interface to match the current extension API (`tachiyomix 1.6`), including the combined `getMangaUpdate` call
- Fix "add extension repository" only accepting a URL ending in `/index.min.json`, rejecting the `repo.json`/`index.pb` URLs Keiyoushi now points users to
- Add support for the newer protobuf/JSON "ExtensionStore" repository index format, with the legacy `index.min.json` array kept as a fallback
- Fix Shikimori tracker using a decommissioned domain (`shikimori.one` → `shikimori.io`)
- Fix downloader always restarting interrupted page downloads from scratch instead of resuming them
- Fix download notifications not respecting the Android 13+ notification permission
- Fix Shizuku extension installer relying on a private API and shelling out to `pm`, a source of silent install failures; now uses the proper (if hidden) `PackageInstaller` session APIs
- Fix extension install broadcast receiver being unnecessarily exported

### Changes
- "Source repos" setting no longer marked as beta

### Other
- Updated OkHttp, Kotlin, Compose, AndroidX, coroutines/serialization, and other core dependencies
- Completed pt-BR translations
