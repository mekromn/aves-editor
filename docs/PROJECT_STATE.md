# Aves Editor Fork — Project State

Last updated: 2026-08-23

## Repository

- Fork: `mekromn/aves-editor`
- Upstream: `deckerst/aves`
- Upstream/default development branch: `develop`
- Current working branch: `feature/non-destructive-editor-foundation`
- Base upstream commit: `a17ee5575564266cdec747f1b1eca77989f3a689`

## P0 product goals

1. **Maximum-fidelity viewer to the limits of the hardware.**
2. **Full non-destructive image editor integrated into Aves.**
3. **No hidden image enhancement in Reference viewing mode.**
4. **Ultra HDR/HDR and wide-gamut content treated as first-class media.**
5. **Every important project fact documented in-repo for continuity.**

## Current architectural direction

### Aves remains the application/viewer foundation

Aves already provides:

- gallery/navigation/media indexing;
- a raster viewer with tiled region decoding for large images;
- an unfinished editor shell (`ImageEditorPage`, editor control panel, transform/crop path);
- Android/native media-edit plumbing and a `MediaEditService` abstraction;
- an existing Save Copy UI entry point whose editor handler is currently unfinished upstream.

The fork will extend this existing editor rather than create a disconnected second application architecture.

### Non-destructive editing

The source image remains untouched during editing.

The intended model is:

`source image -> persistent edit recipe/stack -> high-precision live preview -> explicit export/render`

Required behavior:

- persistent per-image edit recipe;
- undo/redo;
- individual operation enable/disable;
- operation reorder/duplicate/reset/delete later;
- hold-to-preview original;
- split comparison later;
- presets/edit-recipe reuse later;
- explicit export instead of implicit destructive rewrites.

### ImageToolbox saving/export influence

A fork of `T8RIN/ImageToolbox` is available as `mekromn/ImageToolbox`.

The useful architecture to adapt is the separation between:

- rendering/compression;
- an image save target carrying encoded bytes/format/source URI/metadata/filename;
- file-controller behavior for destination selection, save-beside-original, overwrite/new-copy, metadata preservation, media scanning, and export profiles.

Do not blindly copy unrelated ImageToolbox editor code. Preserve Apache-2.0 attribution/notice requirements for any adapted source.

### Initial editing feature set

Planned controls include:

- Exposure
- Brightness
- Contrast
- Highlights
- Shadows
- Whites
- Blacks
- Ambiance-style local tonal balancing
- Tonal Contrast-style high/mid/low local contrast with highlight/shadow protection
- Structure
- Sharpening
- Skin Tone selective adjustment
- Blue Tone selective adjustment
- Temperature/Tint
- Saturation/Vibrance
- Pixel Adaptive-style color-volume expansion later
- Curves and analysis scopes later

Snapseed/Google Photos behavior is inspiration/reference only where proprietary algorithms are not published. Functional equivalents should be independently implemented and, if useful, calibrated through black-box input/output testing.

## Maximum-fidelity viewer

See `docs/MAXIMUM_FIDELITY_VIEWER.md`.

Non-negotiable direction:

- true 1:1 source-pixel inspection;
- physical-display-pixel-aware LOD selection;
- no unnecessary sRGB round trips;
- preserve source profiles/wide gamut where supported;
- explicit color transforms when required;
- HDR/Ultra HDR detection and appropriate HDR display path;
- preserve gain maps and gain-map metadata;
- FP16/high-precision intermediates where useful;
- avoid repeated 8-bit quantization;
- scale-dependent resampling rather than globally forcing one filter;
- seam-safe tiled rendering;
- objective validation for Reference-mode pixel changes.

## Ultra HDR direction

Implementation priority:

1. detect and correctly display Ultra HDR;
2. preserve gain maps through spatial transforms where platform support permits;
3. make tonal/color edits HDR-aware;
4. regenerate an appropriate gain map for edited Ultra HDR output rather than blindly reusing the original;
5. JPEG/R export;
6. investigate Android 16/API 36 HEIC Ultra HDR support for later export.

The correct long-term model is effectively:

`Ultra HDR source -> reconstruct/represent HDR signal -> high-precision edits -> derive SDR rendition + updated gain map -> Ultra HDR export`

## Zoom

User requirement: double upstream maximum pinch-to-zoom.

Implementation on the working branch centralizes this in the Aves magnifier scale clamp via `maxScaleMultiplier = 2.0`, so raster viewing and editor magnification share the same doubled ceiling. Upstream raster max is 5x, producing a 10x effective ceiling in the fork.

Relevant commit:

- `4ffde695c5196c189749c5a370007e0f389e6c4d` — central doubled maximum-scale behavior.

## Documentation/continuity

P0 rule established in `docs/CONTINUITY_PROTOCOL.md`.

Relevant commit:

- `88256bc811e0c144dcf88f9e1456ec924a91c71d` — mandatory continuity protocol.

Maximum-fidelity viewer requirement was captured in:

- `bbd77fd8b08857f389924ef722d7072296da702c` — create `docs/MAXIMUM_FIDELITY_VIEWER.md`.

## Current implementation status

Already landed on `feature/non-destructive-editor-foundation`:

- dedicated feature branch from upstream `develop` base;
- maximum-fidelity viewer design document;
- doubled central magnifier maximum-scale behavior;
- continuity protocol document.

In progress / next implementation work:

1. audit full raster decode/render/display chain;
2. identify color-space and bit-depth losses;
3. implement/validate 1:1 source-pixel mode;
4. improve tile LOD selection using physical display pixels;
5. investigate Ultra HDR detection/display activation in current Flutter/Android path;
6. build non-destructive edit-recipe model and persistence;
7. wire edit panels into existing Aves editor shell;
8. implement live high-precision preview path;
9. implement first baseline adjustments;
10. connect explicit edited-image export/save-copy path;
11. layer richer ImageToolbox-inspired export profiles and destination behavior.

## Known uncertainties requiring measurement/inspection

- exact color-space preservation through Aves decode -> Flutter image -> Impeller -> Android surface;
- actual bit depth/intermediate format used for relevant Flutter/Impeller paths on Android;
- how gain maps are exposed/preserved through the current Aves/Flutter image-provider route;
- whether editor preview should use Flutter runtime shader, Android native GPU, or a hybrid path for maximum fidelity across all required formats;
- highest-quality export encoders/formats available without introducing undesirable conversions;
- format-specific handling for RAW, TIFF, HEIF/HEIC, AVIF, JPEG XL if/where supported.

These must be established by source inspection and tests, not assumed.
