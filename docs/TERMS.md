# Terms of service — codetalker companion

**Effective date:** 2026-05-09
**Version:** 1.0 (covers companion app v0.1.0)
**Operator:** Open Circuit Dev (becky@nativeteachingaids.com)

By installing or using the **codetalker companion** Android
application ("the app", "we") you agree to these Terms of Service.
If you do not agree, do not install or use the app.

---

## 1. License

### 1.1 The companion app

The companion app's source code as a whole is © Open Circuit Dev, all
rights reserved. The app is distributed in compiled form via Google
Play, the XREAL Store, and GitHub Releases. You may install and use
copies of the app on devices you own or control.

### 1.2 OSS components

The companion app incorporates open-source components, each licensed
separately. The full list and corresponding license texts are surfaced
in the in-app **About → Third-party libraries** screen and in the
project's `LICENSES/` directory once the open-core repository
(CCT-30 / `codetalker-pro`) is published. The major components are:

| Component | License |
|---|---|
| AndroidX (Compose, Material 3, CameraX, Media3, DataStore, …) | Apache 2.0 |
| OkHttp + okhttp-sse | Apache 2.0 |
| ZXing core | Apache 2.0 |
| Kotlin coroutines | Apache 2.0 |
| Sentry Android SDK | MIT |
| AndroidX core-splashscreen | Apache 2.0 |

### 1.3 No other rights

These terms do not grant you any right to use the "codetalker"
trademark, the cyan→violet orb logo, or the "speak to your code,
listen to your code" tagline beyond passing reference for
interoperability or commentary.

---

## 2. Permitted use

You may:

- Install the app on your own Android devices.
- Pair the app with a codetalker desktop daemon running on a machine
  you own or have explicit permission to use.
- Examine the app's behaviour and network traffic on devices you own.
- File issues, feature requests, and pull requests against the public
  repository (once published).

You may **not**:

- Distribute modified copies of the compiled app under the codetalker
  name without prior written agreement.
- Reverse-engineer the app to extract proprietary signing keys,
  private telemetry endpoints, or internal protocol secrets that are
  not part of the public API surface.
- Use the app to harass, surveil, or intercept the audio of any person
  who has not consented to being recorded by your codetalker daemon.

---

## 3. The codetalker desktop daemon

The codetalker desktop daemon is a separate piece of software. The
app cannot operate without one. Its source, license, and operation
are governed by **its own** documentation and license, which is
distinct from this document.

You are solely responsible for:

- Operating your codetalker desktop daemon in compliance with all
  applicable laws.
- Securing the network path between this app and your daemon. The
  app encrypts the pairing token in transit via TLS when the daemon
  serves on `https://`, and over LAN/Tailnet authenticated by the
  pairing token over `http://` for local addresses.
- Protecting the pairing token from unauthorized access.

---

## 4. No warranty

The app is provided **"as is", without warranty of any kind**, express
or implied. We do not warrant that the app will be uninterrupted, free
of bugs, free of security vulnerabilities, or fit for any particular
purpose.

Specifically, we do not warrant:

- That voice recognition will be accurate.
- That speech synthesis will be intelligible.
- That the AR overlay will render correctly on every glasses model.
- That the foreground service will survive every Android OEM's
  battery-saver intervention.

---

## 5. Limitation of liability

To the maximum extent permitted by law, in no event will Open Circuit
Dev be liable to you for any indirect, incidental, consequential,
special, or punitive damages, loss of profits, loss of data, or
business interruption arising out of your use of the app, even if we
have been advised of the possibility of such damages.

If a court holds these limitations unenforceable, our aggregate
liability for any claim arising out of or relating to the app is
limited to the greater of (a) the amount you paid us for the app —
the app is currently free, so this is zero — and (b) USD $100.

---

## 6. Indemnity

You agree to defend, indemnify, and hold harmless Open Circuit Dev
from any claim arising out of (a) your use of the app outside the
permitted-use scope above, or (b) any audio, transcript, or session
content that you cause your codetalker daemon to send through the
app — we never see that content but are not responsible for what
your daemon does with it.

---

## 7. Termination

You may stop using the app at any time by uninstalling it.

We may terminate your right to use the app if you breach these terms
or use the app in a way that violates applicable law. Termination
does not affect the disclaimers in §4 and §5, the indemnity in §6,
or any open-source license you continue to hold.

---

## 8. Privacy

The companion-app privacy policy is at
[docs/PRIVACY-POLICY.md](./PRIVACY-POLICY.md). It describes what data
the app handles, how it stores that data, and how to delete it.
Reading the privacy policy is required before installing the app.

---

## 9. Changes

We may update these terms periodically. If a change is material we
will:

1. Update this document and bump the version at the top.
2. Bump the app's `versionCode` so existing users see an update
   prompt.
3. Surface a one-time in-app notice summarizing the change.

Past versions live in the project's git history, publicly auditable
once the open-core repository is published.

---

## 10. Governing law

These terms are governed by the laws of the United States, without
regard to its conflict-of-law principles. Any dispute arising out of
or relating to these terms or the app must be resolved in the federal
or state courts located in your jurisdiction of residence, to the
extent the law allows.

---

## 11. Contact

For licensing questions, takedown requests, or anything else covered
by these terms:

- **Email:** becky@nativeteachingaids.com
- **GitHub issues:** https://github.com/OpenCircuitDev/codetalker-pro/issues
