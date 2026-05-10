# codetalker companion — User Guide

This is the end-user guide for the codetalker companion Android app on
the XREAL Beam Pro. If you're a developer looking to contribute, see
[DEVELOPER-GUIDE.md](./DEVELOPER-GUIDE.md) instead.

---

## What it does

codetalker companion lets you talk to Claude Code on your desktop
through your XREAL Beam Pro and Air 2 Pro glasses. Pair once with a
QR code, then:

- **Tap the side button** on your Beam Pro to ask a question.
- **Listen** to replies through earbuds while you read code on your
  monitor.
- **See live captions** on your AR glasses without taking your eyes
  off your work.

Everything runs over your local network — there is no cloud, no
account, and your audio never leaves your network.

---

## 1. Install + first launch

### 1.1 Install

The app ships through three channels:

- **Google Play** — search for "codetalker companion" or follow the
  link from your codetalker desktop dashboard.
- **XREAL Store** — search for "codetalker companion" in the on-device
  XREAL Store.
- **GitHub Releases** — sideload the APK from
  https://github.com/OpenCircuitDev/codetalker-pro/releases/latest.

### 1.2 First launch

The first time you open the app, three things happen:

1. **Welcome tour.** A three-page walkthrough explains what the app
   does and points you at the codetalker desktop installer if you
   haven't already got it running.
2. **Permission requests.** Camera (for the QR pairing scan),
   microphone (for push-to-talk), and notifications (for the
   foreground-service status). Each permission is explained on its
   own screen before the system prompt appears.
3. **Crash report opt-in.** A one-time dialog asks whether you want
   to share anonymous crash reports. Default: **no**. You can change
   the answer any time in Preferences.

After the tour, you land on the **Pairing** screen.

---

## 2. Pair with the codetalker desktop

### 2.1 Make sure the desktop is running

Open the codetalker desktop app on your computer. Confirm the
dashboard is reachable on your local network — it should show a
**Pair AR Companion** button in the Preferences panel.

### 2.2 Scan the QR

In the Beam Pro:

1. Tap **Scan QR**.
2. Point the camera at the QR shown in the dashboard's Preferences
   panel.
3. The app decodes the daemon URL + pairing token from the QR and
   stores them in encrypted preferences.

You'll see the session list as soon as pairing completes.

### 2.3 Manual pairing (fallback)

If the QR scan won't work (poor lighting, dirty lens, etc.), tap
**Or enter daemon URL manually**. Type the daemon URL (e.g.
`http://192.168.1.86:17832`) and the pairing token from the dashboard's
"Pair AR Companion" panel.

### 2.4 Unpair

Long-press the Sessions menu → **Preferences** → **Unpair this
device**. Confirms with a dialog. Unpairing wipes the encrypted
prefs; you'll need a fresh QR to reconnect.

---

## 3. Day-to-day

### 3.1 Pick a session

The session list shows every Claude Code session running on your
desktop. Live sessions have a green dot. Tap a row to open its
detail screen.

### 3.2 Make a session active

In the detail screen, tap **Set active**. The active session is the
one whose audio + captions stream to the AR HUD; only one session
can be active at a time.

### 3.3 Tune the experience

In the same detail screen:

- **Speaking mode** — `brief` / `direct` / `live` / `trigger`.
  - **brief**: short summaries.
  - **direct**: full responses, no commentary.
  - **live**: streams as the response is generated.
  - **trigger**: only speaks when explicitly asked.
- **Voice** — pick from the daemon's voice library or attach a
  character with a cloned voice.
- **Cadence (live mode)** — slow / normal / fast.
- **Mute / Speaking** — pauses narration without dropping the live
  caption stream.
- **Markup treatments** — choose how code blocks, file paths, todo
  updates, etc. are read out. Six toggles in three categories.

Every change is sent to the daemon as an overlay PUT and applied
immediately.

### 3.4 Speak (push-to-talk)

With a session active:

1. **Press and hold** the Beam Pro side button. The HUD shows
   "Listening…".
2. Speak your question.
3. **Release** the side button. The app transcribes locally on the
   daemon, injects the transcript into the buddy session, and streams
   the reply back as audio + captions.

The reply audio plays through whatever output device Android has
selected (earbuds, speaker, USB-C audio).

### 3.5 Glasses

Connect XREAL Air 2 Pro glasses via USB-C. The Beam Pro routes the
HUD overlay to the glasses' secondary display automatically — the
caption + active-session badge ride on top of the world view.

If the glasses aren't recognised: open Preferences → Diagnostics and
check the **Glasses** card. It shows whether the secondary display is
enumerated; if it isn't, unplug and reconnect.

