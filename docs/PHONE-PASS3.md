# Phone experience: approved requirements and acceptance

Owner feedback and clarification, 19 November 2026. This is the detailed pass-3
specification. WORK.md owns the current action; PHONE-WORK.md owns progress.
Neither compaction nor a passing test changes these requirements.

## Deliverable

Complete the standalone Android prayer/adhan experience and TV companion in the
existing repository/branch. Owner will test one combined build and send feedback.
Prior 0.2.1 functionality is a foundation, not accepted UX. Continue sequentially
without waiting for feedback between slices. Keep PR #9 draft for acceptance.

- Fresh install has a guided location → notifications/precise alarms →
  personalization journey. Explain permissions, handle denied/disabled access,
  preserve progress, permit later setup, and make unfinished setup discoverable.
  A phone works without a TV, account, cloud or manually typed coordinates.
- Daily prayer, calculation, global/per-prayer adhan, private audio import,
  previews/Stop, volume/duration, silent/vibration, before/after reminders,
  weekdays/quiet hours and foreground travel refresh form coherent journeys.
  Reuse Athar's actual recording for interaction and audio-control references.
  Do not extract/redistribute its recordings or invent unverified background
  behavior. Preserve approved media, exact sacred text and provenance.
- Phone themes, backgrounds and personalization show real previews using shared
  assets/tokens and phone composition. Check English/Arabic, RTL, large text and
  landscape. A label alone does not constitute a preview or working feature.
- Both Set up my TV and Pair new TV open one platform chooser. Android/Google TV
  and LG webOS work; Samsung Tizen, Roku and other unimplemented platforms are
  locked with a lock icon and Coming soon on click. Do not pretend support.
- Supported TV flow: instructions specific to its OS/model variations and
  developer access → release channel with latest selected by default (stable /
  preview alternatives) → guided setup. An Advanced icon switches source UI to
  local APK/IPK. Preserve connection, trust, integrity, downgrade, cancellation,
  recovery and data guards; existing Miqat installations can proceed to ordinary
  approved pairing. Keep platform permissions honest and credentials separate.
- Add working Qibla and home-screen prayer widgets now, as explicitly selected
  by the owner. Qibla needs shared bearing rules and native sensor quality/
  calibration/fallback; widgets reuse schedule and lifecycle, not an idle service.
- Quran, adhkar and other broader worship modules are locked Coming soon entries,
  with a message on click. They are not implemented content/features in this pass.
- Preserve portable iOS boundaries and Apple-core checks; Android is first.
  Do not claim an iOS app, hardware acceptance or a public release exists.

## Engineering requirements (apply to later agents too)

Use the smallest complete solution. No overengineering, speculative flexibility,
one-implementation factories, pass-through wrappers or framework migration.
Prefer existing helpers, native facilities and installed dependencies. Add no
dependency for a few lines. Delete obsolete code instead of layering over it.

Keep human-readable, maintainable, expandable object-oriented code: one coherent
responsibility per class, focused methods, clear names, explicit ownership and
error paths. Split by job, not arbitrary line counts. Avoid large classes that
combine rendering, policy, storage, permissions and device transport. Preserve
validation/error handling while moving code. Do not create tiny delegation
classes merely to reduce a file's line count.

Use canonical base contracts and core rules for genuinely shared behavior;
platform shells extend declared capabilities and own rendering/OS integration.
New phone features useful to TVs should be reusable in shared/core/data/design
for subsequent TV releases. Do not force phone sensors or widget APIs into core,
silently enable unsupported TV features or duplicate deterministic policy.
Scalability means extending known boundaries without changing unrelated shells,
not adding infrastructure for hypothetical load. Expected load is one local
household, offline prayers and foreground authenticated household LAN.

Audit the whole repository's entry points, dependencies, shared contracts,
persistence/trust boundaries, background/media lifecycle and high-risk flows.
Trace concrete findings to callers before fixing/removing code. Record findings,
fixes, deferred decisions and uninspected areas; never claim every line reviewed
from a filename scan. Remove slop, unused paths and proven duplication. Preserve
security, data, accessibility, Arabic and hardware calibration requirements.

Tests protect observable features, failures and edge cases. Avoid trivial
getter/constant tests, implementation mirrors and blanket coverage targets. Reuse
focused suites; remove tests only when their purpose is redundant with identified
remaining evidence. Never weaken required checks to get green builds.

Before delivery review the final diff for dead paths, duplicated rules, swallowed
errors, private data and misleading claims. Required affected checks and rendered
flow inspection must pass. Distinguish emulators/mocks from physical devices.
Update CHANGELOG, guides, EVIDENCE and PR to actual final scope.

## Persistence and stopping rules

At milestones and before compaction update PHONE-WORK.md with exact next action,
changed files, commands/results and unresolved items. Private logs/captures stay
under ignored state/. Commit validated milestones and preserve normal history.
AGENTS.md contains standing quality guardrails. A goal also tracks this scope.

Deliver one combined exported APK and concise evidence/screenshots after local
acceptance and final-head CI. Keep hardware acceptance open for the owner. No
release publication, physical deployment or merge in this pass. After handoff,
wait for feedback; do not invent more work or reintroduce unnecessary scaffolding.
