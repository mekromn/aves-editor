# Aves Editor Fork — Project State

Last updated: 2026-08-23

## Repository

- Fork: `mekromn/aves-editor`
- Upstream: `deckerst/aves`
- Upstream/default development branch: `develop`
- Current working branch: `feature/non-destructive-editor-foundation`
- Base upstream commit: `a17ee5575564266cdec747f1b1eca77989f3a689`
- Draft PR: `#1` — **Editor foundation: fidelity roadmap, durable recipes, 10x zoom**
- PR base/head: `develop` <- `feature/non-destructive-editor-foundation`

## P0 product goals

1. **Maximum-fidelity viewer to the limits of the hardware.**
2. **Full non-destructive image editor integrated into Aves.**
3. **No hidden image enhancement in Reference viewing mode.**
4. **Ultra HDR/HDR and wide-gamut content treated as first-class media.**
5. **Scientific inspection/comparison/validation tools.**
6. **Every important project fact documented in-repo for 101% continuity.**

Hard pixel rule:

> Every pixel-changing feature must either improve fidelity/correctness or be explicitly identified as an edit/enhancement. Reference mode never receives hidden beautification.

## Documentation sources of truth

Read in this order when resuming:

1. `docs/PROJECT_STATE.md`
2. `docs/CONTINUITY_PROTOCOL.md`
3. `docs/ROADMAP.md`
4. latest relevant `docs/chat/*.md`
5. subsystem design/audit docs, especially `docs/MAXIMUM_FIDELITY_VIEWER.md` and `docs/VIEWER_RENDER_PIPELINE_AUDIT.md`
6. current branch/PR history

## Current architecture

### Application/viewer foundation

Aves remains the app foundation and already supplies:

- gallery/navigation/media indexing;
- raster viewer with tiled region decoding;
- editor shell (`ImageEditorPage`, transform/crop, control panel);
- native Android media fetch/edit/storage channels;
- `MediaEditService` and an existing Save Copy UI entry point whose upstream editor handler is unfinished.

### Non-destructive editor model — IMPLEMENTED FOUNDATION

The source image remains untouched while editing.

Current model:

`source -> versioned ordered EditRecipe -> live preview (next) -> explicit full-resolution render/export (later)`

Implemented on the feature branch:

- `EditSourceIdentity` with URI, modified time, size, dimensions and MIME type;
- versioned `EditRecipe` schema;
- ordered `EditOperation` stack;
- stable string operation types/IDs;
- generic JSON-compatible parameter payloads;
- enabled state;
- opacity;
- optional mask reference;
- forward-compatible preservation of unknown future operation types;
- stack append/replace/remove/move;
- `EditRecipeController` with undo/redo;
- continuous-interaction grouping so a slider drag can become one undo step;
- enable/disable, opacity, mask assignment, duplicate, delete, reorder and reset-all controller operations;
- unit tests covering serialization/unknown operation preservation/order/undo-redo/interaction grouping/duplicate IDs.

### Durable edit persistence — IMPLEMENTED FOUNDATION

Edit recipes use a dedicated SQLite database:

`editor.db`

This is intentionally separate from Aves `metadata.db` because media/index metadata is rebuildable while edit recipes are user-created work that must survive metadata-cache resets.

Implemented behavior:

- recipe JSON persisted by source URI;
- source identity retained inside recipe;
- exact source fingerprint match restores automatically;
- same URI with a changed source fingerprint is treated as stale and is **not** automatically applied;
- stale/malformed recipes are not silently deleted, preserving future recovery options;
- `ImageEditorPage` reloads recipes and autosaves changes with a short debounce;
- final recipe is flushed when the editor closes.

Future persistence work:

- recovery/rebind UI for moved/replaced sources;
- sidecar option;
- preset storage;
- mask storage;
- history persistence beyond current recipe if desired.

## ImageToolbox saving/export influence

Fork available: `mekromn/ImageToolbox`.

Architecture to adapt:

