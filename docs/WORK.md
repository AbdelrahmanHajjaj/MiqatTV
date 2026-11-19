# Current work

Updated: 2026-11-19.

Owner tested 0.3.0 and requested the 12-item phone UX feedback pass in
[PHONE-PASS4.md](PHONE-PASS4.md). That is the approved current scope on
feature/companion-installation / draft PR #9. Preserve the inherited engineering
requirements in PHONE-PASS3.md and AGENTS.md.

Implementation is in progress. Most affected checks pass, including bilingual
native journeys, actual widget configuration/resize and the portrait large-text
matrix. Next: validate widget launcher variants, wide large-text/native renders,
then final audit/lint/version/CI and one combined APK. Exact progress is in
PHONE-WORK.md; private logs/captures stay under ignored state/.

Pass 3 final head f04c1c9 passed Validate, Companion and Apple-core CI. Previous
review export remains 0.3.0/code 8 until replaced by a validated pass-4 artifact.
LG IPK stays 0.2.28. No physical acceptance is inferred from emulator evidence.

Keep PR #9 draft for owner feedback. No merge, release publication or physical
operation. Public releases remain empty; actual public binary transfer awaits
publication. Do not start unrelated iOS/worship/TV-runtime work after handoff.
