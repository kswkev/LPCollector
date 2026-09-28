# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

LP Collector is a single-module Android app (Kotlin, Jetpack Compose) for tracking a vinyl collection and wishlist. It uses the Discogs API and stores data on the device as XML. The README covers user-facing features and the XML schema.

## Build & test (Windows / PowerShell)

Gradle 9.8 needs a newer JDK than the JDK 17 on PATH, so always use Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest                                   # all JVM unit tests
.\gradlew.bat testDebugUnitTest --tests "*RecordXmlStoreTest"     # one class
.\gradlew.bat testDebugUnitTest --tests "*RecordXmlStoreTest.escapesSpecialCharacters"  # one test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease    # R8-minified; signed only if keystore.properties exists
```

- Branching: do all work on `develop` and push to `origin/develop`. Never commit directly to `main`. When a piece of work is finished, open a PR from `develop` to `main`. Because `gh` isn't installed, create it with the GitHub REST API (`POST /repos/kswkev/LPCollector/pulls`, head `develop`, base `main`) using the token from `git credential fill`. Tags and releases are created by CI when the PR is merged (see below).
- `local.properties` (sdk.dir) and `keystore.properties` + `keystore/lpcollector.jks` are git-ignored. `keystore.properties` holds the release signing key and its password. Never regenerate or replace it: an APK signed with a different key can't be installed over the existing app, and uninstalling first deletes the user's on-device collection.
- CI (`.github/workflows/`): `pr.yml` runs unit tests plus debug and (unsigned) release builds on every PR. `release.yml` runs on every push to `main`: if tag `v<versionName>` doesn't exist yet, it builds a signed APK, checks the signing cert against the pinned SHA-256, and creates the tag and GitHub release with `LPCollector-X.Y.Z.apk`. It signs with repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
- Releases: bump `versionCode` and `versionName` in `app/build.gradle.kts` on `develop`, then merge the PR into `main`. The release workflow does the rest. If you don't bump the version, the merge doesn't release anything. v1.0.0 was published by hand through the GitHub REST API before CI existed.
- Build setup: AGP 9.x with **built-in Kotlin**. Don't apply `org.jetbrains.kotlin.android`; only the Compose and serialization Kotlin plugins are applied. compileSdk/targetSdk 37, minSdk 26. Versions live in `gradle/libs.versions.toml`.

## Architecture

**Manual DI:** `LPCollectorApp` creates one `AppContainer` holding the shared `OkHttpClient`, `SettingsRepository`, `DiscogsClient`, `CoverStore` and `RecordRepository`. Screens get ViewModels through `appViewModel { container -> ... }` in `ui/common/Common.kt`, which is scoped to the nav back-stack entry. Route arguments are passed to the ViewModel constructor rather than read from `SavedStateHandle`.

**Data flow:** `RecordRepository` is the single source of truth. It holds a `StateFlow<Library>` (collection + wishlist) that is loaded once from `filesDir/collection.xml` at startup. Every mutation goes through `mutate()`, which waits for the initial load, takes a mutex, rewrites the whole file atomically with `AtomicFile`, and then publishes the new state. UI state is derived from this flow with `combine`/`map` in the ViewModels.

**Invariant:** a Discogs release id appears in at most one list. Adding a release that is already saved moves it to the target list and keeps the rating, condition and notes. `Library.find(id)` is therefore unambiguous, and `RecordRoute(id)` needs no list type.

**Discogs:** `DiscogsClient` is a thin OkHttp + kotlinx.serialization client, not Retrofit. It adds `Authorization: Discogs token=…` to every request, using the token from DataStore. An OkHttp interceptor in `AppContainer` adds the `User-Agent` that Discogs requires. Coil uses the same OkHttp client (see `newImageLoader`), so image requests send that User-Agent too. `ReleaseMapper.kt` converts `ReleaseDto` into a `Record`: it cleans artist "(n)" suffixes, keeps only tracks of type `track`, and uses the primary image. Search results come from the lighter `SearchResult` DTO. Full details are fetched only when a release is opened or added.

**Covers:** when a record is added, the cover is downloaded to `filesDir/covers/<id>.jpg`. Records store that path relative to `filesDir`, plus the original `coverUrl`. The UI's `coverModel(record)` uses the local file if it exists and falls back to the URL. After an import, `restoreMissingCovers()` downloads covers again.

**XML:** `RecordXmlStore` writes XML by hand (escaping + stripping illegal characters) and reads it through an `XmlPullParser` passed in by the caller. Production passes `Xml.newPullParser()`. JVM unit tests pass `org.kxml2.io.KXmlParser()` (the `kxml2` test dependency) because `android.util.Xml` is a stub on the JVM. Keep that parameter if you refactor. Unknown elements must keep being skipped so older app versions can read newer files.

**UI:** single activity, type-safe Navigation Compose routes (`@Serializable` objects/classes in `MainActivity.kt`). The outer Scaffold draws only the bottom tab bar and uses `contentWindowInsets = WindowInsets(0)`; each screen's own Scaffold/TopAppBar handles the status-bar insets. Scrollable screens with text fields apply `consumeWindowInsets(padding).imePadding()` so the keyboard doesn't cover fields. Back navigation uses `dropUnlessResumed { nav.popBackStack() }` to avoid popping twice.

## Testing on the emulator

AVD `Pixel_10_Pro_XL` (API 37); adb is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- Reinstall with `adb install -r` to keep the user's real collection. The release build isn't debuggable and the image can't `adb root`, so `run-as` fails. To inspect `collection.xml`, use the app's Settings → Export XML and pull the file from `/sdcard/Download`.
- Screenshots: `adb shell screencap -p /sdcard/s.png` then `adb pull`. Redirecting `adb exec-out` output in PowerShell 5.1 corrupts the binary. Screens are 1344×2992, so screenshots come back scaled and tap coordinates must be scaled to match.
- `adb shell input text` with `&`, `<`, `>` or spaces must be quoted on the device side: `adb shell "input text 'a%sb%s&'"` (`%s` = space).
- If the screen is black, the device is asleep. Run `adb shell input keyevent KEYCODE_WAKEUP` and `adb shell svc power stayon true`.
