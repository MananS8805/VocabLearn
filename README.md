# WallLearn

Turn your **lock screen** into a vocabulary trainer. WallLearn quietly swaps in a new
word every time you wake your phone — word, part of speech, meaning, and an example
sentence, rendered straight onto the wallpaper. No app to open, no reminders to
dismiss — you learn a word just by checking the time.

## Features

- **1,000+ GRE words out of the box**, shuffled into a no-repeat rotation that
  reshuffles once you've seen every word.
- **Lock screen only** — your home screen wallpaper is never touched.
- **Add your own words** right from the app; they're merged into the same rotation
  as the built-in list and persist across restarts.
- **Runs itself**: a lightweight foreground service listens for the screen turning
  on and refreshes the wallpaper automatically, and picks back up after a reboot.
- Clean black wallpaper with a bold white serif word — easy to read, easy on the eyes.

## How it works

- `WordRepository` loads the bundled word list (`assets/gre_words.json`) plus any
  words you've added (stored separately, since the bundled list ships read-only
  inside the APK) and keeps a shuffled, persisted rotation through the combined set.
- `WallpaperGenerator` renders the current word onto a full-screen bitmap.
- `WallpaperController` applies that bitmap to the lock screen
  (`WallpaperManager.FLAG_LOCK`).
- `WallpaperUpdateService` is a foreground service that listens for
  `ACTION_SCREEN_ON` and triggers a refresh; `BootReceiver` restarts it after the
  device reboots, if you had it enabled.

## Getting started

**Requirements:** Android Studio (Meerkat or newer), JDK 17.

```bash
git clone https://github.com/MananS8805/VocabLearn.git
cd VocabLearn
```

Open the project in Android Studio and hit **Run**, or build from the command line:

```bash
./gradlew installDebug
```

## Adding your own words

Tap **Add a new word** on the main screen and fill in the word and its meaning
(part of speech and an example sentence are optional). Duplicate words (matched
case-insensitively) are rejected. New words join the rotation immediately.

## Building a signed release

Release builds are signed via a `keystore.properties` file at the project root
(kept out of version control — see `.gitignore`):

```properties
storeFile=keystore/your-release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

With that in place:

```bash
./gradlew assembleRelease
```

The signed APK lands at `app/build/outputs/apk/release/app-release.apk`.

## Permissions

| Permission | Why it's needed |
|---|---|
| `SET_WALLPAPER` | Apply the generated wallpaper to the lock screen |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the screen-on listener alive in the background |
| `POST_NOTIFICATIONS` | Show the required "WallLearn is running" notification on Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Restart the listener after a reboot, if it was enabled |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional — ask not to be killed by battery optimization |

## Tech stack

Kotlin · View Binding · Material Components · min SDK 24 · target/compile SDK 35
