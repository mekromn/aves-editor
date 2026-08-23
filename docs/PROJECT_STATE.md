# Aves Editor Fork — Project State

Last updated: 2026-08-23

## Repository

- Fork: `mekromn/aves-editor`
- Upstream: `deckerst/aves`
- Base/default development branch: `develop`
- Working branch: `feature/non-destructive-editor-foundation`
- Upstream base commit: `a17ee5575564266cdec747f1b1eca77989f3a689`
- Draft PR: **#1 — Editor foundation: fidelity roadmap, durable recipes, 10x zoom**
- PR remains draft while viewer fidelity, live rendering, and export foundations are still being validated.

## P0 rules

1. Maximum-fidelity viewer to the limits of the source, decoder, Android platform, GPU, and display hardware.
2. Full high-precision non-destructive editor integrated into Aves.
3. Reference mode contains no hidden beautification or creative processing.
4. HDR, Ultra HDR, wide gamut, source profiles, bit depth, and metadata are first-class.
5. Scientific inspection/comparison/validation tools are part of the product, not an afterthought.
6. Important project knowledge must be written to the repository for 101% continuity.

Hard pixel rule:

> Every pixel-changing feature must either improve fidelity/correctness or be explicitly identified as an edit/enhancement.

## Resume order

1. `docs/PROJECT_STATE.md`
2. `docs/CONTINUITY_PROTOCOL.md`
3. `docs/ROADMAP.md`
4. latest relevant `docs/chat/*.md`
5. `docs/MAXIMUM_FIDELITY_VIEWER.md`
6. `docs/VIEWER_RENDER_PIPELINE_AUDIT.md`
7. current branch / PR / CI history

## Implemented: zoom

The shared magnifier maximum is doubled centrally.

- upstream raster ceiling: 5x
- fork effective raster ceiling: 10x
- commit: `4ffde695c5196c189749c5a370007e0f389e6c4d`

This shared clamp applies consistently to viewer/editor magnification.

## Implemented: versioned non-destructive recipe foundation

Added a versioned, ordered edit model in `aves_model`:

- `EditSourceIdentity`;
- `EditRecipe`;
- ordered `EditOperation` stack;
- stable string operation types/IDs;
- JSON-compatible parameter payload;
- enabled state;
- opacity;
- optional mask reference;
- append/replace/remove/move;
- forward-compatible preservation of unknown future operation types.

Important commits:

- `2a9d93392ccd95e9c0bcef449d9a6b32f1365945` — initial recipe model;
- `b78cd90ccb77812438afbf9597a8d87cfa8ead0b` — export model;
- `a7180d8a7498b43a71477d698ca9660a8fc0c288` — move-index fix.

## Implemented: edit history controller

`EditRecipeController` supports:

- undo / redo;
- continuous-interaction grouping (one slider drag can be one undo step);
- enable / disable;
- opacity;
- mask assignment;
- duplicate;
- delete;
- reorder;
- reset-all;
- scalar parameter updates.

Initial commit: `1608f66c99ba74babf4ab6965babb235ff36174b`.

Analyzer-driven constructor cleanup: `aaa857319735efd4a6f31fcbfac0843fc4e9e061`.

## Implemented: durable edit persistence

Recipes use a dedicated SQLite database: `editor.db`.

This is intentionally independent from Aves `metadata.db`: gallery/index metadata is rebuildable, but edit recipes are user-created work and must survive metadata-cache resets.

Implemented behavior:

- recipe JSON persisted by source URI;
- source fingerprint includes URI, modification time, size, dimensions, MIME type;
- exact source match restores automatically;
- same URI with changed fingerprint is retained as stale but is not auto-applied;
- malformed/unknown stored data is not silently deleted;
- `ImageEditorPage` loads recipes and autosaves changes with debounce;
- final recipe is flushed when editor closes.

Key commits:

- `011dcfec0b4d6ce61f0d0dc183d9621444a78d0d` — recipe store;
- `ab21f014aaa4270bfbff5146405e31c802c57fae` — DB size handling;
- `b086f2c64e2fb405ed2cf825711be63e1d32d99f` — source identity from entry;
- `622d9374f9f79648dc95e0c571cd195e12e9de01` — editor autosave/reload;
- `7df5ac69e18cff2795bfc45a7e41b9182250af5b` — analyzer cleanup + controller call update.

## Implemented: recipe tests

`test/model/edit_recipe_test.dart` covers:

