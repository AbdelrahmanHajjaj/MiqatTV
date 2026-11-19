# Mobile reference: Athar

Owner-supplied reference, 19 November 2026. The owner personally uses Athar.

Sources: [Google Play](https://play.google.com/store/apps/details?id=com.ahmedmostafa.athar),
[developer site](https://ahmedmostafa147.github.io/athar-web/index.html#download),
[published website/FAQ source](https://github.com/ahmedmostafa147/athar-web/blob/main/index.html).
The public repository contains the website; its README says app source is private.
Initially reviewed published descriptions and promotional artwork. On 19 November
the owner supplied an actual in-app recording (~5m12s); observations follow below.
No independent hands-on Athar session or source-code review has been performed.
Promotional device frames do not establish supported platforms.

Athar describes per-prayer adhan/mute, manual city selection, a monthly timetable,
restart recovery, optional worship modules and manufacturer-specific alert help.
Its site explains permissions, offline boundaries and content provenance.

Apply to the current Miqat scope:

- Keep the daily schedule compact and next prayer prominent. Miqat now uses a
  two-column six-time overview, with separate per-prayer customization on tap.
- Put background-delivery help next to alert permissions. Miqat provides Android
  app-settings access and explains battery restrictions and force-stop recovery;
  exact OEM instructions need device/version validation.
- Retain independent local settings, explicit permission setup and visible Stop.
  Miqat calculates new locations offline and keeps approved adhan recordings local.
- Retain Miqat's existing artwork, tokens, Arabic/English and accessibility. This
  reference supplies product lessons, without copying branding, assets or content.

Candidates for discussion after the owner's combined build feedback: a monthly
prayer overview, widgets/quick actions, optional Qibla/adhkar/tasbih and richer
calendar tools. Quran reading/audio is a separate content, licensing and resource
decision. These are reference ideas rather than added requirements for this build.

Use the owner's real Athar usage to prioritize subsequent work. Published feature
and reliability claims are reference material, not Miqat acceptance evidence.

## Owner recording: observed mobile patterns

Reviewed the supplied recording on 19 November 2026. Raw frames/contact sheets
remain private under ignored state/athar-recording; do not copy personal location,
profile details or third-party content into repository assets.

Approximate timestamps:

| Range | Observed | Miqat implication |
| --- | --- | --- |
| 00:00–00:24 | Home next-prayer summary, small task cards and persistent labelled root navigation | Strong mobile hierarchy, compact summaries and stable root destinations |
| 00:48–01:24 | Prayer location action, compact time/alert rows, current-state highlight and separate monthly timetable | Keep location convenient; separate daily use from deeper calendar views |
| 01:44–02:16 | Categorized settings and compact prayer rows for alert type, general/per-prayer voice, silent-mode behavior, Stop behavior, reminders and troubleshooting | Show saved values; use focused editors instead of keeping every editor open |
| 02:16–02:48 | Reading preview, focused appearance controls and a searchable reciter sheet | Preview the setting being changed; use short sheets/search as appropriate |
| 03:36–04:08 | App palette selection and a widget preview with size/contrast controls | Reuse Miqat tokens and provide visible outcomes; widgets remain a separate feature |
| 04:24–04:40 | Inspectable sources/licences by content category | Preserve accurate source/rights records for Miqat's own material |

These are observations of displayed screens. Background delivery, audio, sensors,
updates and other behavior were not independently verified. Some brief transitions
may need closer review when implementing their corresponding Miqat editor.

Pass-2 design direction: stable Prayer / Your TVs / Phone settings root navigation;
TV Remote/TV settings are nested controls. Compact saved-value rows open one
focused editor, with numeric Done/Cancel only during editing. Use one consistent
spacing/type hierarchy, clear primary actions and accessible Arabic/English,
rather than copying Athar's branding or expanding into its content/feed modules.
Displayed optional power-button/face-down Stop controls are further discussion
candidates; this recording does not automatically add sensor features to scope.
