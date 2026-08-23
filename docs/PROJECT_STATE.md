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
- `MediaEditService` and an existing Save Copy UI entry point whose upstream editor handler is unfinished;
- native window APIs for wide-gamut/HDR support, HDR/SDR ratio, desired HDR headroom and dynamic window color mode;
- an HDR debug panel that can independently toggle wide gamut, HDR mode and manual gain-map application.

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

## Maximum-fidelity viewer — AUDIT + FIRST TRANSPORT FIX IN PROGRESS

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

Android documentation confirms that a null preferred color space can let the decoder choose the embedded image color space or a config-appropriate space, so the forced sRGB path is not an unavoidable platform limitation.

**The forced region-decode preference has not yet been changed.** It will be changed only after transport/build/device validation.

### Native raw bridge — FIRST HIGH-PRECISION FIX IMPLEMENTED

Upstream `BitmapUtils.getRawBytes()` also quantized normal F16/1010102/wide-gamut output through ARGB8888+sRGB.

The fork now adds direct high-precision transport:

- RGBA_F16 -> Dart RGBA float32 without ARGB8888 quantization;
- RGBA_1010102 -> Dart RGBA float32 without losing 10-bit RGB precision;
- non-sRGB ARGB8888 -> extended-sRGB float coordinates so wide-gamut values can remain outside normal [0,1] sRGB instead of being clipped to ordinary 8-bit sRGB;
- ordinary sRGB ARGB8888 remains on the compact 8-bit path;
- gain-map reconstruction deliberately retains its previous base-sRGB assumption until the Ultra HDR math is separately validated.

Relevant commits:

- `026d7a184b690d502815985c97e6f2f490b2b094` — direct high-precision conversion helpers;
- `a0b849add90f9c4c14b21fbeeb8b21b3baaf4be3` — use float transport for high-precision/wide-gamut bitmap output.

This improves fidelity **when the decoder already supplies better data**. It does not yet remove `RegionFetcher`'s normal ARGB8888+sRGB request.

### Flutter raw color-space constraint verified

The exact pinned Flutter engine implementation of `ImageDescriptor::initRaw()` tags raw descriptors with `SkColorSpace::MakeSRGB()` and the public Dart `ImageDescriptor.raw(...)` factory has no arbitrary color-space parameter.

Therefore raw P3 coordinates cannot simply be transported unchanged. The current experimental bridge converts wide-gamut data into high-precision extended-sRGB coordinates before Dart and must now be validated for out-of-gamut preservation through Impeller and the output surface.

### Tile seam risk

Tiles are currently adjacent non-overlapping rectangles. Reconstruction filters may sample tile boundaries without neighboring source texels. Seam/gutter behavior needs an objective regression test and overlap/crop implementation if required.

## Ultra HDR — EXISTING HOOK FOUND, FINAL DESIGN NOT YET ACCEPTED

Aves already includes:

- `PlatformMediaFetchService.applyHdrGainmap` (false by default);
- `applyGainmap` passed through region requests;
- Android `GainmapUtils` that reads gain-map metadata and manually reconstructs gain onto base pixels;
- a special path that can output reconstructed values as Dart RGBA float32;
- dynamic window HDR/wide-gamut APIs and an HDR debug panel;
- commented-out viewer-controller code intended to switch the window to HDR for `entry.isHdr` content.

This is useful infrastructure but must be validated rather than assumed Reference-correct.

Audit still required for:

- base/gain-map color-space assumptions;
- gain-map spatial sampling/interpolation;
- display headroom behavior;
- Android native Ultra HDR presentation versus manual reconstruction;
- preservation of gain maps through the viewer/editor/export pipeline;
- correct HDR working representation.

Automatic viewer HDR switching remains intentionally disabled until the pixel/gain-map path is correct; switching an HDR window on while still feeding SDR-flattened pixels would not satisfy Reference fidelity.

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

## CI / test APK — RUNNING

Added `.github/workflows/editor-test-apk.yml` for feature branches and PRs.

Intended steps:

- Flutter packages;
- localization generation;
- static analysis;
- unit tests;
- Play-flavor profile APK build;
- APK artifact upload.

Code-bearing workflow run observed:

- Editor test APK run `32628418794`
- job `97167306116` — **Validate and build profile APK**

Last observed state in this session:

- setup/JDK/checkout/pub get/gen-l10n: passed;
- static analysis: **in progress**;
- unit tests/build/artifact upload: pending.

Do not claim CI is passing until the run concludes successfully.

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
- maximum-fidelity viewer audit;
- first high-precision native-to-Dart pixel transport fix.

## Immediate next implementation order

1. let the current analyzer/test/APK workflow validate the editor + transport code and fix any failures;
2. validate extended-sRGB float transport through Flutter/Impeller and the Android output surface;
3. redesign the Android region decode policy so it does not globally force ARGB8888+sRGB;
4. use the existing HDR debug controls to characterize wide-gamut/HDR surface behavior on target hardware;
5. validate/improve Ultra HDR presentation using the existing gain-map hook before enabling automatic viewer HDR mode;
6. implement strict physical 1:1 Pixel Inspector mode;
7. add rendering diagnostics for source format, decode format, tile sample size, DPR, scale, window color mode and HDR state;
8. then wire first visible pointwise Light/Color adjustments through a high-precision live preview path;
9. then implement full-resolution export render/save path;
10. then advanced spatial operations and masks.

## Known uncertainties requiring measurement/testing

- exact Android codec behavior when requesting F16/wide-gamut region output across JPEG/PNG/HEIC/AVIF/TIFF/RAW paths;
- out-of-normal-sRGB float values surviving raw `ui.ImageDescriptor` and Impeller without premature clamp;
- Impeller render-target precision and gamut on target Android hardware;
- Android window/surface color-mode interaction with Flutter compositing in the pinned engine;
- correct Ultra HDR headroom control and gain-map preservation strategy;
- whether tile gutters are visibly/quantitatively required for all reconstruction modes;
- memory/performance cost of float32 tile transport and the correct explicit fallback policy;
- highest-fidelity export encoders without unintended conversions;
- format-specific handling for RAW, TIFF, HEIF/HEIC, AVIF and possible JPEG XL.

These must be established by source inspection and objective device tests, not guessed.