- JSON round trip;
- unknown future operation preservation;
- ordered stack movement;
- undo/redo;
- continuous-interaction grouping;
- duplicate operation IDs.

Initial test commit: `e6d0400ad1f4a79196c483573b371909ecb77866`.

Controller-constructor test update: `788b1e36780a10cf7c64bbd8906218ea5dfc59be`.

## Important UX implementation rule

Visible adjustment sliders have deliberately **not** been exposed yet.

A control that only saves a number but does not correctly alter pixels would be a fake milestone. The high-fidelity rendering/color contract is being established first; then visible controls will be connected to real live processing.

## Maximum-fidelity viewer audit: important upstream strengths

Aves already provides more correct machinery than initially assumed:

- `originalScale = 1 / devicePixelRatio`;
- `ScaleState.originalSize` uses that physical scale;
- raster content dimensions are source-pixel-sized;
- tile LOD selection is already DPR-aware;
- at physical 1:1, tile sample size resolves to 1, so full source-resolution region data is requested.

Therefore strict 1:1 work builds on the existing scale rather than replacing it.

Remaining Pixel Inspector work:

- explicit sample-size-1 guarantee;
- unfiltered/nearest rendering for an explicit inspection mode;
- deterministic physical-pixel alignment;
- diagnostics proving the source-pixel/panel-pixel ratio;
- validation through rotation/flip/fractional geometry.

## P0 fidelity bottleneck: normal Android region decode

`RegionFetcher` still requests:

- `Bitmap.Config.ARGB_8888`;
- `ColorSpace.Named.SRGB`.

That can force tiled wide-gamut/high-precision content through an 8-bit sRGB choke point before Flutter sees it.

Android behavior was checked: leaving the preferred color space unset can allow embedded/config-appropriate color handling. The forced sRGB request is therefore not an unavoidable platform constraint.

**This normal decoder request has not yet been changed.** It will be changed after the transport/build path is stable and target-device validation can measure the consequences.

## Implemented: first high-precision native-to-Dart transport fix

The upstream raw bridge also reduced normal F16/10-bit/wide-gamut bitmap output to ARGB8888+sRGB.

The fork now contains direct high-precision transport helpers and bridge selection:

- RGBA_F16 -> Dart RGBA float32 without an ARGB8888 intermediate;
- RGBA_1010102 -> Dart RGBA float32 without reducing 10-bit RGB to 8-bit;
- non-sRGB ARGB8888 -> extended-sRGB float coordinates rather than clipping into ordinary 8-bit sRGB;
- ordinary sRGB ARGB8888 remains on the existing compact path;
- existing gain-map reconstruction keeps its previous base-sRGB assumption until Ultra HDR math is validated separately.

Commits:

- `026d7a184b690d502815985c97e6f2f490b2b094` — high-precision conversion helpers;
- `a0b849add90f9c4c14b21fbeeb8b21b3baaf4be3` — high-precision/wide-gamut bridge selection.

This currently improves cases where Android already supplies better bitmap data. It does not yet remove the normal RegionFetcher ARGB8888+sRGB preference.

Known runtime concern: float32 transport costs 16 bytes/pixel; memory/fallback policy must be engineered and tested explicitly.

## Flutter raw color contract: verified

Pinned Flutter submodule:

`7c7929adb0767c020659a422ae86df9ec0d5f82a`

The exact engine implementation of raw `ImageDescriptor` creates the raw image with sRGB color-space semantics, and the public Dart raw descriptor factory does not accept an arbitrary source color-space tag.

Therefore raw Display-P3 channel coordinates cannot simply be forwarded unchanged. Current experimental transport converts source data into extended-sRGB coordinates and relies on float values outside ordinary [0,1] sRGB where necessary. This must be validated through Flutter/Impeller/output surface before becoming the final Reference transport contract.

## HDR / Ultra HDR: existing Aves scaffolding discovered

Aves already exposes window APIs for:

- wide-gamut support;
- HDR support;
- current wide/HDR mode;
- display HDR/SDR ratio;
- desired HDR headroom;
- dynamic window color mode.

Native Android `ActivityWindowHandler` already implements these operations.

Aves also already has:

- `PlatformMediaFetchService.applyHdrGainmap` (off by default);
- gain-map data passed through region requests;
- `GainmapUtils` manual gain-map reconstruction;
- float output for the existing gain-map reconstruction path;
- an HDR debug panel with independent wide-gamut/HDR/headroom/gain-map controls;
- commented-out `ViewerController` code intended to automatically switch HDR mode for `entry.isHdr` media.

