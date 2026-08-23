# Project Continuity Protocol

This document defines a P0 requirement for this fork: **project continuity must survive chat boundaries, developer handoffs, device changes, branch changes, and long gaps between work sessions.**

The repository is the durable source of truth. Chat memory is useful, but it is never the only place where an important project fact may live.

## 1. Documentation is part of the implementation

A change is not complete when code compiles. It is complete only when the relevant documentation is updated.

Document all of the following:

- user requirements and wording that materially affects behavior;
- architectural decisions and the reason for them;
- rejected approaches and why they were rejected;
- experiments, including unsuccessful ones;
- bugs, symptoms, reproduction steps, root causes, and fixes;
- visual-quality observations and test-device observations;
- branch names, base commits, feature commits, PRs, and build artifacts;
- upstream code or projects used for reference or adaptation;
- licensing implications of borrowed/adapted code;
- algorithms, constants, color spaces, transfer functions, precision choices, and assumptions;
- performance compromises and any quality fallback;
- build-system changes;
- test results and known untested areas;
- future work that was intentionally deferred;
- important chat decisions and user feedback.

If it would matter to someone resuming the project later, it belongs in the repository.

## 2. Chat continuity

Relevant project chats must be captured under `docs/chat/` as chronological project records.

Each chat record should preserve:

- date/time where available;
- user request or requirement;
- technical interpretation;
- decisions made;
- repository actions taken;
- commits/branches created;
- unresolved questions;
- corrections to earlier assumptions.

Exact user wording should be preserved when it materially defines a requirement. Long assistant explanations may be summarized when the summary preserves every technical decision, but no material requirement may be omitted.

## 3. Project-state snapshot

`docs/PROJECT_STATE.md` is the fast-resume document. It must always answer:

- What branch are we working on?
- What upstream commit is the work based on?
- What has already been implemented?
- What is currently being implemented?
- What remains?
- What are the non-negotiable quality requirements?
- What are the known problems or uncertainties?
- Which commits matter?

When a session ends after meaningful work, update the state snapshot.

## 4. Decision logging

Architectural or image-quality decisions must include rationale, not just outcomes.

Examples:

- why Reference mode must contain no hidden enhancement;
- why editing should use a non-destructive recipe rather than repeated bitmap rewrites;
- why Ultra HDR gain maps cannot simply be reused after arbitrary tonal edits;
- why an ImageToolbox saving abstraction is being adapted instead of copying an entire feature module;
- why physical display pixels matter for source-pixel inspection and tile LOD selection.

This prevents future work from accidentally undoing a deliberate choice.

## 5. Code annotations

Code comments should explain **why** a non-obvious implementation exists, especially when the implementation protects fidelity, compatibility, or correctness.

Do not fill straightforward code with redundant comments. Instead, annotate:

- unusual math;
- platform workarounds;
- precision-sensitive decisions;
- HDR/Ultra HDR behavior;
- color-management behavior;
- tile-boundary/LOD behavior;
- performance-versus-quality decisions;
- behavior that intentionally differs from upstream Aves.

## 6. Fidelity changes require evidence

Any viewer or editor change that can alter rendered pixels should be documented with:

- expected behavior;
- source format/color space/HDR state;
- device/display path if relevant;
- before/after observation or objective measurement;
- whether the change affects Reference mode or only an explicit enhancement/edit mode.

Reference mode must never be modified based only on subjective preference.

## 7. Commit discipline

Prefer focused commits with descriptive messages. Important commits should be recorded in `PROJECT_STATE.md` or the relevant design document.

For experiments that may be reverted, state the hypothesis in the commit message or project journal.

## 8. Source attribution and licenses

When adapting code or behavior from another project, document:

- source project;
- exact file/commit when practical;
- license;
- whether code was copied, adapted, or independently reimplemented;
- notices required in the distributed application/source tree.

ImageToolbox-derived code must retain Apache-2.0 requirements. Upstream Aves BSD-3-Clause requirements remain applicable.

## 9. Session resume procedure

Before continuing substantial work after a new chat/session:

1. Read `docs/PROJECT_STATE.md`.
2. Read `docs/CONTINUITY_PROTOCOL.md`.
3. Read the most recent relevant file in `docs/chat/`.
4. Read design documents for the subsystem being changed.
5. Inspect current branch/commits before writing code.
6. Do not rely on remembered state when the repository can answer the question.

## 10. Session close procedure

After meaningful work:

1. update project state;
2. append/update the current chat log;
3. update subsystem design docs;
4. record important commits/builds/tests;
5. record known failures and next steps;
6. leave the repository in a state where another session can resume without asking the user to reconstruct history.

## Absolute rule

**No important project knowledge is allowed to exist only in chat.**