- render/compress separately from saving;
- output target contains encoded bytes/format/source URI/metadata/filename;
- storage controller handles chosen folder, beside original, overwrite/new-copy, one-time destination, metadata policy and media scanning.

Do not blindly import ImageToolbox editor modules. If actual code is copied/adapted, document exact provenance and retain Apache-2.0 attribution/NOTICE requirements. Aves BSD-3-Clause requirements remain applicable.

## Zoom — IMPLEMENTED

User requirement: double upstream maximum pinch zoom.

The shared magnifier clamp now applies `maxScaleMultiplier = 2.0`.

Upstream raster max 5x -> effective fork raster max 10x.

Relevant commit:

- `4ffde695c5196c189749c5a370007e0f389e6c4d`

## Maximum-fidelity viewer — AUDIT IN PROGRESS

Detailed audit: `docs/VIEWER_RENDER_PIPELINE_AUDIT.md`.

### Important positive upstream findings

Aves already has a strong base:

- `ScaleBoundaries.originalScale = 1 / devicePixelRatio`;
- `ScaleState.originalSize` uses that scale;
- because raster content size is source-pixel-sized, this is the correct baseline for one source pixel per physical display pixel;
- tiled LOD selection is already DPR-aware;
- at physical 1:1 the tile sampler requests `sampleSize = 1`, so full source-resolution regions are available.

### Remaining strict 1:1 work

Upstream `originalSize` is not yet a strict pixel-inspection path because it still uses filtered rendering (`FilterQuality.high` at the exact 1:1 condition).

Planned strict Pixel Inspector mode:

- sample size 1 guaranteed;
- unfiltered/nearest sampling in explicit pixel-inspection mode;
- deterministic physical-pixel alignment/snapping;
- diagnostics proving source-pixel-to-panel-pixel ratio;
- validation for rotations/flips/fractional viewport geometry.

### Major fidelity bottleneck found: Android tiled decode

`RegionFetcher` currently requests:

- `Bitmap.Config.ARGB_8888`
- `ColorSpace.Named.SRGB`

for normal region decoding.

This can force large wide-gamut/high-precision images through an 8-bit sRGB choke point before Flutter receives them.

### Major fidelity bottleneck found: native raw-byte bridge

`BitmapUtils.getRawBytes()` currently connects normal bitmap output to sRGB and converts:

- RGBA_F16 -> ARGB_8888;
- RGBA_1010102 -> ARGB_8888;
- wide-gamut ARGB_8888 -> sRGB ARGB_8888.

This is incompatible with the project’s final maximum-fidelity Reference goal and is a P0 replacement target.

### Useful existing high-precision Dart transport

`InteropDecoding.rawBytesToDescriptor()` already understands:

- RGBA8888;
- RGBA1010102 (expanded to Flutter RGBA float32);
- RGBA float32.

This means the bridge can likely be upgraded without replacing Aves’ entire viewer architecture.

### Tile seam risk

Tiles are currently adjacent non-overlapping rectangles. Reconstruction filters may sample tile boundaries without neighboring source texels. Seam/gutter behavior needs an objective regression test and overlap/crop implementation if required.

## Ultra HDR — EXISTING HOOK FOUND, FINAL DESIGN NOT YET ACCEPTED

Aves already includes:

- `PlatformMediaFetchService.applyHdrGainmap` (false by default);
- `applyGainmap` passed through region requests;
- Android `GainmapUtils` that reads gain-map metadata and manually reconstructs gain onto base pixels;
- a special path that can output reconstructed values as Dart RGBA float32.

This is useful infrastructure but must be validated rather than assumed Reference-correct.

Audit still required for:

- base/gain-map color-space assumptions;
- gain-map spatial sampling/interpolation;
- display headroom behavior;
- Android native Ultra HDR presentation versus manual reconstruction;
- preservation of gain maps through the viewer/editor/export pipeline;
- correct HDR working representation.

Long-term edited Ultra HDR model remains:

`Ultra HDR source -> HDR working representation -> high-precision edits -> edited HDR + derived SDR rendition -> regenerated gain map -> JPEG/R (and later possible HEIC Ultra HDR)`

## Flutter live-preview capability — VERIFIED IN PINNED SDK

The fork pins Flutter submodule commit:

`7c7929adb0767c020659a422ae86df9ec0d5f82a`

That exact SDK contains:

- `ImageFilter.shader(FragmentShader)`;
- `ImageFilter.isShaderFilterSupported`;
- `FragmentProgram.fromAsset`;
- named uniform binding APIs.

`ImageFilter.shader` is Impeller-only and has documented shader-input requirements.

Planned use:

- pointwise Light/Color live preview after the color transport/working-space contract is fixed;
- wrap photo pixels only, not crop scrim/selection UI;
- bypass the filter entirely for untouched Reference mode.

Do not implement Structure/Tonal Contrast/local Ambiance as naive screen-space filters because radius/strength would vary with zoom. Those need source-space/multipass behavior.

## Initial editing feature set

Planned controls include:

- Exposure, Brightness, Contrast, Highlights, Shadows, Whites, Blacks;
- Temperature, Tint, Saturation, Vibrance;
- Ambiance-style local tonal balancing;
- Tonal Contrast-style high/mid/low local contrast with protections;
- Structure, Sharpening, multi-scale detail, denoise later;
- Skin Tone and Blue Tone selective adjustments;
- Pixel Adaptive-style color-volume expansion as an explicit enhancement/edit mode only;
- Curves, scopes, masks and analysis tools.

Snapseed/Google Photos behavior is reference/inspiration where proprietary math is unpublished. Functional equivalents are independently implemented and may be black-box calibrated.

## CI / test APK

Added `.github/workflows/editor-test-apk.yml` for feature branches and PRs.

Intended steps:

- Flutter packages;
- localization generation;
- static analysis;
- unit tests;
- Play-flavor profile APK build;
- APK artifact upload.

Current status: **workflow/run has not yet surfaced through the connected GitHub API, so analysis/tests/APK build are unverified. Do not claim CI is passing yet.**

## Draft PR

PR #1 remains draft while the rendering path and first real live controls are implemented.

Current PR purpose:

- continuity/fidelity/roadmap documentation;
- 10x zoom;
- durable edit recipe foundation;
- undo/redo and stack controller;
- editor autosave/reload;
- tests;
- CI/test APK workflow;
- ongoing maximum-fidelity viewer audit.

## Immediate next implementation order

1. redesign the Android region decode policy so it does not globally force ARGB8888+sRGB;
2. redesign native raw-pixel transport so wide gamut / >8-bit pixels survive to Flutter with an explicit color-space contract;
3. trace Flutter/Impeller surface color-space/bit-depth behavior and Android window color mode;
4. validate/improve Ultra HDR presentation using the existing gain-map hook;
5. implement strict physical 1:1 Pixel Inspector mode;
6. add rendering diagnostics for source format, decode format, tile sample size, DPR, scale and HDR state;
7. then wire first visible pointwise Light/Color adjustments through a high-precision live preview path;
8. then implement full-resolution export render/save path;
9. then advanced spatial operations and masks.

## Known uncertainties requiring measurement/testing

- exact Android codec behavior when requesting F16/wide-gamut region output across JPEG/PNG/HEIC/AVIF/TIFF/RAW paths;
- Flutter `ui.ImageDescriptor.raw(rgbaFloat32)` color-space interpretation and downstream surface transforms;
- Impeller render-target precision and gamut on target Android hardware;
- Android window/surface color-mode behavior for wide-gamut SDR and HDR in this Flutter version;
- correct Ultra HDR headroom control and gain-map preservation strategy;
- whether tile gutters are visibly/quantitatively required for all reconstruction modes;
- highest-fidelity export encoders without unintended conversions;
- format-specific handling for RAW, TIFF, HEIF/HEIC, AVIF and possible JPEG XL.

These must be established by source inspection and objective device tests, not guessed.
