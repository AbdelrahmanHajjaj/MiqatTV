#!/bin/sh
set -eu

cd "$(dirname "$0")/../.."
sdk="${1:?Pass iphoneos or iphonesimulator}"
case "$sdk" in
    iphoneos|iphonesimulator) ;;
    *) exit 2 ;;
esac
root="$PWD/state/ios-adb-crypto"
build="$root/$sdk-build"
prefix="$root/$sdk"
cmake -S companion/ios/adb-crypto -B "$build" \
    -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT="$sdk" -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=17.0 -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$prefix"
cmake --build "$build" --target miqat_adb_spake --parallel "$(sysctl -n hw.logicalcpu)"
cmake --install "$build" --component miqat-adb
