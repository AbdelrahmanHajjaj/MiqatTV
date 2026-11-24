#!/bin/sh
set -eu

cd "$(dirname "$0")/../.."
root="$PWD/state/ios-ssh"
mkdir -p "$root"

fetch() {
    url="$1"
    filename="$2"
    checksum="$3"
    if ! printf '%s  %s\n' "$checksum" "$root/$filename" | shasum -a 256 -c -s; then
        curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
            --connect-timeout 20 --max-time 180 "$url" -o "$root/$filename.partial"
        printf '%s  %s\n' "$checksum" "$root/$filename.partial" | shasum -a 256 -c
        mv "$root/$filename.partial" "$root/$filename"
    fi
}

sdk="${1:?Pass iphoneos or iphonesimulator}"
case "$sdk" in
    iphoneos) target=ios64-xcrun; minimum=-miphoneos-version-min=17.0 ;;
    iphonesimulator) target=iossimulator-arm64-xcrun; minimum=-mios-simulator-version-min=17.0 ;;
    *) exit 2 ;;
esac
openssl=openssl-3.5.9
libssh=libssh2-1.11.1
prefix="$root/$sdk"
stamp="$openssl $libssh $(xcrun --sdk "$sdk" --show-sdk-build-version)"
if [ -f "$prefix/stamp" ] && [ "$(cat "$prefix/stamp")" = "$stamp" ] &&
   [ -f "$prefix/lib/libssh2.a" ] && [ -f "$prefix/lib/libcrypto.a" ] &&
   [ -f "$prefix/include/libssh2.h" ] &&
   [ -f "$prefix/Notices/libssh2.txt" ] && [ -f "$prefix/Notices/OpenSSL.txt" ]; then
    exit 0
fi

fetch "https://github.com/openssl/openssl/releases/download/$openssl/$openssl.tar.gz" "$openssl.tar.gz" \
    603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a
fetch "https://libssh2.org/download/$libssh.tar.gz" "$libssh.tar.gz" \
    d9ec76cbe34db98eec3539fe2c899d26b0c837cb3eb466a56b0f109cabf658f7
work="$(mktemp -d "$root/build.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
tar -xf "$root/$openssl.tar.gz" -C "$work"
tar -xf "$root/$libssh.tar.gz" -C "$work"
jobs="$(sysctl -n hw.logicalcpu)"
(
    cd "$work/$openssl"
    ./Configure "$target" "$minimum" no-apps no-shared no-tests no-module --prefix="$prefix" --libdir=lib
    make -j "$jobs" build_libs
    make install_dev
)
cmake -S "$work/$libssh" -B "$work/ssh-build" \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.10 \
    -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT="$sdk" -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=17.0 -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$prefix" \
    -DCRYPTO_BACKEND=OpenSSL -DOPENSSL_ROOT_DIR="$prefix" -DOPENSSL_USE_STATIC_LIBS=ON \
    -DOPENSSL_CRYPTO_LIBRARY="$prefix/lib/libcrypto.a" -DOPENSSL_SSL_LIBRARY="$prefix/lib/libssl.a" \
    -DOPENSSL_INCLUDE_DIR="$prefix/include" -DBUILD_SHARED_LIBS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_TESTING=OFF
cmake --build "$work/ssh-build" --parallel "$jobs"
cmake --install "$work/ssh-build"
mkdir -p "$prefix/Notices"
cp "$work/$openssl/LICENSE.txt" "$prefix/Notices/OpenSSL.txt"
cp "$work/$libssh/COPYING" "$prefix/Notices/libssh2.txt"
printf '%s\n' "$stamp" > "$prefix/stamp"
