# Aves Editor Fork — Product & Engineering Roadmap

This roadmap is normative for the fork. It turns the project goals into ordered engineering work rather than a loose feature wishlist.

The project has three defining pillars:

1. **Maximum-fidelity reference viewing to the limits of the hardware.**
2. **High-precision, fully non-destructive image editing.**
3. **Scientific inspection, comparison, and validation tools.**

A fourth P0 pillar applies to every phase: **complete in-repository continuity and documentation.**

---

## P0 invariants

- Reference mode contains no hidden beautification or creative processing.
- Every pixel-changing feature must either improve fidelity/correctness or be explicitly identified as an edit/enhancement.
- Source files remain untouched by normal editing; edits are stored as versioned recipes and rendered explicitly on export.
- HDR, Ultra HDR, wide gamut, ICC/profile information, bit depth, metadata, and source geometry are preserved whenever the platform/format allows.
- Avoid unnecessary decode/encode cycles, unnecessary sRGB conversion, and repeated low-bit-depth quantization.
- High-precision/linear-light processing is used where mathematically appropriate.
- Performance fallbacks may exist, but may not silently replace the reference-quality path.
- Every important requirement, experiment, failure, decision, test, commit, and rationale is documented.

---

# Phase 0 — Fork, continuity, testability

## 0.1 Repository continuity

- [x] Fork upstream Aves `develop`.
- [x] Create `feature/non-destructive-editor-foundation`.
- [x] Establish `docs/CONTINUITY_PROTOCOL.md`.
- [x] Establish `docs/PROJECT_STATE.md`.
- [x] Establish chronological chat records under `docs/chat/`.
- [ ] Keep project-state and chat records updated during every meaningful work session.

## 0.2 CI / installable test builds

- [ ] Add a fork-friendly GitHub Actions debug/profile APK workflow.
- [ ] Upload APK artifacts for every editor feature-branch build.
- [ ] Run Flutter analysis/tests on every PR.
- [ ] Record workflow run IDs and artifact names in project state when used for device testing.

## 0.3 Permanent image test corpus

Create/maintain controlled samples for:

- [ ] sRGB JPEG
- [ ] Display P3 JPEG
- [ ] PNG
- [ ] 8/10/16-bit images where supported
- [ ] TIFF
- [ ] HEIC/HEIF
- [ ] AVIF
- [ ] DNG/RAW
- [ ] Ultra HDR JPEG/R
- [ ] HDR/PQ/HLG samples as supported
- [ ] smooth gradients / banding tests
- [ ] shadow ramps
- [ ] highlight ramps
- [ ] saturated red/green/blue/cyan tests
- [ ] skin tones
- [ ] foliage
- [ ] skies
- [ ] very large panoramas / tiled-decode stress images

---

# Phase 1 — Maximum-fidelity viewer foundation

See `MAXIMUM_FIDELITY_VIEWER.md` for the detailed invariants.

## 1.1 Zoom and source inspection

- [x] Double upstream pinch-to-zoom ceiling (5x raster -> 10x effective ceiling).
- [ ] Add true 1:1 source-pixel inspection mode.
- [ ] Define 1:1 in physical display pixels, not only Flutter logical pixels.
- [ ] Keep high-zoom regions backed by sufficient source-resolution tile data.
- [ ] Add optional nearest-neighbor pixel-inspection rendering at extreme zoom.

## 1.2 Decode / tile / LOD quality

- [ ] Audit source decode -> image provider -> tile decode -> Flutter texture -> Impeller -> Android surface -> panel.
- [ ] Document every bit-depth/color-space conversion in the path.
- [ ] Select tile LOD using source pixels versus physical display pixels.
- [ ] Prevent low-resolution previews from remaining after higher-quality data is available.
- [ ] Add seam-safe tile overlap/cropping where filtering can sample tile boundaries.
- [ ] Improve progressive thumbnail -> screen-resolution preview -> full-resolution tile replacement.

## 1.3 Resampling

- [ ] Make resampling scale-ratio-aware.
- [ ] Use high-quality downsampling/prefiltering for large reductions.
- [ ] Use appropriate reconstruction near 1:1 and during enlargement.
- [ ] Avoid globally forcing one Flutter `FilterQuality` value and calling that “maximum quality.”
- [ ] Add optional Sharp / Smooth / Pixel inspection rendering choices without changing Reference semantics.

## 1.4 Color management

- [ ] Detect source color profile/color space whenever available.
- [ ] Avoid unnecessary sRGB round trips.
- [ ] Preserve Display P3/wide gamut on capable displays.
- [ ] Add explicit display transforms where required.
- [ ] Surface source profile, transfer function, gamut, and bit depth in viewer information.
- [ ] Add SDR-reference and soft-proof paths later.

## 1.5 HDR / Ultra HDR display

- [ ] Detect Ultra HDR gain maps and metadata.
- [ ] Detect HDR transfer functions/content state.
- [ ] Activate the appropriate HDR-capable Android display path dynamically.
- [ ] Preserve SDR fallback behavior.
- [ ] Display current HDR headroom / gain-map status in diagnostics.
- [ ] Never flatten Ultra HDR merely to fit the normal SDR viewer path.

