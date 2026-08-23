# Aves Editor Fork Documentation Index

Documentation is a P0 part of this fork. Start here before substantial work.

## Mandatory continuity reading order

1. [`PROJECT_STATE.md`](PROJECT_STATE.md) — current branch, implemented work, next work, known uncertainties.
2. [`CONTINUITY_PROTOCOL.md`](CONTINUITY_PROTOCOL.md) — rules for documenting every material decision/change/test/failure/chat requirement.
3. [`chat/`](chat/) — chronological project conversation records.
4. [`MAXIMUM_FIDELITY_VIEWER.md`](MAXIMUM_FIDELITY_VIEWER.md) — non-negotiable reference-viewer fidelity requirements.

## Documentation rule

No important project knowledge may exist only in chat, memory, a local working tree, or an unreferenced commit.

When code changes materially affect behavior, quality, architecture, compatibility, HDR/color handling, export, storage, licensing, or build/test behavior, update the relevant documentation in the same work session.

## Current project pillars

- maximum-fidelity hardware-limited image viewing;
- non-destructive high-precision editing;
- explicit Reference vs enhancement/edit rendering separation;
- wide gamut and HDR/Ultra HDR as first-class paths;
- robust Android export/save behavior influenced by ImageToolbox architecture;
- complete project continuity across chats and developer handoffs.
