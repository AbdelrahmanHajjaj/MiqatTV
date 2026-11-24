# Validation and deployment

| Action | Trigger | Outcome |
| --- | --- | --- |
| [Validate](../.github/workflows/validate.yml) | Pull request targeting `main` | Affected checks and the required merge status. |
| [Build](../.github/workflows/build.yml) | Manual, **Development** or **Staging** | Installable Actions artifacts or a signed GitHub prerelease. |
| [Device simulation](../.github/workflows/device-simulation.yml) | Manual on `main`, or called by Production | Android emulator and LG webOS browser-mock results for one Staging candidate. |
| [Promote to Production](../.github/workflows/promote.yml) | Manual on `main` with publication approval | Device simulation, byte-for-byte package promotion and deployment of the release website. |

## PR gate

The `main` rules require a pull request, the GitHub Actions **Required checks**
status, an up-to-date branch and resolved review conversations. Merge with a
normal merge commit. There are no automatic builds or deployments on merge.

Select checks runs on every PR. It checks workflow syntax, selector/gate
regressions and whitespace using the complete merge-base diff. **Required
checks** accepts successful selected jobs and skipped unselected jobs. Missing
selection, cancellation and unexpected skips fail the gate.

- Ordinary documentation and the PR template select the gate alone. Product
  screenshot PNGs select Preview. Packaged notices and generated
  `docs/UI-GAPS.md` select all jobs.
- Site paths select Preview. Android TV and LG paths select their own platform.
  Installer changes select Android TV and Miqat Phone.
- Changes confined to the phone app or its portable core select Miqat Phone.
  Companion contracts, LAN, QR and generators also select their TV and preview
  consumers.
- Shared sources, tooling, workflow configuration and unknown paths select all
  jobs. Changes spanning areas select their union.

| Job | Coverage |
| --- | --- |
| Select checks | Check selection and release regressions, canonical data/geometry and approved media hashes for selected builds. |
| Core | Portable JVM/Java/JS and legacy ES5. |
| Android build | Package size, packaged audio, debug and release build/lint, instrumentation compilation and host regressions. |
| LG build and tests | ES5 app/service, volume, scheduling, HTTPS and LAN fixtures and full-audio IPK. PRs also run a focused browser smoke check. |
| Preview | Data/token freshness, automated regressions, static build and bilingual website smoke check. |
| Miqat Phone | Portable settings, QR/LAN host checks, debug and release build/lint, instrumentation compilation and packaged audio. |

Build reuses the package checks on demand. Production builds and tests the
website separately from the packaged Staging source. See
[Releasing](RELEASING.md) for asset names, signing and retries.

## Release evidence

Device simulation boots isolated Android emulators. It installs and launches
the signed Staging APKs first, then runs native instrumentation on debug APKs
built from the same commit. LG runs the full browser suite against the app and
service extracted from the verified IPK. These results describe emulated or
mocked behavior. Physical device acceptance follows [Testing](TESTING.md).
Development, Staging and ordinary PR jobs never boot emulators.

GitHub jobs use separate workspaces, and generators stay sequential within each
job. Actions are pinned to commits and checkout does not persist credentials.
Release-writing permissions are limited to Staging and Production publication
jobs. Only the Staging job receives the signing secrets. Pages privileges are
limited to the Production deployment job.

## Website

The [Miqat website](https://abdelrahmanhajjaj.github.io/MiqatTV/) publishes the
Arabic and English product pages and the interactive TV preview from the
Production candidate's source. Its packaged screenshots come from the same
candidate. The build derives the download URLs and `/updates.json` from the
Production tag and verified Android APK versions.

Pages uses GitHub Actions and the main-only `github-pages` environment. The
Production run prepares the site before publication and deploys it afterwards.
A Pages retry uses the same artifact and checks that its release is still the
current Production version. Site-only changes go live with the next Production
release.
