# Project Chat Record — 2026-08-23 — Renderer fidelity implementation continuation

This record continues `2026-08-23-0811.md` and captures the work performed after the initial editor foundation and viewer audit.

## User directive governing this work

The project requirements established in this session remain P0:

- maximum-fidelity viewing to the limits of the hardware;
- full non-destructive editing;
- complete in-repository continuity, including material chat decisions;
- no hidden beautification in Reference mode;
- every pixel-changing feature either improves fidelity/correctness or is explicitly an edit/enhancement.

## Exact Flutter raw-image color contract discovered

The fork pins Flutter at submodule commit:

`7c7929adb0767c020659a422ae86df9ec0d5f82a`

Inspection of the exact Flutter engine implementation showed that `ImageDescriptor::initRaw()` creates raw images with an sRGB SkColorSpace and the Dart `ImageDescriptor.raw(...)` API does not expose an arbitrary color-space parameter.

Consequence:

- raw Display-P3 coordinates cannot be passed unchanged and treated as if they were correctly tagged;
- high-precision wide-gamut raw transport needs an agreed coordinate basis;
- the current experimental transport converts source colors into extended-sRGB coordinates and retains out-of-normal-range float values for validation through Flutter/Impeller.

## First high-precision native-to-Dart transport implementation

### Commit `026d7a184b690d502815985c97e6f2f490b2b094`

Updated:

`android/app/src/main/kotlin/deckers/thibault/aves/utils/BitmapConversion.kt`

Added direct high-precision conversion paths:

- Android `RGBA_F16` -> Dart RGBA float32 without quantizing through ARGB8888;
- Android `RGBA_1010102` -> Dart RGBA float32 without reducing 10-bit RGB to 8-bit first;
- revised ARGB8888 -> Dart float conversion support for a connector destination such as extended sRGB;
- retained legacy lossy converters for compatibility/fallback callers.

### Commit `a0b849add90f9c4c14b21fbeeb8b21b3baaf4be3`

Updated:

`android/app/src/main/kotlin/deckers/thibault/aves/utils/BitmapUtils.kt`

New raw-bridge policy when a higher-quality Android bitmap already reaches this layer:

- ordinary sRGB ARGB8888 stays on the compact existing 8-bit transport;
- non-sRGB ARGB8888 converts into extended-sRGB float coordinates rather than ordinary 8-bit sRGB;
- RGBA_F16 converts directly to Dart float32;
- RGBA_1010102 converts directly to Dart float32;
- the existing Ultra HDR gain-map reconstruction path intentionally retains its prior sRGB-base assumption until the gain-map/color-space math is separately validated.

Important limitation:

This does **not** yet change `RegionFetcher`'s normal decoder request. The normal tiled path still requests ARGB8888 + sRGB. The new transport therefore improves high-precision/fallback paths first and establishes the bridge needed before changing decoder policy globally.

Known runtime issue to address/test:

- float32 transport costs 16 bytes/pixel, so memory allocation/fallback policy must be explicitly engineered rather than assuming the old pre-allocation check is sufficient.

## Region-decode bottleneck remains P0

`RegionFetcher.kt` currently explicitly requests:

- `Bitmap.Config.ARGB_8888`;
- `ColorSpace.Named.SRGB`.

Android platform documentation confirms that leaving preferred color space unset allows the decoder to choose the embedded image space or one appropriate to the requested config. Therefore this forced narrowing is not an unavoidable Android limitation.

The decoder policy will be changed after the first transport layer compiles/builds and target-device behavior is measurable.

## Existing Aves HDR/window infrastructure discovered

`lib/services/window_service.dart` already exposes:

- `supportsWideGamut()`;
- `supportsHdr()`;
- `isInWideColorGamutMode()`;
- `isInHdrMode()`;
- `getDisplayHdrSdrRatio()`;
- `getDesiredHdrHeadroom()`;
- `setColorMode(wideColorGamut, hdr, desiredHdrHeadroom)`.

Native `ActivityWindowHandler.kt` already implements Android wide-gamut/HDR window modes, display HDR/SDR ratio, and desired HDR headroom support.

This means the fork does not need a parallel window-control framework.

## Upstream HDR integration is present but intentionally disabled

`ViewerController` contains commented-out `TODO [hdr] enable when ready` code that would switch the window into HDR for `entry.isHdr` media and reset the color mode on disposal.

`CatalogMetadata.isHdr` explicitly represents:

- embedded HDR gain map for images;
- HDR transfer for video.

Decision:

Do **not** simply uncomment automatic HDR yet. Enabling an HDR window while the image path is still flattened or color-misinterpreted would not satisfy Reference fidelity.

## Existing HDR debug harness discovered

`lib/widgets/debug/hdr.dart` already provides manual controls for:

- wide-gamut window mode;
- HDR window mode;
- display HDR/SDR ratio;
- desired HDR headroom;
- manual `PlatformMediaFetchService.applyHdrGainmap` toggle.

This is now the preferred initial target-device validation harness. It lets window mode and gain-map reconstruction be tested independently before automated normal-viewer behavior is enabled.

## Ultra HDR existing implementation status

Aves already has `GainmapUtils` and a manual gain-map reconstruction path. It reads gain-map ratio/gamma/epsilon/headroom metadata and can produce float output.

This is useful starting infrastructure, but it is not yet accepted as the final Reference Ultra HDR renderer.

Still to validate:

- source/base color-space assumptions;
- gain-map sampling/interpolation;
- display-headroom behavior;
- native Android Ultra HDR presentation versus manual reconstruction;
- preservation/regeneration rules during editing/export.

## Documentation updates

Updated/created during this continuation:

- `docs/VIEWER_RENDER_PIPELINE_AUDIT.md` — exact Flutter raw-color contract + transport patch + remaining decoder work;
- `docs/PROJECT_STATE.md` — current code, audit findings, HDR/window scaffolding, CI status, and immediate next order;
- `docs/README.md` — render-pipeline audit added to mandatory continuity index;
- this chat continuation record.

Relevant documentation commits include:

- `b9b6bd15310529024a11697146cfbb47ab0c7592` — transport/audit update;
- `bc91c83aa1b5ced38bbbf3e799e2cf071b08e264` — docs index update;
- `aa80a7a4622b52ffc3369dffa50ce9a1e2b437af` — project-state synchronization.

## CI state observed during implementation

GitHub Actions successfully started the new fork test workflow.

Observed Editor test APK run:

- run ID: `32628418794`;
- job ID: `97167306116`;
- job name: `Validate and build profile APK`.

At the last observation before this record was written:

- setup: success;
- Harden Runner: success;
- JDK setup: success;
- checkout: success;
- Flutter package restore: success;
- localization generation: success;
- static analysis: in progress;
- unit tests: pending;
- profile APK build: pending;
- artifact upload: pending.

No claim of CI success should be made until this or a newer code-bearing run finishes successfully.

## Immediate next engineering order

1. fix any analyzer/test/build failures from the current code-bearing workflow;
2. validate extended-sRGB float transport through Flutter/Impeller and the Android output surface;
3. replace the forced ARGB8888+sRGB region-decode policy with a source/capability-aware policy;
4. characterize wide-gamut/HDR window behavior using the existing HDR debug panel on target hardware;
5. validate/rework Ultra HDR gain-map presentation;
6. implement strict physical 1:1 Pixel Inspector mode;
7. add render diagnostics/telemetry;
8. expose first real Light/Color controls only after their live high-precision renderer is correct;
9. implement full-resolution export rendering/saving;
10. continue into spatial/detail/selective/mask tools.
