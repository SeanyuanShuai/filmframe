# JustFrame 📷

> Wrap a photo in a photographer's frame and let its EXIF write the caption.

JustFrame puts a clean editorial border around your photo and fills the caption straight from the file: camera, lens, aperture, shutter, ISO. Pick a template, import, export. Everything runs on-device. No photo ever leaves your phone.

> **Status: v0.2, pre-alpha.** Built in the open as a vibe-coding learning project with Claude Code. Expect rough edges.

## What works now

- Import from your gallery through a built-in picker; up to 30 photos at once for batch framing.
- Caption filled from EXIF: camera, lens, aperture, shutter, ISO.
- Five templates: Classic (经典留白), Bold (高反差), Solid (纯色底), Minimal (极简无界), Polaroid (宝丽来复古).
- Auto border detection strips an in-camera frame first — a phone's built-in Hasselblad/Leica border, say — so you don't end up with a frame inside a frame.
- Per-photo adjustments: border width, caption font size, custom caption text, optional watermark.
- Export up to original resolution, with the source EXIF written back into the output. Files save to Pictures/JustFrame and are **never uploaded anywhere**.

Frames render on a Canvas (no OpenCV). Captions are set in Cormorant, DM Serif Display, and Inter, all OFL-licensed and bundled.

## Tech

- Kotlin + Jetpack Compose
- Coil 3 for image loading, AndroidX ExifInterface for metadata
- Min SDK 26, target 36
- No backend, no analytics, no network calls

## Build

Open the project in Android Studio, plug in a device (or start an emulator), and run.

## Roadmap

The import → frame → export loop is done, across five templates and batch mode. Next up:

- [ ] Custom templates: editable ratios, fonts, and logo position, saved as presets
- [ ] Aspect presets for Instagram and 小红书
- [ ] Polish pass on the new three-tab interaction

Already shipped:

- [x] Pick photos, read EXIF, render frames on device
- [x] Five built-in templates with live switching
- [x] Full-resolution export with EXIF preserved
- [x] Batch processing
- [x] Interaction redesign — three-tab layout (Gallery / Create / Settings), one immersive editor, Hasselblad-orange accent

## License

MIT — see [LICENSE](LICENSE).

## About

Built by [@SeanyuanShuai](https://github.com/SeanyuanShuai), a PM learning to ship by vibe-coding with Claude Code.
