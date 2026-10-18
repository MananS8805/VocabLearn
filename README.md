# Glossa

Turn your **lock screen** into a vocabulary trainer. Glossa quietly swaps in a new
word every time you wake your phone — word, part of speech, meaning, and an example
sentence, rendered straight onto the wallpaper — and, inside the app, doubles as an
offline dictionary and a read-aloud GRE essay library.

*Formerly WallLearn. The package id is still `com.example.walllearn`, so existing
installs keep their words and settings when they update.*

## Features

- **1,000+ GRE words out of the box**, shuffled into a no-repeat rotation that
  reshuffles once you've seen every word. Add your own from the Today tab.
- **Lock screen only** — your home screen wallpaper is never touched. A lightweight
  foreground service refreshes the wallpaper whenever the screen turns on, and picks back up
  after a reboot.
- **11 lock screen themes** — pick one in the Themes tab; each preview is rendered by the
  same code as the real wallpaper, using your current word.
- **Dictionary** — the offline WordNet database covers 147k words. For a word it doesn't
  have (e.g. "selfie"), an opt-in "Look up online" button fetches it from Wiktionary, saves it
  on the device (searchable offline afterwards) and adds it to your rotation.
- **GRE essay reader** — 40 model Issue essays plus any you paste in, offline:
  - *Read* is a black screen with white serif text. *Listen* reads it aloud and dims every
    sentence but the spoken one (the spoken word is highlighted too on Android 8+), keeping
    the active line centred like synced lyrics. Tap any sentence to start from it.
  - The notes sheet lists each essay's strongest points (thesis, arguments, the complexity
    move, conclusion) with the source's own explanation of why they work, its vocabulary
    glossary, and a structure breakdown. One tap adds a vocabulary word to your lock-screen
    rotation, using the essay's sentence as its example.
  - Five voices (American ×2, British ×2, Indian), speed and pitch controls. See
    [Voices](#voices).

## Using the app

Four tabs behind a bottom bar, each keeping its state while you switch:

| Tab | What it does |
|---|---|
| **Today** | Today's word, the *Lock screen words* switch, "Next word", add your own word |
| **Words** | Search the dictionary; opening a word adds it to your rotation |
| **Essays** | The 40 model essays by theme, and "Add your own essay" (long-press one of yours to delete it) |
| **Themes** | The app's look (**Dark**, **Light** or **Bauhaus**) and the lock-screen theme |

Back goes from any tab to Today, then exits. "Next word" types the word in letter by letter
(skipped when system animations are off).

## Requirements

**To run:** Android 7.0 (API 24) or newer. For the essay reader's voices, the phone needs the
**Google Speech Services (text-to-speech)** engine with English voice data installed — it is
preinstalled on most phones. If it is missing, install it from the Play Store and download
English (US/UK/India) voices in *Settings › Text-to-speech output*. Everything else works
without it, and no feature other than the online word lookup needs a network.

**To build:**

| Tool | Version | Needed for |
|---|---|---|
| JDK | 17 | Everything |
| Android SDK | platform 35 (Android Studio installs it) | Everything |
| Android Studio | Meerkat or newer (optional) | IDE workflow |
| Python | 3.10+ | Only the `tools/` scripts below |
| `pymupdf`, `pillow` | latest | `tools/build_essays.py`, `tools/make_icons.py` |

## Build and run

```bash
git clone https://github.com/MananS8805/VocabLearn.git
cd VocabLearn
./gradlew installDebug          # build and install on a connected phone
./gradlew testDebugUnitTest     # unit tests
./gradlew lintDebug             # lint
```

Point Gradle at your SDK with `sdk.dir=...` in `local.properties` (Android Studio does this
for you).

## Building the shareable APK

Release builds are signed with `keystore.properties` at the project root (kept out of version
control; see `.gitignore`):

```properties
storeFile=keystore/your-release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew assembleRelease
```

The signed APK lands at `app/build/outputs/apk/release/app-release.apk`. Bump `versionCode`
(must increase every release) and `versionName` in `app/build.gradle.kts` first, and add an
entry to [CHANGELOG.md](CHANGELOG.md).

> **Keep the keystore safe and backed up.** Android only installs an update over an existing
> install if it is signed with the *same* key. Lose the key and every user has to uninstall
> (and lose their saved words) to get the next version.

### Sharing it

Send the APK file (messaging app, cloud drive, USB). To install, the recipient opens it on
their phone and allows *Install unknown apps* for whichever app they opened it from when
Android asks. Play Protect may warn about an app it has not seen before; that is expected for
APKs shared outside the Play Store. Updating later is the same: install the newer APK over
the old one, and nothing is lost.

A debug build (`installDebug`) is signed with a different, per-machine debug key, so it cannot
update a release install or vice versa without uninstalling first.

## Tools

| Script | Purpose |
|---|---|
| `tools/build_essays.py "<essay compilation>.pdf"` | Regenerates `app/src/main/assets/essays.json` from the source PDF. Deterministic (no model calls); fails unless it finds exactly 40 essays and every highlighted quote is a verbatim sentence of its essay. |
| `tools/build_dictionary.py` | Builds `assets/dictionary.db` (WordNet 3.0). Its `DB_VERSION` must match `Dictionary.kt`. |
| `tools/make_icons.py` | Regenerates the legacy (pre-Android-8) launcher icons from the three-bar mark. The adaptive icon is the vector in `res/drawable/ic_launcher_*.xml`. |

The source essay PDF is **not** in the repository (see [Third-party content](#third-party-content)).
The essay points are chosen by paragraph role; the "why" text is copied from the source's own
structure notes and is left blank where those notes don't discuss that paragraph.

## Voices

The reader's voices are the phone's own Google text-to-speech voices, resolved by region
(US, GB, IN) at run time, so they are instant and fully offline. Choosing a voice plays a
sample. Google doesn't publish voice genders, so judge by ear; to swap one, edit the
`names` list in `model/EssayVoice.kt` (voice names are listed in the log as
`EssaySpeaker: Using voice …`). Pitch defaults to 0.9× because these voices run high.

A bundled neural model (Kokoro-82M) was tried and dropped: on a Galaxy S25 it generated speech
slower than real time, so playback would have stalled on every sentence.

## How it works

- `WordRepository` loads the bundled word list (`assets/gre_words.json`) plus your own words
  (stored separately, since the APK's assets are read-only) and keeps a shuffled, persisted
  rotation through the combined set.
- `WallpaperGenerator` draws the word onto a full-screen bitmap from a `LockTheme`;
  `WallpaperController` applies it to the lock screen only (`WallpaperManager.FLAG_LOCK`).
- `WallpaperUpdateService` listens for `ACTION_SCREEN_ON` and triggers a refresh;
  `BootReceiver` restarts it after a reboot if you had it enabled.
- The UI is one `MainActivity` hosting four fragments; `EssayReaderActivity` is the full-screen
  reader. App looks are three themes in `res/values/themes.xml` driven by `?attr/glossa…`
  tokens. Every colour in a theme **must** be a `@color` resource: Material resolves some
  attributes by resource id and an inline hex value crashes at inflate (`ThemeResourcesTest`
  guards this).
- `EssayRepository` reads the bundled essays and your own (`user_essays.json`);
  `SentenceSplitter` cuts them into lyric lines; `EssaySpeaker` drives text-to-speech one
  sentence at a time (Android has no pause, so pause restarts from the current sentence).
- `Dictionary` (WordNet, read-only SQLite) → `WebWordStore` (saved online words) →
  `WebDictionary`/`WiktionaryParser` (opt-in lookup).

## Permissions

| Permission | Why it's needed |
|---|---|
| `SET_WALLPAPER` | Apply the generated wallpaper to the lock screen |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the screen-on listener alive in the background |
| `POST_NOTIFICATIONS` | Show the required "Glossa is running" notification on Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Restart the listener after a reboot, if it was enabled |
| `INTERNET` | Only for the opt-in "Look up online" word fallback; nothing else touches the network |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional — ask not to be killed by battery optimization |

## Third-party content

- **Model essays.** The 40 essays, glossaries and structure notes in `essays.json` come from a
  third-party "GRE Analyze an Issue" compilation. They are fine for personal study, but
  **do not publish or redistribute them** (public repository, Play Store listing) without the
  author's permission. Remove `assets/essays.json` and the essay tab content before any public release.
- **WordNet 3.0** definitions © Princeton University (licence in `assets/wordnet_license.txt`).
- **Wiktionary** definitions fetched by "Look up online" are CC BY-SA 4.0; the app shows the credit
  on every such entry.
- Kokoro and sherpa-onnx are not used.

## Tech stack

Kotlin 2.0 · View Binding · Material Components 1.12 · Fragments + BottomNavigationView ·
min SDK 24 · target/compile SDK 35 · JDK 17 · AGP 8.9