---

# Phase 2 — Non-destructive editor core

## 2.1 Versioned edit recipe

- [ ] Ordered edit-operation stack.
- [ ] Stable operation IDs.
- [ ] Operation type + parameter payload.
- [ ] enabled/disabled state.
- [ ] per-operation opacity.
- [ ] optional mask reference.
- [ ] recipe schema version.
- [ ] source fingerprint/identity to detect source replacement/modification.
- [ ] forward-compatible handling of unknown future operation types.

## 2.2 History

- [ ] Undo.
- [ ] Redo.
- [ ] Undo grouping for continuous slider drags.
- [ ] Crash-safe persistence of current recipe.
- [ ] Restore unfinished edits after process death/reboot.

## 2.3 Stack manipulation

- [ ] Reorder operations.
- [ ] enable/disable.
- [ ] duplicate.
- [ ] delete.
- [ ] reset one operation.
- [ ] per-operation opacity.
- [ ] copy/paste all edits.
- [ ] copy/paste selected operations.
- [ ] save stack as preset.

## 2.4 Comparison

- [ ] Touch/hold original.
- [ ] One-tap global bypass.
- [ ] Split before/after divider.
- [ ] Swipe divider.
- [ ] Flicker comparison.
- [ ] Two-image side-by-side comparison with synchronized pan/zoom.

---

# Phase 3 — High-precision rendering engine

## 3.1 Working representation

- [ ] Decode source once where practical.
- [ ] Explicit transfer-function decode.
- [ ] Linear-light processing where mathematically appropriate.
- [ ] FP16 GPU intermediates where supported/useful.
- [ ] FP32 selectively where precision/range warrants it.
- [ ] Avoid repeated 8-bit intermediate quantization.
- [ ] Apply final display/output transform once.
- [ ] Dither when reducing precision where beneficial.

## 3.2 Baseline Light controls

- [ ] Exposure
- [ ] Brightness
- [ ] Contrast
- [ ] Highlights
- [ ] Shadows
- [ ] Whites
- [ ] Blacks

## 3.3 Baseline Color controls

- [ ] Temperature
- [ ] Tint
- [ ] Saturation
- [ ] Vibrance

White balance should use a defined chromatic-adaptation model instead of arbitrary RGB multipliers.

---

# Phase 4 — Advanced tone, detail, and selective color

## 4.1 Ambiance-style control

Independent implementation combining appropriately constrained:

- local exposure balancing;
- highlight compression;
- shadow expansion;
- adaptive local contrast;
- restrained chroma compensation.

## 4.2 Tonal Contrast-style control

- [ ] high tones
- [ ] mid tones
- [ ] low tones
- [ ] highlight protection
- [ ] shadow protection
- [ ] multiscale/edge-aware decomposition
- [ ] halo suppression

## 4.3 Detail

- [ ] Structure
- [ ] Sharpening
- [ ] fine detail
- [ ] medium detail
- [ ] coarse detail
- [ ] halo-resistant edge handling
- [ ] chroma-noise-aware behavior
- [ ] denoise later
- [ ] deconvolution-style sharpening investigation later

## 4.4 Skin Tone

Perceptual selective adjustment using soft hue/chroma/luma gates, preferably in a well-defined perceptual space.

Planned controls:

- [ ] amount
- [ ] hue
- [ ] chroma
- [ ] warmth
- [ ] luminance
- [ ] mask preview
- [ ] gamut protection

## 4.5 Blue Tone

Selective cyan/azure/blue processing with:

- [ ] amount
- [ ] hue
- [ ] luminance
- [ ] aqua <-> blue balance
- [ ] sky protection
- [ ] clipping/gamut protection
- [ ] mask preview

## 4.6 Pixel Adaptive-style color-volume mode

Explicit enhancement/edit mode only; never part of Reference mode.

- luminance-preserving;
- hue-aware;
- neutral-preserving;
- skin-protected;
- gamut-boundary-aware chroma/color-volume expansion;
- graceful gamut compression rather than hard clipping.

---

# Phase 5 — Masks

Every compatible operation should eventually support masking.

- [ ] brush
- [ ] erase/refine brush
- [ ] linear gradient
- [ ] radial gradient
- [ ] luminance range
- [ ] hue/color range
- [ ] skin range
- [ ] sky/blue range
- [ ] subject/background later
- [ ] feather
- [ ] invert
- [ ] mask opacity
- [ ] mask overlay preview

---

# Phase 6 — Scientific scopes and inspection

## 6.1 Scopes

- [ ] luminance histogram
- [ ] RGB histogram
- [ ] waveform
- [ ] RGB parade
- [ ] vectorscope

## 6.2 Warnings / overlays

- [ ] highlight clipping
- [ ] shadow clipping
- [ ] gamut clipping
- [ ] false color
- [ ] HDR nit-level overlay

## 6.3 Pixel inspector

At selected coordinates expose where available:

- [ ] source encoded RGB
- [ ] decoded/linear RGB
- [ ] luminance
- [ ] OKLab / OKLCH
- [ ] source coordinates
- [ ] displayed coordinates
- [ ] HDR/gain-map contribution
- [ ] gamut/clipping state

## 6.4 Calibration/test mode

- [ ] gray ramp
- [ ] gradient/banding test
- [ ] black-level ramp
- [ ] highlight/HDR ramp
- [ ] gamut sweep
- [ ] color-patch targets
- [ ] display clipping tests
- [ ] device-specific diagnostics where useful

---

# Phase 7 — Ultra HDR editing and export

## 7.1 Spatial transforms

- [ ] Preserve gain maps through crop/rotate/scale where Android APIs support it.

## 7.2 HDR-aware edits

Arbitrary tone/color edits must not blindly reuse a now-invalid original gain map.

Long-term model:

`Ultra HDR source -> HDR working representation -> high-precision edit graph -> edited HDR + derived SDR rendition -> regenerated gain map -> Ultra HDR output`

## 7.3 Export

- [ ] JPEG/R Ultra HDR
- [ ] preserve appropriate gain-map metadata
- [ ] validate SDR fallback rendition
- [ ] validate HDR rendition under varying headroom
- [ ] investigate HEIC Ultra HDR on Android 16/API 36

---

# Phase 8 — RAW / DNG

- [ ] DNG/RAW metadata ingest
- [ ] demosaic strategy
- [ ] camera white balance
- [ ] camera color matrix -> working space
- [ ] highlight recovery
- [ ] pre-sharpen noise reduction
- [ ] lens corrections
- [ ] chromatic aberration correction where data permits
- [ ] feed result into the same non-destructive edit stack

---

# Phase 9 — Professional export

Use Aves native media/storage infrastructure plus an ImageToolbox-inspired separation of render/compress from save destination.

- [ ] Save Copy actually wired from editor
- [ ] original folder
- [ ] chosen folder
- [ ] one-time destination
- [ ] filename templates
- [ ] overwrite/new-copy policy
- [ ] preserve/strip selected EXIF
- [ ] ICC/profile preservation
- [ ] JPEG quality + estimated output size
- [ ] PNG
- [ ] TIFF
- [ ] HEIC
- [ ] AVIF
- [ ] Ultra HDR JPEG/R
- [ ] bit-depth options where supported
- [ ] resize/resampling controls
- [ ] export profiles
- [ ] batch export
- [ ] per-file failure reporting
- [ ] never accidentally overwrite originals

Any ImageToolbox-derived source must retain Apache-2.0 attribution/notice requirements and be documented with exact provenance.

---

# Phase 10 — Batch editing

- [ ] apply preset to multiple images
- [ ] common WB/exposure adjustment across selection
- [ ] copy/paste selected operations across images
- [ ] queued exports
- [ ] Android job/background execution appropriate for long exports
- [ ] progress + per-image results
- [ ] cancellation without corrupting outputs

---

# Phase 11 — Rendering diagnostics and telemetry

Debug/engineering panel should expose:

- [ ] source dimensions
- [ ] source MIME/codec
- [ ] source profile/gamut/transfer function
- [ ] source bit depth where known
- [ ] decoded pixel format
- [ ] active tile level
- [ ] current decoded region resolution
- [ ] current zoom
- [ ] source-pixel-to-physical-pixel ratio
- [ ] GPU/backend information
- [ ] surface format/color mode
- [ ] HDR state/headroom
- [ ] gain-map state
- [ ] cache hit/miss
- [ ] render/frame timing

This panel is for validation and performance engineering; it must not alter Reference output.

---

# Phase 12 — Automated image-quality regression testing

- [ ] deterministic renders for controlled inputs
- [ ] reference-output comparisons
- [ ] exact comparison where mathematically expected
- [ ] RMSE/PSNR where useful
- [ ] SSIM where useful
- [ ] Delta-E for defined color-transform tests
- [ ] HDR/gain-map validation cases
- [ ] tile seam regression cases
- [ ] 1:1 pixel-mapping validation
- [ ] fail CI on unintended Reference rendering changes

Reference-mode output must not be changed on subjective preference alone.

---

# Phase 13 — Editor UX

The editor should be powerful without becoming a wall of sliders.

## Groups

- Light
- Color
- Detail
- Curves
- Masks
- Analyze
- View
- History

## Interaction goals

- [ ] touch-first controls
- [ ] generous hit targets
- [ ] smooth slider interaction
- [ ] precise numeric entry
- [ ] fine-adjust mode
- [ ] haptic zero/neutral detent
- [ ] double-tap control/label to reset where appropriate
- [ ] touch-and-hold original without blocking pinch/other multitouch
- [ ] fast operation navigation
- [ ] visible undo/redo state
- [ ] visible edit history
- [ ] no destructive ambiguity

---

# Completion definition

A feature is not complete until:

1. implementation exists;
2. fidelity/correctness implications are understood;
3. tests or explicit manual validation are recorded;
4. known limitations are documented;
5. relevant project state/chat/design docs are updated;
6. CI/build status is known;
7. any borrowed/adapted code has proper attribution/licensing documentation.
