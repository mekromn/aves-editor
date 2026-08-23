# Viewer Render Pipeline Audit

Last updated: 2026-08-23

This document records source-level findings for the Aves raster viewer. It exists so maximum-fidelity work is based on the actual decode/render path instead of assumptions.

## Scope

Current path under audit:

`source media -> Android decoder/Bitmap -> platform byte bridge -> Dart ui.ImageDescriptor/ui.Codec -> RasterImageView/tiled regions -> Flutter/Impeller -> Android surface -> display`

The target is Reference-mode fidelity to the limits of the source, platform, GPU and display hardware.

---

# Findings

## 1. Upstream already has a physical-pixel-aware `originalSize` scale

`ScaleBoundaries.originalScale` is:

`1 / devicePixelRatio`

`AvesMagnifierController.getScaleForScaleState(ScaleState.originalSize)` uses that scale.

Because the raster content dimensions are expressed using the source image pixel dimensions, this is the correct baseline relationship for mapping one source image pixel to one physical display pixel.

### Consequence

We do **not** need to invent a second unrelated 1:1 scale calculation.

### Remaining work

Upstream `originalSize` is not yet a strict pixel-inspection rendering mode because filtering and alignment still need validation/control.

---

## 2. Upstream tiled LOD selection is already device-pixel-ratio aware

`RasterImageView` calculates sample sizes using both current magnifier scale and device pixel ratio.

`ExtraAvesEntryImages.sampleSizeForScale()` approximately chooses:

`highestPowerOf2(1 / (magnifierScale * devicePixelRatio))`

with a floor of 1.

At physical 1:1 (`magnifierScale = 1 / DPR`), this resolves to `sampleSize = 1`, so full source-resolution tile data is requested.

### Consequence

The roadmap item is refined from “make LOD DPR-aware” to:

- validate the existing DPR-aware LOD rigorously;
- remove fidelity losses after tile decode;
- ensure high-quality source tiles always supersede lower-resolution layers;
- improve boundary behavior and diagnostics.

---

## 3. Physical 1:1 currently still uses filtered sampling

At `originalSize`, `renderingScale = magnifierScale * DPR * sampleSize = 1` for a sample-size-1 tile.

Current `_qualityForScale()` selects `FilterQuality.high` at that point.

### Consequence

The source data is full resolution, but this is not a strict unfiltered pixel-inspection path.

### Planned strict Pixel Inspector mode

- force sample size 1;
- use nearest/unfiltered sampling (`FilterQuality.none` or a lower-level equivalent) only in explicit pixel-inspection mode;
- align/snap image translation so source pixel boundaries map deterministically to physical display pixel boundaries;
- expose source-pixel-to-panel-pixel ratio in diagnostics;
- test rotated/flipped images and fractional viewport dimensions.

Normal Reference viewing does not automatically need nearest-neighbor rendering; this strict mode is for exact inspection.

---

## 4. Tile boundaries currently have no sampling gutter

Visible region tiles are requested as adjacent, non-overlapping source rectangles and are independently filtered when rendered.

### Risk

Reconstruction filters can sample at/near tile edges where neighboring source texels are not present, which can create seams or subtly different edge reconstruction depending on backend/filter behavior.

### Planned work

Evaluate and, where required, implement source-region gutters/overlap with display cropping so filtering sees valid neighboring texels without double-rendering them.

This must be tested rather than assumed to be visible on every backend.

---

# Major fidelity bottleneck: Android region decode

## 5. `RegionFetcher` explicitly requests 8-bit sRGB

The Android tiled-region decoder currently sets:

- `inPreferredConfig = Bitmap.Config.ARGB_8888`
- `inPreferredColorSpace = ColorSpace.Named.SRGB`

before `BitmapRegionDecoder.decodeRegion()`.

It retries without forced values only if that decode fails.

### Consequence

For the normal successful tile path, wide-gamut and higher-precision source content can be forced through an 8-bit sRGB decode target **before Flutter receives the pixels**.

This is a real Reference-mode fidelity bottleneck and a P0 target for this fork.

