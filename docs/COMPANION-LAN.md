# Foreground companion LAN design

Implementation design for the authorized Miqat companion branch. Scope/status remain in
[COMPANION-WORK.md](COMPANION-WORK.md). This document is not hardware evidence.

## Trust and lifecycle

- Same household LAN, no accounts/cloud, Bluetooth, Wi-Fi Direct or ADB.
- Standard HTTPS, TLS 1.2 minimum, with a unique TV-generated private key. QR
  schema 3 carries its SHA-256 pin and short-lived random pairing information.
  Pin verification is request-scoped; never change global trust, disable peer
  verification or bundle a private key. The scanned certificate is a request-scoped
  trust anchor for Android's standard trust manager; the leaf must also match the
  pin. For schemas 2 and 3 the phone first obtains the certificate with a TLS-handshake-only
  trust manager that accepts only the scanned SHA-256 pin and verifies validity
  and the self-signature. No HTTP data or credentials are sent during bootstrap.
  Subsequent requests use the normal scoped trust anchor. Schema 1 with its inline
  certificate and schema 2 JSON remain accepted; no path changes global trust or invents a
  transport encryption protocol.
- A new phone requests access using the QR information. TV displays its name and
  a matching verification code; physical remote approval grants a unique random
  credential. QR possession alone never grants settings access. There is no LAN
  approval/revocation API: those actions exist only in the trusted TV UI.
- Each approved phone has an independent credential, stored securely by the phone
  shell. TV persists only credential hashes and public phone metadata. Revocation
  takes effect on the next request; it does not touch launcher preferences.
- Bound request sizes, clients, pending requests, expiry, socket lifetime and
  worker concurrency. Reject malformed/unknown fields, redirects, browser-origin
  requests and unauthenticated settings calls. Never log credentials/QR secrets.
- Accept only local/private numeric addresses initially. Known-address reconnect
  reads identity/status, never writes settings. Rescanning the same pinned TV may
  repair a changed address without reapplying cached values.
- Listener runs only while our app is active. Android uses activity lifecycle,
  not a background service. LG uses a foreground app subscription, closed on
  suspend/exit. Existing media, scheduling and startup retain their ownership.

## Pairing exchange

1. TV opens a bounded pairing window and shows endpoint, pin and one-time secret
   as a compact QR code with original mihrab modules and finder eyes. First key generation is asynchronous
   and reports progress through the foreground subscription. Changing/cancelling/
   expiring the window invalidates old requests.
2. Phone validates QR shape/address, connects to the pinned certificate and sends
   its name plus a fresh random request nonce. TV permits one pending request.
3. Phone and TV show the same short verification code. TV remote approves/rejects.
4. Approved phone retrieves its unique credential over the pinned connection.
   A completion acknowledgement invalidates the temporary pairing exchange.
5. TV lists approved phones and offers remote-controlled individual revocation.
   Unfinished/expired exchanges cannot authorize a different request.

## Settings exchange

Opening Settings fetches actual values, supported controls/choices and system
locale. Shared IDs stay semantic; shell preference names remain internal. Reuse
canonical catalogs/labels and the companion's existing control bindings. Actual
app inventories/remote assignments replace demo examples; never assume a model's
capabilities from its brand.

Confirmed edits carry their original baseline and explicit changes. TV validates
and applies them through existing behavior, including dependent city defaults,
then returns actual values. Preserve unrelated changes and appearance grouping.
Reject writes while the TV has an active local editor if they would discard that
draft. LG local full-object saves must merge intentional changes against their
baseline, so they cannot overwrite a newer phone edit.

Networking stays off the phone UI thread. Failures preserve drafts; an uncertain
write outcome requires reading actual state before another edit, never blind
replay. No real update installation is exposed in this milestone.

## Compact QR schema 3 and theme rendering

