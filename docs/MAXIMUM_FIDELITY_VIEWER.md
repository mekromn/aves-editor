# Maximum Fidelity Viewer

Maximum-fidelity viewing is a P0 architectural requirement for this fork.

The default viewer path must reproduce the source as faithfully as the device hardware, Android platform, decoder, GPU, and display allow. Quality must never be intentionally reduced for convenience when a higher-fidelity path is available. Performance fallbacks may exist, but they must be explicit and must never silently become the reference path.

## Non-negotiable invariants

1. **No hidden enhancement in Reference mode**
   - No automatic saturation boost.
   - No hidden sharpening.
   - No contrast enhancement.
   - No creative tone curve.
   - No local-contrast enhancement.
   - No gamut expansion.
   - No Pixel-Adaptive-style color-volume expansion.
   - Enhancements belong in explicitly named viewing/editing modes only.

2. **Preserve source color information**
   - Detect and preserve source color space/profile information whenever the platform exposes it.
   - Avoid unnecessary conversion through sRGB.
   - Preserve wide-gamut content on capable displays.
   - Use explicit color transforms when a transform is required.

3. **HDR and Ultra HDR are first-class**
   - Detect HDR and Ultra HDR content.
   - Preserve gain maps and gain-map metadata.
   - Use an HDR-capable display path when supported by the device.
   - Never flatten Ultra HDR to SDR merely to fit the normal viewer pipeline.
   - SDR fallback must remain visually correct on non-HDR paths.

4. **High-precision processing**
   - Editing and analytical operations should use linear-light high-precision intermediates.
   - Prefer FP16 GPU intermediates where supported and useful.
   - Avoid repeated 8-bit quantization between operations.
   - Quantize only when required by the final display/output target.

5. **Source-resolution viewing**
   - Provide a true 1:1 source-pixel inspection mode.
   - LOD/tile selection must be based on source pixels versus physical display pixels, not only logical Flutter coordinates.
   - At high zoom, load the source detail required for the visible region instead of magnifying an unnecessarily low-resolution preview.

6. **High-quality resampling**
   - Choose reconstruction/downsampling based on scale ratio.
   - Avoid assuming one filtering mode is optimal at every scale.
   - Prevent tile-boundary seams by providing sufficient sampling support/overlap at tile edges.
   - Preserve fine detail without artificial ringing or oversharpening.

7. **No unnecessary destructive cycles**
   - Viewing must never re-encode source media.
   - Non-destructive editing stores edit instructions separately from source pixels.
   - Export should decode once, process at high precision, perform the required final color transform, and encode once whenever practical.

8. **Hardware-aware, not hardware-limited by defaults**
   - Respect GPU maximum texture size and memory limits dynamically.
   - Use tiled rendering where the source exceeds practical texture limits.
   - Exploit capable Android/Impeller/GPU paths when present.
   - Fall back gracefully rather than globally selecting the lowest common denominator.

9. **Zoom fidelity**
   - Raster-image maximum pinch zoom target: 10x (double current upstream 5x limit).
   - Editor maximum pinch zoom target: double the current editor maximum.
   - High zoom must remain a source-detail inspection tool, not merely enlarged cached pixels.

10. **Reference and Enhanced modes are separate**
    - `Reference` = source-faithful rendering.
    - Optional future modes may include Natural, Adaptive/Enhanced, SDR reference, wide-gamut reference, and HDR-expanded views.
    - Switching modes must be explicit and reversible.

## Required viewer pipeline

Conceptually:

```text
source media
   ↓
decode with source metadata/profile/gain map preserved
   ↓
source-aware / physical-pixel-aware LOD selection
   ↓
high-quality tiled or full-resolution raster path
   ↓
explicit color-management / HDR presentation transform
   ↓
GPU/display surface with the highest useful precision supported
   ↓
device panel
```

For non-destructive edits:

```text
source media
   ↓
decode
   ↓
linear high-precision working representation
   ↓
non-destructive edit graph
   ↓
display transform / HDR presentation
   ↓
viewer
```

The original file remains untouched until an explicit export/overwrite action.

## Immediate implementation priorities

P0:
- Double raster max zoom from 5x to 10x.
- Double editor max zoom.
- Audit current Flutter/Android decode -> texture -> surface color path.
- Audit tiled image LOD selection against physical pixels/device pixel ratio.
- Verify wide-gamut preservation end to end.
- Add Ultra HDR detection and correct display path.
- Add true 1:1 source-pixel inspection.

P1:
- Improve tile-edge sampling/overlap.
- Improve adaptive downsample/upscale reconstruction.
- Add explicit Reference vs Enhanced display modes.
- Add clipping/gamut/scopes overlays that do not alter the underlying image rendering.

P2:
- Integrate the same high-precision reference path with the non-destructive editor.
- HDR/Ultra-HDR-aware edit graph and export.
- Gain-map regeneration for edited Ultra HDR output.

## Acceptance rule

A new viewer/editor feature is not considered complete if it makes an untouched source image less faithful than the best path the target hardware can support.