### Android platform semantics verified

Current Android documentation says that when `inPreferredColorSpace` is null, the decoder chooses the embedded image color space or one appropriate for the requested config; examples include sRGB for ARGB8888 and EXTENDED_SRGB for RGBA_F16.

Therefore the current forced sRGB request is an intentional upstream narrowing, not an unavoidable Android behavior.

### Required replacement behavior

The decoder policy must become source/capability aware instead of globally requesting ARGB8888+sRGB.

Potential outputs to evaluate include:

- preserving a suitable source/wide color space;
- RGBA_F16 where Android decoder/platform support allows;
- RGBA_1010102 when appropriate;
- a controlled high-precision working representation for editing/HDR paths;
- fallback to ARGB_8888 only when the source/platform requires it.

The forced decoder preference itself has **not yet been changed**. That change will follow analyzer/build and target-device testing of the transport layer first.

---

# Native raw-byte bridge

## 6. Upstream bridge also had an 8-bit/sRGB bottleneck

Before this fork's first transport patch, `BitmapUtils.getRawBytes()` connected normal bitmap output to `ColorSpace.Named.SRGB` and converted:

- ARGB_8888 source -> ARGB_8888 sRGB;
- RGBA_F16 source -> ARGB_8888 sRGB;
- RGBA_1010102 source -> ARGB_8888 sRGB.

So even a higher-precision fallback bitmap could be reduced before Dart received it.

## 7. First high-precision transport patch — IMPLEMENTED, NOT YET DEVICE-VALIDATED

Commits:

- `026d7a184b690d502815985c97e6f2f490b2b094` — add direct F16/10-bit -> Dart float32 conversion helpers;
- `a0b849add90f9c4c14b21fbeeb8b21b3baaf4be3` — use high-precision float transport for non-8-bit/wide-gamut bitmap output.

New behavior when such a bitmap reaches `BitmapUtils.getRawBytes()`:

- RGBA_F16 is converted directly to Dart RGBA float32 instead of quantizing through ARGB8888;
- RGBA_1010102 is converted directly to Dart RGBA float32 instead of losing 10-bit precision;
- non-sRGB ARGB8888 can be transformed into extended-sRGB float coordinates instead of being clipped/quantized into ordinary 8-bit sRGB;
- ordinary sRGB ARGB8888 remains on the compact existing 8-bit path;
- the existing gain-map path deliberately retains its previous sRGB-base assumption for now, because Ultra HDR color-space math is being audited separately.

This patch improves precision **when the decoder already supplies better data**. It does not yet fix the normal region path because `RegionFetcher` still requests ARGB8888+sRGB.

### Why extended-sRGB coordinates

The pinned Flutter raw-image API does not accept an arbitrary source color-space tag. Therefore P3/other wide-gamut numeric coordinates cannot simply be passed unchanged and labeled as raw sRGB.

The bridge converts wide-gamut data into the sRGB-primary coordinate system while retaining out-of-[0,1] floating-point components. This is the intended transport representation to validate before changing the decoder policy.

### Validation still required

- Kotlin/Flutter analyzer/build;
- Pixel 9 Pro XL rendering of known P3 patches;
- out-of-sRGB values surviving `ui.ImageDescriptor` and Impeller without premature clamp;
- memory behavior of float32 tile transport;
- comparison against full-image encoded/profile-aware decode.

Do not yet claim this patch is reference-validated.

---

# Flutter raw-image color contract

## 8. Dart bridge already supports high precision, but raw images are tagged sRGB

`InteropDecoding.rawBytesToDescriptor()` recognizes custom pixel format codes for:

- RGBA8888;
- RGBA1010102;
- RGBA float32.

RGBA1010102 is unpacked to `ui.PixelFormat.rgbaFloat32` for Flutter.

The exact pinned Flutter engine source for `ImageDescriptor::initRaw()` sets:

`color_space = SkColorSpace::MakeSRGB()`

for raw descriptors regardless of raw pixel format.

The Dart API's `ImageDescriptor.raw(...)` factory exposes no color-space argument.

### Consequence

