# Feature graphic spec — 1024×500

This is the spec for the Google Play "feature graphic" hero (1024×500
PNG, mandatory). It also doubles as the XREAL Store banner.

## Composition

```
┌────────────────────────────────────────────────────────────────────┐
│                                                                    │
│         [orb]   codetalker companion                               │
│         (cyan→violet, 180px diameter, vertically centred)          │
│                                                                    │
│                 speak to your code, listen to your code            │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

Background: linear gradient #0A0B10 → #11141C, 45° angle.
Wordmark: Inter ExtraBold 64px, #E6E8EE.
Tagline: Inter Regular 24px, #AAB1C0, 12px below wordmark.
Orb: same gradient as ic_launcher_foreground (cyan #22D3EE → violet
     #A855F7), centred at (160, 250). 6px white waveform glyph centred.
Safe area: 60px outer margin on every side. Test crop in Play Console
preview before locking.
```

## Generation

Until a designer lays this out in vector form, the feature graphic can
be rendered programmatically by re-using the launcher icon vector + a
text overlay. See `scripts/generate-feature-graphic.sh` (TBD; tracked
under H.5 polish).

For v0.1.0 submission, the launcher icon at 512×512 is acceptable as a
"high-res icon" while a placeholder feature graphic ships using the same
artwork. Replacement to follow before public production track.

## Output paths

- `store-assets/google-play/feature-graphic.png` (1024×500)
- `store-assets/google-play/icon-512.png` (512×512 high-res icon)
- `store-assets/xreal-store/banner.png` (same 1024×500 asset)