The symbol contains `MIQ3:` plus exactly 107 Base45 characters in QR alphanumeric
mode. The decoded 71-byte frame contains schema byte 3, four IPv4 octets, a two-byte
unsigned network-order port, the complete 32-byte SHA-256 certificate pin and the
complete 32-byte pairing-window token. No field is truncated or fetched from a
cloud lookup. Normal endpoint/port/LAN validation still runs before network I/O.
Reject wrong lengths/versions/alphabets, overflowing groups, zero ports and
noncanonical URL/token inputs. The JVM and ES5 implementations share test vectors.
[Base45](https://www.rfc-editor.org/rfc/rfc9285.html) is an encoding, not encryption;
TLS pin verification and physical TV approval remain the access boundaries.

Miqat uses its own `miqat-tv-pair` type and `MIQ3:` prefix. JSON schemas 1 and 2
remain accepted only with the Miqat type. Previous app identities, QR codes and
paired credentials are not imported; Miqat requires a fresh installation/pairing.

Both TV shells render the canonical medallion eyes, mihrab centres and connected
rounded modules, with a five-module light quiet zone. Colours follow the active
TV theme automatically. Shared geometry/contrast thresholds live in
`shared/design/assets/qr-style.json`; renderers keep data/frame/centre gradient
endpoints at least 6:1 and thin decorative inlays at least 3:1 against the paper.
An unsuitable dark custom paper falls back to the canonical light paper. These
rendering adjustments do not change encoded bit values or approval permissions.

## Explicit foreground Remote commands

Authenticated `GET /v1/remote` returns the shell's offered canonical action IDs,
actual discovered input values and current media state. `POST /v1/command` accepts
only `{id,value}` for Home, Inputs, LG's current TV picture, radio Play/Stop and
12-second adhan preview/Stop. The allowlist references existing UI contract IDs;
it grants no pairing approval, OS command or arbitrary intent execution.

Commands are separate from settings. Each tap sends once; the phone never replays
an uncertain command on reconnect. Refresh reads actual Remote state before
further commands. Stop remains available for recovery and cancels only the companion
adhan preview, preserving scheduled alerts. Local TV editors/media guard other
commands. LG dispatches to its current foreground app session and requires a
bounded acknowledgement; stale sessions, lost replies or foreground loss cannot
be reported as confirmed execution. Accepted means the shell accepted the action;
loading/playing come from subsequent media state. Opening an external Android input
may background the launcher and disconnect this foreground-only protocol.

## Runtime considerations and verification

Android has platform Keystore/TLS and camera APIs. A QR codec is justified for
encoding/decoding, not a new UI/network framework. LG's target is ES5/Node 0.10.25:
do not assume modern crypto APIs. Modern hosts use ECDHE/GCM; the approved legacy
path uses native TLS 1.2 RSA/AES-128-GCM with SSL/TLS <1.2 disabled. Forge 1.4.3
handles PKI and yields during RSA key generation if native generation is absent;
its entropy comes only from OS randomness. Node handles TLS in both paths.
QR encoding uses qrcode-generator 2.0.4. Full upstream notices are packaged.
Private identity/trust files are separate from launcher settings and require owned
POSIX directories/files, no symlinks and atomic durable writes. Unsupported or
unsafe TLS capabilities must fail closed and leave that TV standalone. The legacy
cipher is an explicit owner-approved compatibility tradeoff; no plaintext, RC4,
CBC or unverified-certificate fallback is allowed. Do not infer actual LG OpenSSL patch status.
The official Node 0.10.25 server API explicitly lacks ECDH ciphers, so that documented
runtime cannot meet the modern endpoint's ECDHE/GCM baseline. RSA/AES-GCM instead
interoperates with the existing pinned Android client. Newer LG runtime/API support is not proof of
vendor security patch status or hardware acceptance. LG's current runtime table is
in its [service basics](https://webostv.developer.lge.com/develop/guides/js-service-basics).

The owner approved RSA/AES-GCM compatibility for the legacy runtime. Static RSA
lacks forward secrecy: later private-key compromise can expose recorded sessions.
Old runtime vulnerabilities remain; Miqat physical LG acceptance is pending.
Validation of the old identity is not acceptance of this port.

Verify malformed/expired QR, pending/rejected/approved exchange, token isolation,
wrong certificate, unauthenticated access, revocation, bounded input, lifecycle,
baseline conflicts, failed/uncertain writes and settings preservation. Use owned
emulators/local services and LG browser/service mocks first. Physical remote and
actual TV networking remain explicit acceptance gates; no TV operation is currently
authorized.

Sources consulted: [Android Keystore](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec),
[Node 0.10 TLS](https://nodejs.org/download/release/v0.10.25/docs/api/tls.html),
[webOS service lifecycle/API](https://webostv.developer.lge.com/develop/references/webos-service-reference),
[Forge PKI](https://github.com/digitalbazaar/forge),
[ZXing](https://github.com/zxing/zxing),
[QR generator](https://github.com/kazuhikoarase/qrcode-generator/tree/master/js).
Library/runtime compatibility is not established merely by these references.
