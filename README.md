# Open Shelf

An app store for Android TV that lists open-source TV apps from GitHub and installs them from each project's releases, using only the remote.

Google Play on TV only carries apps that publish there. Plenty of good TV apps only live on GitHub, where installing one means a phone, a download link and a guess at which APK fits your box. Open Shelf does that part for you.

## What it does

- Lists about 2,900 Android TV apps from GitHub, in 18 categories
- Every app in the list declares TV support in its manifest and ships an APK in its latest release
- Search by name or by what an app does, like "video player" or "launcher"
- Picks the APK that matches your TV's CPU
- Checks the signing key before installing an update, and holds the update if the key changed
- My Apps shows what's installed, with updates for apps in the catalog
- Light and dark themes, and a choice of accent colours

## Install

You need an Android TV, Google TV, Fire TV or other Android box running Android 9 or newer.

1. Download the latest `OpenShelf-x.y.z.apk` from [Releases](https://github.com/wingedpanther/openshelf/releases).
2. Sideload it with an app like Downloader, or from a computer with `adb install OpenShelf-x.y.z.apk`.
3. The first time you install an app, Android asks you to allow installs from Open Shelf. Allow it once.

## The catalog

The app list is built from public GitHub data and ships inside the APK. An app is included when:

- the repository isn't a fork
- its latest release has an APK, published in the last 3 years
- that APK's manifest has the Android TV launcher entry

Apps with fewer than 10 stars stay out of browsing, but search still finds them by their exact name.

## Build

Needs JDK 17 or newer and the Android SDK. Point `local.properties` at the SDK (`sdk.dir=...`), then:

```
./gradlew assembleRelease
```

Release builds are signed with the debug key unless a `keystore.properties` file sits in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Licence

Open Shelf is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

The Geist font is licensed under the SIL Open Font License 1.1. See [FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt).

App names, descriptions and artwork in the catalog belong to their projects.
