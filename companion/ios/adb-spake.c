#include "adb-spake.h"
#include <openssl/aead.h>
#include <openssl/curve25519.h>
#include <openssl/digest.h>
#include <openssl/hkdf.h>
#include <openssl/mem.h>

MiqatAdbSpake *miqat_adb_spake_begin(int server, const uint8_t *password, size_t password_size,
                                    uint8_t message[32]) {
    static const uint8_t client_name[] = "adb pair client";
    static const uint8_t server_name[] = "adb pair server";
    if (!password || password_size != 70 || !message) {
        return NULL;
    }

    // ADB includes each role name's terminating NUL in the transcript.
    SPAKE2_CTX *ctx = server
        ? SPAKE2_CTX_new(spake2_role_bob, server_name, sizeof(server_name), client_name, sizeof(client_name))
        : SPAKE2_CTX_new(spake2_role_alice, client_name, sizeof(client_name), server_name, sizeof(server_name));
    size_t used = 0;
    if (!ctx || !SPAKE2_generate_msg(ctx, message, &used, 32, password, password_size) || used != 32) {
        SPAKE2_CTX_free(ctx);
        return NULL;
    }
    return (MiqatAdbSpake *)ctx;
}

int miqat_adb_spake_finish(MiqatAdbSpake *session, const uint8_t peer[32], uint8_t key[64]) {
    size_t used = 0;
    return session && peer && key
        && SPAKE2_process_msg((SPAKE2_CTX *)session, key, &used, 64, peer, 32) && used == 64;
}

void miqat_adb_spake_free(MiqatAdbSpake *session) {
    SPAKE2_CTX_free((SPAKE2_CTX *)session);
}

static EVP_AEAD_CTX *peer_cipher(const uint8_t key[64]) {
    static const uint8_t label[] = "adb pairing_auth aes-128-gcm key";
    uint8_t derived[16];
    EVP_AEAD_CTX *cipher = NULL;
    if (HKDF(derived, sizeof(derived), EVP_sha256(), key, 64, NULL, 0, label, sizeof(label) - 1)) {
        cipher = EVP_AEAD_CTX_new(EVP_aead_aes_128_gcm(), derived, sizeof(derived), 16);
    }
    OPENSSL_cleanse(derived, sizeof(derived));
    return cipher;
}

// ADB sends exactly one authenticated peer-info message in each direction.
int miqat_adb_peer_seal(const uint8_t key[64], const uint8_t peer[8192], uint8_t sealed[8208]) {
    if (!key || !peer || !sealed) {
        return 0;
    }
    EVP_AEAD_CTX *cipher = peer_cipher(key);
    uint8_t nonce[12] = {0};
    size_t used = 0;
    const int ok = cipher
        && EVP_AEAD_CTX_seal(cipher, sealed, &used, 8208, nonce, sizeof(nonce), peer, 8192, NULL, 0)
        && used == 8208;
    EVP_AEAD_CTX_free(cipher);
    return ok;
}

int miqat_adb_peer_open(const uint8_t key[64], const uint8_t sealed[8208], uint8_t peer[8192]) {
    if (!key || !sealed || !peer) {
        return 0;
    }
    EVP_AEAD_CTX *cipher = peer_cipher(key);
    uint8_t nonce[12] = {0};
    size_t used = 0;
    const int ok = cipher
        && EVP_AEAD_CTX_open(cipher, peer, &used, 8192, nonce, sizeof(nonce), sealed, 8208, NULL, 0)
        && used == 8192;
    EVP_AEAD_CTX_free(cipher);
    return ok;
}
