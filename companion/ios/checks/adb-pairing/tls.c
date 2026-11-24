#include "../../adb-spake.h"
#include "../../adb-tls.h"
#include <assert.h>
#include <openssl/bio.h>
#include <openssl/digest.h>
#include <openssl/evp.h>
#include <openssl/mem.h>
#include <openssl/rsa.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>
#include <stdio.h>
#include <string.h>

static EVP_PKEY *fixture_key(void) {
    EVP_PKEY_CTX *ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_RSA, NULL);
    EVP_PKEY *key = NULL;
    assert(ctx && EVP_PKEY_keygen_init(ctx) && EVP_PKEY_CTX_set_rsa_keygen_bits(ctx, 2048));
    assert(EVP_PKEY_keygen(ctx, &key) == 1);
    EVP_PKEY_CTX_free(ctx);
    return key;
}

static MiqatAdbTLS *fixture_tls(EVP_PKEY *key) {
    uint8_t *der = NULL;
    int size = i2d_PrivateKey(key, &der);
    assert(size > 0);
    MiqatAdbTLS *session = miqat_adb_tls_new(der, (size_t)size);
    OPENSSL_cleanse(der, (size_t)size);
    OPENSSL_free(der);
    assert(session);
    return session;
}

static int accept_fixture_certificate(int preverified, X509_STORE_CTX *context) {
    (void)preverified;
    (void)context;
    return 1;
}

static void connect_fixture(MiqatAdbTLS *client, SSL *server) {
    BIO *client_bio = NULL, *server_bio = NULL;
    assert(BIO_new_bio_pair(&client_bio, 0, &server_bio, 0));
    SSL_set_bio((SSL *)client, client_bio, client_bio);
    SSL_set_bio(server, server_bio, server_bio);
    SSL_set_accept_state(server);
    SSL_set_verify(server, SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT, accept_fixture_certificate);
    for (int attempt = 0; attempt < 32; attempt++) {
        int state = miqat_adb_tls_connect(client);
        assert(state == 1 || state == MIQAT_TLS_READ || state == MIQAT_TLS_WRITE);
        int result = SSL_do_handshake(server);
        int error = SSL_get_error(server, result);
        assert(result == 1 || error == SSL_ERROR_WANT_READ || error == SSL_ERROR_WANT_WRITE);
        if (state == 1 && result == 1) {
            return;
        }
    }
    assert(!"TLS fixture handshake did not finish");
}

int main(void) {
    EVP_PKEY *client_key = fixture_key(), *server_key = fixture_key();
    MiqatAdbTLS *client = fixture_tls(client_key), *server_session = fixture_tls(server_key);
    SSL *server = (SSL *)server_session;
    assert(!miqat_adb_tls_new(NULL, 0));
    uint8_t client_password[70] = "012345", server_password[70] = "012345";
    assert(!miqat_adb_tls_export(client, client_password + 6));
    connect_fixture(client, server);
    assert(SSL_version(server) == TLS1_3_VERSION);
    X509 *presented = SSL_get_peer_certificate(server);
    EVP_PKEY *presented_key = X509_get_pubkey(presented);
    assert(presented_key && EVP_PKEY_cmp(presented_key, client_key) == 1);
    EVP_PKEY_free(presented_key);
    X509_free(presented);

    uint8_t digest[32], expected[32];
    unsigned int digest_size = 0;
    assert(miqat_adb_tls_peer_digest(client, digest));
    assert(X509_digest(SSL_get_certificate(server), EVP_sha256(), expected, &digest_size));
    assert(digest_size == sizeof(expected) && memcmp(digest, expected, sizeof(expected)) == 0);

    static const char label[] = "adb-label";
    assert(miqat_adb_tls_export(client, client_password + 6));
    assert(SSL_export_keying_material(server, server_password + 6, 64, label, sizeof(label), NULL, 0, 0));
    assert(memcmp(client_password, server_password, 70) == 0);

    uint8_t a[32], b[32], received[32], client_shared[64], server_shared[64];
    MiqatAdbSpake *alice = miqat_adb_spake_begin(0, client_password, 70, a);
    MiqatAdbSpake *bob = miqat_adb_spake_begin(1, server_password, 70, b);
    assert(alice && bob);
    assert(miqat_adb_tls_write(client, a, sizeof(a)) == sizeof(a));
    assert(SSL_read(server, received, sizeof(received)) == sizeof(received));
    assert(miqat_adb_spake_finish(bob, received, server_shared));
    assert(SSL_write(server, b, sizeof(b)) == sizeof(b));
    assert(miqat_adb_tls_read(client, received, sizeof(received)) == sizeof(received));
    assert(miqat_adb_spake_finish(alice, received, client_shared));

    uint8_t peer[8192] = {1}, sealed[8208], over_tls[8208], confirmed[8192];
    memcpy(peer + 1, "adb-fixture", sizeof("adb-fixture"));
    assert(miqat_adb_peer_seal(server_shared, peer, sealed));
    assert(SSL_write(server, sealed, sizeof(sealed)) == sizeof(sealed));
    assert(miqat_adb_tls_read(client, over_tls, sizeof(over_tls)) == sizeof(over_tls));
    assert(miqat_adb_peer_open(client_shared, over_tls, confirmed));
    assert(memcmp(peer, confirmed, sizeof(peer)) == 0);

    miqat_adb_spake_free(alice);
    miqat_adb_spake_free(bob);
    miqat_adb_tls_free(client);
    miqat_adb_tls_free(server_session);
    EVP_PKEY_free(client_key);
    EVP_PKEY_free(server_key);
    puts("ADB TLS 1.3 client certificate, exporter, peer digest and pairing records verified.");
}
