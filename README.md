
Messenger-style floating bubble for Android. Talk over Tailscale to **Salt Remote Bridge** on your Windows PC (Whisper), then send the transcript toward **Grok Bot** and **Brand Studio Capture** without leaving Camera, Chrome, or the cut you are making.

- Package: `com.easton.saltbubble`
- Intended GitHub repo: [github.com/GeorgieXRPL/salt-bubble](https://github.com/GeorgieXRPL/salt-bubble)

This is the phone HUD for Easton / Great Gatspy. It is not a Termux CLI and it does not inject into Grok Bot — there is no public inject API. Send copies the line, toasts **Paste in Grok Bot**, and opens a share sheet.

## Clone

Once the GitHub repo exists:

```bash
git clone https://github.com/GeorgieXRPL/salt-bubble.git
cd salt-bubble
```

If that URL 404s, create an empty repo named `salt-bubble` under **GeorgieXRPL** (no README, no .gitignore), then push this tree — see [Publish to GitHub](#publish-to-github). You can also unzip the source archive and work from that folder.

## Open in Android Studio or Cursor

**Android Studio (recommended to build/install)**

1. Install Android Studio (Ladybug / Koala or newer) with Android SDK 35 and JDK 17+.
2. **File → Open** and choose this folder (the one that contains `settings.gradle.kts`).
3. Let Gradle sync. If it asks for an SDK, set it to your Android SDK (often `~/Android/Sdk` or `%LOCALAPPDATA%\Android\Sdk`).
4. Android Studio writes `local.properties` for you. Do not commit that file.

**Cursor**

1. **File → Open Folder** on the same repo root.
2. JDK 17+ on `PATH`. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your SDK.
3. Build with the Gradle wrapper (below). You do not need Origin, Cursor Cloud, or any special remote to compile.

```bash
# Windows PowerShell example
Copy-Item local.properties.example local.properties
# then edit sdk.dir=C:\\Users\\YOU\\AppData\\Local\\Android\\Sdk

# macOS / Linux
cp local.properties.example local.properties
# then edit sdk.dir=/Users/YOU/Library/Android/sdk
#      or sdk.dir=$HOME/Android/Sdk
```

## Build a debug APK

Needs JDK 17+ and Android SDK (platform 35).

```bash
./gradlew :app:assembleDebug
```

On Windows: `gradlew.bat :app:assembleDebug`.

APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use Android Studio **Run** on a USB-debugged phone.

## Tailscale bridge URL

Salt Bubble does **not** run Whisper on the phone. It POSTs audio to Salt Remote Bridge on your PC.

| Setting | Default |
| --- | --- |
| Host | `mypc.tailc2bc11.ts.net` |
| Port | `8791` |
| HTTPS | off (HTTP over Tailscale) |
| Base URL | `http://mypc.tailc2bc11.ts.net:8791` |
| JSON | `POST /transcribe` — multipart `audio` or `file`, body `{ "text": "…" }` |
| Plain text | `POST /transcribe.txt` (fallback) |

**In the app:** open Salt Bubble → **Host, port, Capture URL**. Change host/port if your MagicDNS name is different. Phone and PC must be on the same tailnet, and the Windows bridge must be listening on that port.

HTTP cleartext is allowed on purpose — the default Tailscale bridge is `http://`, not HTTPS.

## What v1 does

1. Floating cyan bubble over other apps (`SYSTEM_ALERT_WINDOW`).
2. Tap to expand a small messenger thread (voice/text + status).
3. Hold-to-talk: records 16 kHz mono WAV, POSTs it to the PC bridge, shows the transcript.
4. Per-message send: Grok Bot (clipboard + share + paste toast), Brand Studio Capture (clipboard + open URL), or both.
5. Foreground service notification so the overlay stays alive while you work in other apps.

Out of scope: on-device Whisper, a Windows overlay rewrite, a fake Grok Bot API, Brand Studio private write APIs.

## Install and first run

1. Install the debug APK (sideload). Android may warn that the app is from an unknown source — allow it.
| Foreground service (mic + special use) | Stay alive while recording and while the HUD is up |
| Internet | HTTP to the Tailscale bridge |

## Publish to GitHub

This project is meant to live at `https://github.com/GeorgieXRPL/salt-bubble` so you can `git clone` it like any other repo. You do **not** need Origin or Cursor Cloud.

1. On GitHub: **New repository** → owner **GeorgieXRPL** → name **`salt-bubble`** → Public or Private → **do not** add a README or .gitignore.
2. From this folder (cloned tree or unzipped source):

```bash
# If this folder is already a git repo (has .git):
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/GeorgieXRPL/salt-bubble.git
git branch -M main
git push -u origin main

# If you only have an unzipped copy (no .git):
git init
git add .
git commit -m "Salt Bubble Android MVP"
git branch -M main
git remote add origin https://github.com/GeorgieXRPL/salt-bubble.git
git push -u origin main
```

Or, with GitHub CLI on your machine (after `gh auth login`):

```bash
gh repo create GeorgieXRPL/salt-bubble --source=. --private --push --remote=origin
```

Use `--public` instead of `--private` if you want it public.

## Project layout

```
