# Store assets

Listing copy, screenshots, feature graphics, and Data Safety responses
for the three release surfaces:

```
store-assets/
├── google-play/
│   ├── listing.md             # Full Play Console listing copy + screenshot manifest
│   ├── data-safety-form.md    # Filled-out responses for the Data Safety section
│   ├── feature-graphic.md     # 1024×500 feature graphic spec (PNG generation pending)
│   └── screenshots/           # Symlink-style references to ../docs/mockups/screenshots/
├── xreal-store/
│   └── listing.md             # XREAL Store listing copy
└── github-releases/
    └── release-notes-template.md
```

## Screenshots

Screenshots live in the public docs at `../docs/mockups/screenshots/`
to keep them de-duplicated. The listing files reference them by relative
path. When uploading to Play Console / XREAL Store, point the upload
dialog at those PNGs directly — they're sized for phone form factor and
include real Beam Pro device frames.

## Refreshing

After every release-grade UI change, regenerate the screenshot set with
`scripts/e2e/run_release_check.sh` (which calls into the per-task screen
captures) before submitting the listing update.
