# LP Collector

An Android app for tracking your vinyl record collection and wishlist. Records are looked up on [Discogs](https://www.discogs.com/) by text search or barcode. Their metadata and cover art are saved on the device, and the collection is stored as an XML file.

## Features

- **Collection and wishlist tabs.** Filter by artist, title, label or year, and sort by artist, title, year or date added.
- **Discogs search.** Search by artist, album or catalog number, with a "Vinyl only" filter. Typing or pasting an 8–14 digit UPC/EAN searches by barcode.
- **Barcode scanning.** Scan a record's barcode with the camera. This uses the Google Play services code scanner, so no camera permission is needed.
- **Local metadata.** Adding a release downloads its title, artist, year, country, labels and catalog numbers, formats, genres, styles, barcode and tracklist. The cover image is cached on the device, so your lists work offline.
- **Personal notes.** Give each record a 1–5 star rating, a condition grade (M, NM, VG+, VG, G+, G, F, P) and free-text notes.
- **Move between lists.** A "Got it" button moves a record from the wishlist to the collection. A record can also go back to the wishlist, or be removed.
- **Export and import.** Save the collection XML anywhere through the system file picker, and restore it later. On import you can *Merge* with your current data or *Replace* it. After an import, missing covers are downloaded again.

A release belongs to at most one list. Adding a wishlisted release to your collection moves it there and keeps your notes.

## Requirements

- Android 8.0 (API 26) or newer
- A free Discogs **personal access token**
- Google Play services, for barcode scanning only

## Getting started

1. Install `dist/LPCollector.apk` on your phone. You need to allow installing apps from unknown sources.
2. Get a token: log in at discogs.com, open **Settings → Developers** (<https://www.discogs.com/settings/developers>) and click **Generate new token**.
3. In the app, open **Settings** (gear icon on the Collection or Wishlist tab), paste the token and tap **Save token**.
4. Go to the **Search** tab and start adding records.

## Data storage

Your collection lives in the app's private storage:

| What | Where |
|---|---|
| Collection & wishlist | `files/collection.xml` |
| Cover images | `files/covers/<discogsId>.jpg` |
| Discogs token and "Vinyl only" setting | Jetpack DataStore (`settings`) |

Other apps can't read this storage. To back up or move your data, use **Settings → Export XML**. Uninstalling the app deletes the data.

### XML format

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lpcollector version="1">
  <collection>
    <record discogsId="249504" addedAt="2026-09-26T12:00:00Z">
      <title>Rumours</title>
      <artist>Fleetwood Mac</artist>
      <year>1977</year>
      <country>US</country>
      <labels>
        <label catno="BSK 3010">Warner Bros. Records</label>
      </labels>
      <formats>
        <format>Vinyl, LP, Album</format>
      </formats>
      <genres>
        <genre>Rock</genre>
      </genres>
      <styles>
        <style>Pop Rock</style>
      </styles>
      <barcode>075992731310</barcode>
      <tracklist>
        <track position="A1" duration="2:43">Second Hand News</track>
      </tracklist>
      <coverUrl>https://i.discogs.com/…</coverUrl>
      <coverFile>covers/249504.jpg</coverFile>
      <discogsUrl>https://www.discogs.com/release/249504</discogsUrl>
      <rating>4</rating>
      <condition>VG+</condition>
      <notes>Bought at a record fair.</notes>
    </record>
  </collection>
  <wishlist />
</lpcollector>
```

Optional elements are left out when empty. When reading, unknown elements are ignored and records without a `discogsId` are skipped. A file without an `<lpcollector>` root is rejected.

## Building from source

### Prerequisites

- JDK 17 or newer. Android Studio's bundled JBR works.
- Android SDK with platform **android-37**
- A `local.properties` file pointing at the SDK:
  ```properties
  sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
  ```

### Commands (PowerShell)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat testDebugUnitTest   # unit tests
.\gradlew.bat assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

### Release signing

Release builds are signed only if a `keystore.properties` file exists in the project root:

```properties
storeFile=keystore/lpcollector.jks
storePassword=...
keyAlias=lpcollector
keyPassword=...
```

To create a keystore:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore keystore\lpcollector.jks `
  -alias lpcollector -keyalg RSA -keysize 4096 -validity 10000
```

`keystore/` and `keystore.properties` are git-ignored. **Back them up.** An update installs over an existing copy only if it is signed with the same key. Otherwise you have to uninstall first, which deletes the on-device collection. Export your data before that.

### Install on a device or emulator

```powershell
adb install -r dist\LPCollector.apk
```

`-r` keeps the existing app data.

## Project structure

```
app/src/main/java/com/lpcollector/
├── LPCollectorApp.kt          Application, AppContainer (manual DI), Coil image loader
├── MainActivity.kt            Compose host, navigation routes, bottom tabs
├── data/
│   ├── RecordRepository.kt    In-memory library + atomic XML persistence, add/move/remove/import/export
│   ├── model/Record.kt        Record, Track, Label, Library, ListType, condition grades
│   ├── xml/RecordXmlStore.kt  XML writer and XmlPullParser-based reader
│   ├── discogs/               OkHttp Discogs client, JSON DTOs, DTO → Record mapping
│   ├── images/CoverStore.kt   Cover download/delete under files/covers/
│   └── settings/              DataStore-backed token and preferences
└── ui/
    ├── list/                  Collection & wishlist screen
    ├── search/                Discogs search + barcode scanner
    ├── detail/                Discogs release preview with add buttons
    ├── record/                Saved record: details, rating, condition, notes
    ├── settings/              Token, export/import
    ├── common/                Shared composables (cover, rating bar, tracklist…)
    └── theme/
```

**Tech stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, OkHttp, kotlinx.serialization, Coil 3, DataStore, ML Kit code scanner. Built with Android Gradle Plugin 9.4 and Gradle 9.8.

## Discogs API notes

- All requests go to `https://api.discogs.com/` with a `User-Agent` header and `Authorization: Discogs token=<token>`.
- Endpoints used: `GET /database/search` (text or `barcode=` search, `type=release`) and `GET /releases/{id}`.
- Authenticated requests are limited to 60 per minute. If the limit is hit, the app shows a "Too many requests" message.
- Data comes from Discogs and is subject to the Discogs API Terms of Use.