Decision: **do not blindly enable automatic HDR yet.** An HDR window fed incorrect/flattened pixel data is not a fidelity improvement.

Ultra HDR still needs validation of:

- source/base color-space assumptions;
- gain-map spatial interpolation;
- display-headroom behavior;
- manual reconstruction versus Android-native Ultra HDR presentation;
- transform preservation;
- editing/regeneration/export behavior.

Long-term edited Ultra HDR model:

`Ultra HDR source -> HDR working representation -> high-precision edits -> edited HDR + derived SDR rendition -> regenerated gain map -> Ultra HDR output`

## Live editor shader capability: verified, not yet exposed

The pinned Flutter SDK supports Impeller `ImageFilter.shader(FragmentShader)` and runtime support detection.

Planned use:

- pointwise Light/Color preview after the color transport contract is validated;
- filter only photo pixels, never crop/selection UI;
- untouched Reference rendering bypasses the shader entirely.

Structure, Tonal Contrast, and true local Ambiance will **not** be faked as zoom-dependent screen-space effects; they require source-space/multipass design.

## CI / test APK workflow

Added `.github/workflows/editor-test-apk.yml`.

It performs:

1. dependency restore;
2. localization generation;
3. static analysis;
4. unit tests;
5. Play-flavor profile APK build;
6. APK artifact upload.

### First code-bearing run

- run: `32628418794`
- job: `97167306116`
- result: **failed at static analysis**
- tests/build/artifact were skipped because analysis failed.

The failure contained only two analyzer infos treated as fatal by the project workflow:

1. unnecessary `flutter/foundation.dart` import in `entry_editor_page.dart`;
2. `prefer_initializing_formals` in `EditRecipeController`.

Both were fixed immediately:

- `aaa857319735efd4a6f31fcbfac0843fc4e9e061` — initializing-formal cleanup;
- `7df5ac69e18cff2795bfc45a7e41b9182250af5b` — unnecessary import removal and editor call update;
- `788b1e36780a10cf7c64bbd8906218ea5dfc59be` — test call-site update.

A workflow run for the newest fix commit had not yet surfaced through the connected GitHub API at the time of this state update. **Do not claim the branch is green yet.**

## Documentation / continuity status

Established and maintained:

- `docs/CONTINUITY_PROTOCOL.md`;
- `docs/ROADMAP.md`;
- `docs/MAXIMUM_FIDELITY_VIEWER.md`;
- `docs/VIEWER_RENDER_PIPELINE_AUDIT.md`;
- chronological files under `docs/chat/` including this implementation session;
- this state snapshot.

All meaningful architecture choices, failures, commits, and test state from this session are now represented in-repo.

## Immediate next engineering order

1. verify the post-lint-fix CI run; fix any test/Kotlin/build failures immediately;
2. validate extended-sRGB float transport through Flutter/Impeller and the Android output surface;
3. replace the forced normal ARGB8888+sRGB region decode with a source/capability-aware policy;
4. characterize wide-gamut/HDR surface behavior using the existing debug harness on target hardware;
5. validate/rework Ultra HDR gain-map presentation before automatic HDR viewer switching;
6. implement strict physical 1:1 Pixel Inspector mode;
7. add rendering telemetry (source/decode config, tile sample, DPR, scale, window color mode, HDR/headroom, cache/timing);
8. expose first real pointwise Light/Color controls only when the live high-precision path is correct;
9. implement original-resolution render/export and ImageToolbox-inspired save destinations/profiles;
10. continue into spatial tone/detail, masks, scopes, RAW, batch, and automated regression testing per `docs/ROADMAP.md`.

## Known unresolved items requiring measurement, not guesses

- Android decoder behavior for F16/wide-gamut region output across formats/devices;
- whether extended-sRGB out-of-range float values survive raw Flutter image creation, Impeller, and the Android surface without premature clamp;
- actual Impeller render-target precision/gamut on target Android hardware;
- correct memory/fallback policy for float32 tile transport;
- final Android wide-gamut/HDR window behavior with Flutter compositing;
- correct Ultra HDR gain-map/headroom/color behavior;
- whether/how much tile gutters improve reconstruction seam behavior;
- highest-fidelity full-resolution export path for each supported format.
