# Contributing to Miqat

File issues and PRs in Arabic or English. Report the platform, model, OS, app
version and steps to reproduce. Keep private device details out of reports.

Use a feature branch and submit a PR to this repository's main branch. Keep the
change coherent, include its affected tests/docs, and use the PR template. Preserve
branch commits with a normal merge; never squash. Agents use gh and follow the
standing merge instruction in [AGENTS.md](../AGENTS.md). Publishing releases and
physical-device operations require the owner's corresponding approval.

## Implement a change

Read the [architecture](ARCHITECTURE.md), [UI contract](UI-CONTRACT.md) and
[test requirements](TESTING.md). Put portable product decisions in shared/core,
canonical data in shared/data and design in shared/design. Shells own rendering
and OS integration. Check affected consumers of a shared source change.

Edit canonical sources and regenerate outputs; do not hand-edit generated
Java/XML/CSS/catalog files. Keep existing dependencies and platform conventions.
Android uses Java Views. Shipped LG app/service code must parse as ES5 and work
on WebKit 538.2 / Node 0.10.25. Build tools use JDK 17 and Node 24.

Keep Arabic/English, RTL/LTR, joined Arabic, focus and clipping correct at supported
sizes. Ordinary numbers edit inline with shared immediate-save semantics. Use the
shared choice policy and design tokens. Declare missing platform capabilities.
Keep touch and remote journeys appropriate to their shells. Phone-only controls
belong in shared/data/phone-ui-contract.json; common TV controls remain in
shared/data/ui-contract.json. The phone's TV companion mode is part of this repository.

Preserve exact religious text and complete adhan wording. Cite surah/ayah and a
verified source when changing Quranic text. Calculation changes need a dated
comparison with the chosen authority, including method, timezone, school and offsets.
Do not change frozen fixtures without an intentional source or behavior change.

## Validate and deliver

Run the checks for the changed boundaries in [TESTING.md](TESTING.md). Add focused
behavioral regressions and inspect affected rendered UI. Distinguish mocks,
emulators, live diagnostics and human hardware observations. State skipped checks
and remaining limits; do not weaken checks to obtain a pass.

Update CHANGELOG.md for visible changes and the relevant guide for changed setup
or support. Keep raw evidence in ignored state/, with concise durable outcomes
in [EVIDENCE.md](EVIDENCE.md). Review the staged diff for credentials, keys,
restricted media, outputs and private device data before committing.
Update the bilingual website and current screenshots when product presentation
changes. Public screenshots use sanitized fixtures and recorded versions/sources.

Only reviewed, approved recordings belong in shared/media/; originals and review
material remain under ignored state/. Keep
the MIT and third-party notices in packaged builds; see [licences](THIRD-PARTY.md).
No ads, analytics, accounts, feeds or new network behavior belong in incidental work.
