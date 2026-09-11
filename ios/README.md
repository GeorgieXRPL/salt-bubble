# Salt Bubble for iOS

Companion HUD for the same **saltvoice-local** bridge and **Brand Studio** pair link as the Android app.

iOS cannot draw a bubble over Camera. This is a full-screen hold-to-talk app you switch to (or open from the pair link).

## Open in Xcode

Needs a Mac with Xcode 15+ and an Apple ID for device install.

```bash
cd ios
open SaltBubble.xcodeproj
```

Select your Team under **Signing & Capabilities**, then Run on a phone.

## Pair Brand Studio

1. In Brand Studio → **Agents**, create a key.
2. On the iPhone tap **Open Salt Bubble**, or open the copied `https://app.mybrandstudio.club/salt-bubble/pair?…` link and then **Open Salt Bubble**.
3. The app stores hub URL + `ak_…` and POSTs `/api/ideas` when you send to Brand Studio.

Custom URL scheme: `saltbubble://pair?hub=&key=&capture=`

## saltvoice-local

Same as Android:

- Host: PC MagicDNS name (example `mypc.tailc2bc11.ts.net`)
- Port: `8791`
- HTTP over Tailscale
- Paste `BRIDGE TOKEN` from the Windows console

Hold **Hold to talk**, release, then **Grok** / **Studio** / **Both**.
