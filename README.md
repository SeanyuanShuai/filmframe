# JustFrame 📷

> Wrap a photo in a photographer's frame and let its EXIF write the caption.

JustFrame puts a clean editorial border around your photo and fills the caption straight from the file: camera, lens, aperture, shutter, ISO. Pick a style, import, export. Everything runs on-device. No photo ever leaves your phone.

> **Status: v0.2, pre-alpha.** Built in the open as a vibe-coding learning project with Claude Code. Expect rough edges.

## What works now

- Import from your gallery through a built-in picker; up to 30 photos at once for batch framing.
- Caption filled from EXIF: camera, lens, aperture, shutter, ISO.
- Template groups — pick a style family on the home screen, and the editor shows only that group's variants, each previewed live on your own photo. Shipped so far: 杂志留白 Editorial Margin, 美术馆装裱 Passepartout, 片边底纹 Rebate (35mm sprocket edges), three variants each.
- Auto-removes a phone's built-in watermark frame first — the bottom Hasselblad / Leica / Zeiss / XMAGE bar, or a 4-side mat — so you don't get a frame inside a frame.
- Per-photo adjustments: border width, caption size, EXIF caption on/off, optional watermark. Hold to compare with the original.
- Export up to original resolution, with the source EXIF written back into the output (toggleable). Files save to Pictures/JustFrame and are **never uploaded anywhere**.
- Tiered haptics on key actions, a frosted-glass tab bar, and a portrait lock.

Frames render on a Canvas (no OpenCV). The UI is set in an editorial Chinese/Latin serif (Source Han Serif, subset and bundled); rendered captions use Cormorant, DM Serif Display, and Inter. All fonts are OFL-licensed.

## Tech

- Kotlin + Jetpack Compose
- Coil 3 for image loading, AndroidX ExifInterface for metadata
- Min SDK 26, target 36
- No backend, no analytics, no network calls

## Build

Open the project in Android Studio, plug in a device (or start an emulator), and run.

## Roadmap

The import → frame → export loop is done, across three template groups and batch mode. Next up:

- [ ] Two more groups: 印样校样 Contact Sheet (darkroom proof annotations) and 跨幅长卷 Carousel (multi-tile panorama export)
- [ ] Custom templates: editable ratios, fonts, and logo position, saved as presets

Already shipped:

- [x] Pick photos, read EXIF, render frames on device; full-resolution export with EXIF preserved
- [x] Batch processing, per-photo styling
- [x] Template groups — home picks a style family; the editor shows that group's variants with live per-photo previews
- [x] Auto-remove of phone watermark frames, including bottom-bar watermarks (Hasselblad / Leica / Zeiss / XMAGE)
- [x] Three-tab layout (Gallery / Create / Settings), one immersive editor, Hasselblad-orange accent, editorial serif UI

## License

MIT — see [LICENSE](LICENSE).

## About

Built by [@SeanyuanShuai](https://github.com/SeanyuanShuai), a PM learning to ship by vibe-coding with Claude Code.
