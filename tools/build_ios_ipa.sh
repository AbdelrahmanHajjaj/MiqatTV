#!/bin/sh
set -eu

: "${IOS_CERTIFICATE_BASE64:?Set the iOS distribution certificate secret}"
: "${IOS_CERTIFICATE_PASSWORD:?Set the iOS certificate password secret}"
: "${IOS_APP_PROFILE_BASE64:?Set the Miqat app Ad Hoc profile secret}"
: "${IOS_WIDGET_PROFILE_BASE64:?Set the Miqat widget Ad Hoc profile secret}"
: "${IOS_IPA_PASSPHRASE:?Set the iOS artifact encryption passphrase secret}"
if [ "${#IOS_IPA_PASSPHRASE}" -lt 32 ]; then
    echo 'Use a randomly generated artifact passphrase of at least 32 characters.' >&2
    exit 1
fi
if [ "${1:-}" = --check-secrets ]; then exit 0; fi
: "${RUNNER_TEMP:?This script requires a GitHub Actions runner}"

umask 077
signing=$(mktemp -d "$RUNNER_TEMP/miqat-ios.XXXXXX")
keychain="$signing/build.keychain-db"
profiles="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
app_profile=
widget_profile=
cleanup() {
    security delete-keychain "$keychain" >/dev/null 2>&1 || :
    if [ -n "$app_profile" ]; then rm -f "$app_profile"; fi
    if [ -n "$widget_profile" ]; then rm -f "$widget_profile"; fi
    rm -rf "${signing:?}"
}
trap cleanup EXIT

printf '%s' "$IOS_CERTIFICATE_BASE64" | base64 -D > "$signing/signing.p12"
printf '%s' "$IOS_APP_PROFILE_BASE64" | base64 -D > "$signing/app.mobileprovision"
printf '%s' "$IOS_WIDGET_PROFILE_BASE64" | base64 -D > "$signing/widget.mobileprovision"
printf '%s\n' "$IOS_IPA_PASSPHRASE" > "$signing/passphrase"
password=$(openssl rand -hex 24)
security create-keychain -p "$password" "$keychain"
security set-keychain-settings -lut 3600 "$keychain"
security unlock-keychain -p "$password" "$keychain"
security import "$signing/signing.p12" -P "$IOS_CERTIFICATE_PASSWORD" \
    -T /usr/bin/codesign -t cert -f pkcs12 -k "$keychain"
security set-key-partition-list -S apple-tool:,apple: -s -k "$password" "$keychain" >/dev/null
security list-keychains -d user -s "$keychain"
rm -f "$signing/signing.p12"
unset password IOS_CERTIFICATE_BASE64 IOS_CERTIFICATE_PASSWORD IOS_APP_PROFILE_BASE64 \
    IOS_WIDGET_PROFILE_BASE64 IOS_IPA_PASSPHRASE

security cms -D -i "$signing/app.mobileprovision" > "$signing/app.plist"
security cms -D -i "$signing/widget.mobileprovision" > "$signing/widget.plist"
python3 - "$signing" <<'PY'
from datetime import datetime, timezone
from pathlib import Path
import plistlib
import re
import sys

root = Path(sys.argv[1])
groups = "com.apple.security.application-groups"
profiles = {}
for name, bundle_id in (("app", "tv.miqat.ios"), ("widget", "tv.miqat.ios.widget")):
    profile = plistlib.loads((root / f"{name}.plist").read_bytes())
    team, = profile["TeamIdentifier"]
    entitlements = profile["Entitlements"]
    expiration = profile["ExpirationDate"].replace(tzinfo=timezone.utc)
    devices = set(profile.get("ProvisionedDevices", []))
    uuid = profile["UUID"]
    if (not re.fullmatch(r"[A-Z0-9]{10}", team)
            or not re.fullmatch(r"[0-9a-fA-F-]{36}", uuid)
            or entitlements.get("application-identifier") != f'{profile["ApplicationIdentifierPrefix"][0]}.{bundle_id}'
            or entitlements.get("get-task-allow") is not False
            or "group.tv.miqat.ios" not in entitlements.get(groups, [])
            or expiration <= datetime.now(timezone.utc) or not devices):
        sys.exit(f"Use a current Ad Hoc profile for {bundle_id} with the Miqat App Group and registered iPhones.")
    profiles[bundle_id] = (team, uuid, devices)

app, widget = profiles.values()
if app[0] != widget[0] or app[2] != widget[2]:
    sys.exit("Use the same Apple team and registered iPhones in both Miqat profiles.")
with (root / "ExportOptions.plist").open("wb") as options:
    plistlib.dump({
        "method": "release-testing", "destination": "export", "signingStyle": "manual",
        "signingCertificate": "Apple Distribution", "teamID": app[0],
        "provisioningProfiles": {bundle_id: profile[1] for bundle_id, profile in profiles.items()},
    }, options)
PY

plist=/usr/libexec/PlistBuddy
options="$signing/ExportOptions.plist"
team=$("$plist" -c 'Print :teamID' "$options")
app_uuid=$("$plist" -c 'Print :provisioningProfiles:tv.miqat.ios' "$options")
widget_uuid=$("$plist" -c 'Print :provisioningProfiles:tv.miqat.ios.widget' "$options")
mkdir -p "$profiles"
app_profile="$profiles/$app_uuid.mobileprovision"
widget_profile="$profiles/$widget_uuid.mobileprovision"
cp "$signing/app.mobileprovision" "$app_profile"
cp "$signing/widget.mobileprovision" "$widget_profile"

archive="$signing/Miqat.xcarchive"
xcodebuild -project companion/ios/Miqat.xcodeproj -scheme Miqat -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$archive" \
    CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM="$team" CODE_SIGN_IDENTITY='Apple Distribution' \
    MIQAT_APP_PROFILE="$app_uuid" MIQAT_WIDGET_PROFILE="$widget_uuid" archive
xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$options" -exportPath "$signing/export"

ipa="$signing/export/Miqat.ipa"
ditto -x -k "$ipa" "$signing/verified"
app="$signing/verified/Payload/Miqat.app"
widget="$app/PlugIns/MiqatPrayerWidget.appex"
codesign --verify --deep --strict "$app"
test "$("$plist" -c 'Print :CFBundleIdentifier' "$app/Info.plist")" = tv.miqat.ios
test "$("$plist" -c 'Print :CFBundleIdentifier' "$widget/Info.plist")" = tv.miqat.ios.widget
for bundle in "$app" "$widget"; do
    test -s "$bundle/embedded.mobileprovision"
    test "$("$plist" -c 'Print :CFBundleSupportedPlatforms:0' "$bundle/Info.plist")" = iPhoneOS
    codesign -d --entitlements - "$bundle" > "$signing/entitlements.plist" 2>/dev/null
    test "$("$plist" -c 'Print :com.apple.security.application-groups:0' "$signing/entitlements.plist")" = group.tv.miqat.ios
done

# This repository is public and the IPA contains the registered device list.
gpg --batch --pinentry-mode loopback --passphrase-file "$signing/passphrase" \
    --cipher-algo AES256 --output "$signing/Miqat.ipa.gpg" --symmetric "$ipa"
gpg --batch --pinentry-mode loopback --passphrase-file "$signing/passphrase" \
    --output "$signing/decrypted.ipa" --decrypt "$signing/Miqat.ipa.gpg"
cmp "$ipa" "$signing/decrypted.ipa"
cp "$signing/Miqat.ipa.gpg" "$RUNNER_TEMP/Miqat.ipa.gpg"
