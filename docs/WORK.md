# Current work

Updated: 2026-11-22. Approved scope: refresh README, agent guidance, current docs,
website and screenshots for the standalone Android phone app, its TV companion
mode, Android/Google TV and LG webOS. Preserve the existing interactive TV preview
and its direct links. Use current native phone/Android captures and explicitly
label LG browser captures; exclude private state and unsupported product claims.

Implementation and local native/browser/document checks are complete; EVIDENCE.md
records the capture sources and corrected mobile hero. Delivery uses required PR
checks and a normal merge, followed by the existing validated Pages workflow.
PR/main/deployment records own remote status. No additional feature work is approved.

Product baseline: Android TV 0.7.2/code 13, LG 0.2.30, phone 0.5.18/code 34.
PR #13 is merged and v0.7.2-preview.1 is an unpublished, verified preview draft.
Stable signing remains unconfigured. Physical TV acceptance and public app
release publication remain separate. No app behavior changes are in this scope;
correct the stale TV-preview Apply caption found while capturing the current UI.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Retain the phone's existing local signer; private logs/captures stay under state/.