---

## 4. Glasses + audio routing

### 4.1 Recommended setup

For the canonical XREAL workflow:

1. Connect Air 2 Pro to Beam Pro via USB-C.
2. Plug your earbuds into the Beam Pro's audio jack (or pair
   Bluetooth earbuds).
3. Open codetalker companion. The HUD renders on the glasses; audio
   plays through your earbuds; the phone screen is dim, just for
   tapping into Preferences when needed.

### 4.2 Foreground service

While the app is the active codetalker source, it shows a notification
saying "codetalker is listening". Tapping the notification opens the
dashboard. Swiping it away tells the system you're done — the audio
stream stops, and the SSE connection closes.

### 4.3 Audio focus

If a phone call comes in, codetalker pauses cleanly, the call rings,
and codetalker resumes where it left off. If a music app permanently
takes audio focus (rare but possible), the error banner explains and
gives you a "Reclaim audio" button.

---

## 5. Troubleshooting

### "codetalker desktop unreachable"

Most common cause: the desktop and Beam Pro are on different
networks. Open Diagnostics and check:

- Pairing token expiry — if expired, re-pair via QR.
- Daemon last-success timestamp — if more than 30 seconds ago,
  Wi-Fi may have flipped.
- Network RTT — over 200 ms suggests a saturated network.

If the desktop is on a Tailscale network and the Beam Pro isn't, you
need to either (a) install Tailscale on the Beam Pro, or (b) put both
on the same LAN. The pairing token is bound to a specific URL, so
moving networks invalidates it.

### "This session isn't currently live in Claude Code"

The session you tried to activate has stopped streaming on the
desktop. Tap **Refresh sessions** to reload the list and pick a
different one.

### "Pairing expired"

Re-pair via QR from the dashboard's Preferences panel.

### Audio cuts out when the screen turns off

The foreground-service notification must be present. If you swiped
it away, reopen the app and audio resumes. If your OEM has
aggressive battery-saver settings, allow codetalker to run in the
background unrestricted via Android Settings → Apps → codetalker
companion → Battery.

### Voice doesn't match the character I attached

The character's `voice_ref` must point at a voice that exists in your
daemon's library. If the voice has been removed from the library, the
daemon falls back to the default voice. Check the desktop dashboard's
Voice Library panel.

### QR scan won't decode

- Make sure the camera lens is clean.
- Try the manual entry path (Pairing screen → "Or enter daemon URL
  manually").
- Re-generate the QR from the dashboard. QRs expire after 30 minutes.

### Crashes on startup

If the app crashes immediately on launch, your DataStore preferences
may be corrupted from an interrupted update. Clear app data via
Android Settings → Apps → codetalker companion → Storage & cache →
Clear storage. You'll need to re-pair.

If crashes persist, opt into crash reporting in Preferences and the
maintainer will see the trace.

---

## 6. FAQ

### Do you need an internet connection?

No. The app talks only to the codetalker desktop you paired with,
which lives on your local network. If the desktop is on Tailscale,
you can use codetalker over Tailscale too — but neither side needs
public-internet access.

### Does anything I say leave my network?

No. Audio is captured on the Beam Pro, sent to your codetalker
desktop, transcribed there, and the transcript stays on your machine.
Audio is never written to disk on the Beam Pro and never sent to a
third party.

### Why does the app need camera permission?

Only to scan the pairing QR once. After pairing, the camera is not
used. If you'd rather not grant camera access, use the manual
pairing path.

### Why is there a notification while the app is running?

Android requires foreground services to display a status notification
while playing audio in the background. Tapping the notification opens
the app; swiping it away cleanly stops the audio + SSE.

### Can I run codetalker companion without the desktop?

No. The app is a UI shell over the desktop daemon. Without the
daemon, there's nothing to talk to.

### Can I use the app with multiple desktops?

Not in v0.1.0. One pairing token = one daemon. To switch, unpair and
re-pair against the new daemon. v1.x may add a daemon switcher.

### How do I get help?

- **Issues + feature requests:** GitHub
  https://github.com/OpenCircuitDev/codetalker-pro/issues
- **Privacy / data deletion:** becky@nativeteachingaids.com
- **In-app diagnostics:** long-press Sessions → Preferences →
  Diagnostics. Screenshots of that screen are useful in bug reports.

---

## 7. What's next

Future updates land via the same channel you installed from
(Play / XREAL / sideload). The AboutScreen has a **Check for updates**
link that opens the GitHub Releases page directly.

When v1.x ships, we'll surface a one-time release-notes summary on
first launch.
