Messenger-style floating bubble for Android. Talk over Tailscale to **saltvoice-local** `remote_bridge` on your Windows PC (Whisper), then send the transcript toward **Grok Bot on Android** and **My Brand Studio Capture** without leaving Camera, Chrome, or the cut you are making.

- Package: `com.easton.saltbubble`
- Intended GitHub repo: [github.com/GeorgieXRPL/salt-bubble](https://github.com/GeorgieXRPL/salt-bubble)
- Whisper bridge: [github.com/GeorgieXRPL/saltvoice-local](https://github.com/GeorgieXRPL/saltvoice-local)

This is the phone HUD for Easton / Great Gatspy. It is not a Termux CLI and it does **not** inject into Grok Bot — there is no public inject API.

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

## saltvoice-local (Whisper over Tailscale)

Salt Bubble does **not** run Whisper on the phone. It POSTs 16 kHz mono WAV to **saltvoice-local** on your PC. Enable **`remote_bridge`** (that is the default). The existing `TranscriptionClient` talks HTTP over Tailscale:

| Setting | Default |
| --- | --- |
| Host | `mypc.tailc2bc11.ts.net` |
| Port | `8791` |
| HTTPS | off (HTTP over Tailscale) |
| Base URL | `http://mypc.tailc2bc11.ts.net:8791` |
| JSON | `POST /transcribe` — multipart `audio` or `file`, header `X-Salt-Token`, body `{ "text": "…" }` |
| Plain text | `POST /transcribe.txt` (fallback) |
| Token | Not printed. Open the console `PAIR PHONE: saltbubble://bridge?…` link on the phone. |

**In the app:** grant overlay + mic, then open **PAIR PHONE** from the saltvoice-local console on this phone. Host and token fill in. Pick **Grok on Phone / PC / Both**. Use **Ping bridge** to confirm the private tailnet hop. Advanced host/port stays under Settings → Advanced.

HTTP cleartext is allowed on purpose — the default Tailscale bridge is `http://`, not HTTPS.

Repo: [github.com/GeorgieXRPL/saltvoice-local](https://github.com/GeorgieXRPL/saltvoice-local)

## Grok Bot on Android

There is **no Grok inject API**. Home has a **Grok lands** switch:

- **Phone** (default) — clipboard + open Grok Bot. Safest. The PC `/send-to-grok` endpoint is not called.
- **PC** — `POST /send-to-grok` pastes into desktop Grok. Phone share stays off.
- **Both** — PC paste first, phone share if the bridge misses.

Phone share still copies the line, then `ACTION_SEND` to `ai.x.grok.bot` (fallback `ai.x.grok`), then a chooser. Toast **Paste in Grok Bot**.

## Brand Studio Capture

Capture URL default: `https://app.mybrandstudio.club/capture`.

When destination is **Brand Studio** or **Both**:

1. Clipboard already has the transcript.
2. Open Capture with `?text=` (or `&text=` if the URL already has a query) URL-encoded prefill.
3. If you set an optional deep link: custom schemes open as-is; `http(s)` links also get the text query.
4. No extra share sheet for Capture (too noisy). Share is only Grok’s last resort.

### Optional hub Agent API

If you paste an Agent API key (`ak_…`) in Settings and leave **POST idea to hub when sending to Brand Studio** on:

- `POST {hubUrl}/api/ideas`
- `Authorization: Bearer {token}`
- body `{"text":"…"}`
- success `200` / `201`

Default hub: `https://brand-studio-hub.fly.dev`. A blank token skips the POST. A failed POST does not undo Grok/Capture send; the overlay shows `Could not POST idea: …`.

### Pair from Brand Studio

Create an Agent key in Brand Studio → **Agents**. On the phone tap **Open Salt Bubble**. That opens:

`saltbubble://pair?hub=…&key=ak_…&capture=…`

or the landing page `https://app.mybrandstudio.club/salt-bubble/pair?…`. Salt Bubble stores the hub URL and key. The key can `POST /api/ideas` only — it cannot publish.

## iOS companion

iOS cannot float a bubble over Camera. Source lives in [`ios/`](ios/). Open `ios/SaltBubble.xcodeproj` on a Mac. Same bridge token, same pair URL, hold-to-talk in the app.

## What v1 does

1. Floating cyan bubble over other apps (`SYSTEM_ALERT_WINDOW`).
2. Tap to expand a small messenger thread (voice/text + status).
3. Hold-to-talk: records 16 kHz mono WAV, POSTs it to saltvoice-local `remote_bridge`, shows the transcript.
4. Per-message send: Grok Bot (clipboard + targeted share / launch + paste), Brand Studio Capture (`?text=` prefill), or both.
5. Optional `POST /api/ideas` when an `ak_` hub token is set.
6. Foreground service notification so the overlay stays alive while you work in other apps.

Out of scope: on-device Whisper, a Windows overlay rewrite, a fake Grok Bot inject API.

## Install and first run

1. Install the debug APK (sideload). Android may warn that the app is from an unknown source — allow it.
2. Open Salt Bubble. Grant **Draw over other apps**, **Microphone**, and (Android 13+) **Notifications**.
3. Confirm the bridge URL matches your PC’s Tailscale MagicDNS name and that [saltvoice-local](https://github.com/GeorgieXRPL/saltvoice-local) is running with **remote_bridge** enabled (default port `8791`).
4. On the PC console, open **PAIR PHONE** (`saltbubble://bridge?…`) on this phone. Do not stream that line. Tap **Ping bridge**.
5. Choose **Grok on Phone / PC / Both**.
6. Optional: Brand Studio → Agents → Open Salt Bubble for the `ak_…` idea key.
7. Tap **Launch bubble**. Say **Salt wake up**, or hold-to-talk.

### Permissions

| Permission | Why |
| --- | --- |
| Draw over other apps | Floating HUD over Camera / Chrome |
| Microphone | Hold-to-talk WAV |
| Notifications | Foreground service while the overlay is up |
| Foreground service (mic + special use) | Stay alive while recording and while the HUD is up |
| Internet | HTTP to the Tailscale bridge and optional hub POST |

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
salt-bubble/
  README.md
  settings.gradle.kts
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/easton/saltbubble/
    MainActivity.kt              # permissions + Grok install check
    SaltBubbleApp.kt            # SettingsStore, TranscriptionClient, BrandStudioClient, SendHelper
    audio/WavRecorder.kt
    data/Models.kt               # AppSettings, CaptureUrls, GrokTargets
    data/SettingsStore.kt
    net/TranscriptionClient.kt  # POST /transcribe to saltvoice-local
    net/BrandStudioClient.kt     # optional POST /api/ideas
    overlay/                     # bubble service, chat controller (do not rewrite)
    send/SendHelper.kt           # clipboard + Grok package share + Capture ?text=
    send/GrokApps.kt            # PackageManager probe
    ui/home/HomeScreen.kt
    ui/settings/SettingsScreen.kt
    ui/overlay/OverlayRoot.kt
  app/src/test/java/com/easton/saltbubble/
    BridgeUrlsTest.kt
  ios/                               # iOS companion (Xcode)
    SaltBubble.xcodeproj
    SaltBubble/
```


## Salt session (Local-parity)

With the bubble launched, Salt mirrors Salt Voice Local:

1. **Standby** — listens for `Salt wake up` (does not auto-send tasks).
2. **Listening** — your spoken instructions auto-send to the current destination.
3. Voice targets — `Salt send to Grok` / `Salt send to Brand Studio` (or Capture).
4. **Standby again** — `Salt go on standby`, `bye`, `Salt cut asleep`, etc.

Audio is chunked on-device and transcribed by Salt Remote Bridge on your PC over Tailscale. Hold-to-talk remains as a manual override.