Raw Display-P3 coordinates cannot be handed across unchanged; the engine would interpret the channel basis as sRGB.

Float transport is still useful because values outside normal sRGB can be represented in the sRGB coordinate basis. This is what the first native bridge patch is designed to preserve and now needs objective validation.

### Important distinction

Encoded image decode is different: Flutter's image generator can retain color-space information from encoded/profiled content. The raw tiled bridge is where this explicit transport-space problem exists.

---

# Ultra HDR findings

## 9. Aves already has an Ultra HDR gain-map hook

`PlatformMediaFetchService` contains a static `applyHdrGainmap` flag (currently false by default).

Region requests pass `applyGainmap` to Android `RegionFetcher`, which passes it to `BitmapUtils.getBytes()`.

On Android 14+ when a bitmap has a gain map, `BitmapUtils` can obtain a gain-map pixel transformer and promote the result to Dart RGBA float32.

### Consequence

Ultra HDR support is not starting from zero. There is existing gain-map reconstruction logic that can be studied and improved.

---

## 10. Current gain-map implementation is a manual reconstruction path

`GainmapUtils.getGainmapPixelTransformer()` reads:

- ratioMin;
- ratioMax;
- gamma;
- epsilonSdr;
- epsilonHdr;
- minimum display ratio for HDR transition;
- display ratio for full HDR;
- the gain-map bitmap itself.

It then computes a per-pixel gain and applies it to the base pixel.

### Important audit point

The current upstream implementation is not yet accepted as the final Reference Ultra HDR path merely because it exists.

It must be validated for:

- color-space assumptions;
- base/gain-map coordinate mapping and interpolation;
- display-headroom behavior;
- gain-map sampling quality;
- interaction with Android's native Ultra HDR presentation behavior;
- output transfer function/working representation;
- whether manual reconstruction is preferable to preserving the gain-map object deeper into the display pipeline.

---

# Flutter runtime-shader findings

## 11. Exact bundled Flutter supports `ImageFilter.shader`

The fork pins Flutter through the `.flutter` submodule at commit:

`7c7929adb0767c020659a422ae86df9ec0d5f82a`

At that exact SDK commit:

- `ImageFilter.shader(FragmentShader)` exists;
- `ImageFilter.isShaderFilterSupported` exists;
- it is Impeller-only;
- the first float uniform must be a vec2 used by the engine for input texture size;
- at least one sampler2D is required and the first sampler is bound to filter input;
- GLES Impeller requires Y-axis handling in custom filter shaders.

### Planned use

This is a viable path for live **pointwise** editor preview operations after color-pipeline assumptions are made explicit.

The image filter must wrap only the photo pixels, not crop scrims/selection UI.

### Critical limitations

- zero/identity recipe in Reference mode should bypass the image filter entirely so an offscreen shader pass cannot silently alter untouched pixels;
- screen-space shader filters are not sufficient for scale-independent Structure, Tonal Contrast or true local Ambiance unless source-space sampling/multipass behavior is explicitly designed;
- pointwise math must use the agreed transport/working-space contract rather than assuming arbitrary source coordinates are sRGB.

---

# Priority fixes resulting from this audit

1. **In progress:** preserve high precision in the native raw bridge — first patch landed; CI/device validation pending.
2. Replace the forced ARGB8888+sRGB region decode policy with a source/capability-aware fidelity policy.
3. Validate extended-sRGB float transport through Flutter/Impeller and the Android output surface.
4. Trace/activate appropriate Android wide-color/HDR window/surface modes.
5. Build strict 1:1 Pixel Inspector mode on top of the existing correct physical-scale baseline.
6. Add seam/gutter tests and fix tile boundary reconstruction where required.
7. Validate and redesign Ultra HDR display/reconstruction based on measured behavior and Android native capabilities.
8. Only then make pointwise editor controls visible through the live shader path.

---

# Acceptance rule

A decode/render change is not accepted because it looks more vivid or sharper.

Reference-path changes require evidence that they preserve or more correctly reproduce source information. Creative appearance changes belong in explicit enhancement/edit modes.
