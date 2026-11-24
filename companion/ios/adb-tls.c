#include "adb-tls.h"
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>

static int tls_result(SSL *ssl, int result) {
    if (result > 0) {
        return result;
    }
    switch (SSL_get_error(ssl, result)) {
        case SSL_ERROR_WANT_READ: return MIQAT_TLS_READ;
        case SSL_ERROR_WANT_WRITE: return MIQAT_TLS_WRITE;
        default: return MIQAT_TLS_FAILED;
    }
}

static X509 *client_certificate(EVP_PKEY *key) {
    X509 *cert = X509_new();
    uint8_t serial[20];
    if (!cert || !RAND_bytes(serial, sizeof(serial))) {
        X509_free(cert);
        return NULL;
    }
    // Serial numbers must be positive. The random tail keeps certificates distinct.
    serial[0] = 1;
    X509_NAME *name = X509_get_subject_name(cert);
    if (!X509_set_version(cert, 2)
        || !ASN1_STRING_set(X509_get_serialNumber(cert), serial, sizeof(serial))
        || !X509_time_adj_ex(X509_getm_notBefore(cert), -1, 0, NULL)
        || !X509_time_adj_ex(X509_getm_notAfter(cert), 365, 0, NULL)
        || !name || !X509_NAME_add_entry_by_txt(name, "CN", MBSTRING_ASC,
                                               (const uint8_t *)"Miqat iPhone ADB", -1, -1, 0)
        || !X509_set_issuer_name(cert, name)
        || !X509_set_pubkey(cert, key)
        || X509_sign(cert, key, EVP_sha256()) <= 0) {
        X509_free(cert);
        return NULL;
    }
    return cert;
}

MiqatAdbTLS *miqat_adb_tls_new(const uint8_t *private_key, size_t key_size) {
    if (!private_key || key_size == 0 || key_size > 4096) {
        return NULL;
    }
    const uint8_t *cursor = private_key;
    EVP_PKEY *key = d2i_AutoPrivateKey(NULL, &cursor, (long)key_size);
    if (!key || cursor != private_key + key_size || EVP_PKEY_id(key) != EVP_PKEY_RSA
        || EVP_PKEY_bits(key) != 2048) {
        EVP_PKEY_free(key);
        return NULL;
    }

    X509 *cert = client_certificate(key);
    SSL_CTX *context = cert ? SSL_CTX_new(TLS_method()) : NULL;
    SSL *tls = NULL;
    if (context && SSL_CTX_set_min_proto_version(context, TLS1_3_VERSION)
        && SSL_CTX_set_max_proto_version(context, TLS1_3_VERSION)
        && SSL_CTX_use_certificate(context, cert)
        && SSL_CTX_use_PrivateKey(context, key)) {
        // Android's pairing and debugging certificates are distinct and self-signed.
        // Callers must authenticate pairing with its TLS exporter, or verify a debug pin.
        SSL_CTX_set_verify(context, SSL_VERIFY_NONE, NULL);
        tls = SSL_new(context);
    }
    SSL_CTX_free(context);
    X509_free(cert);
    EVP_PKEY_free(key);
    return (MiqatAdbTLS *)tls;
}

void miqat_adb_tls_free(MiqatAdbTLS *session) {
    SSL_free((SSL *)session);
}

int miqat_adb_tls_set_fd(MiqatAdbTLS *session, int fd) {
    return session && fd >= 0 && SSL_set_fd((SSL *)session, fd);
}

int miqat_adb_tls_connect(MiqatAdbTLS *session) {
    if (!session) {
        return MIQAT_TLS_FAILED;
    }
    SSL *tls = (SSL *)session;
    ERR_clear_error();
    return tls_result(tls, SSL_connect(tls));
}

int miqat_adb_tls_export(MiqatAdbTLS *session, uint8_t output[64]) {
    static const char exporter[] = "adb-label";
    SSL *tls = (SSL *)session;
    // ADB passes sizeof(label), including the NUL, to the TLS exporter.
    return tls && output && SSL_is_init_finished(tls)
        && SSL_export_keying_material(tls, output, 64, exporter, sizeof(exporter), NULL, 0, 0);
}

int miqat_adb_tls_peer_digest(MiqatAdbTLS *session, uint8_t output[32]) {
    SSL *tls = (SSL *)session;
    X509 *cert = tls && output && SSL_is_init_finished(tls) ? SSL_get_peer_certificate(tls) : NULL;
    unsigned int length = 0;
    const int ok = cert && X509_digest(cert, EVP_sha256(), output, &length) && length == 32;
    X509_free(cert);
    return ok;
}

int miqat_adb_tls_read(MiqatAdbTLS *session, uint8_t *bytes, int length) {
    if (!session || !bytes || length <= 0) {
        return MIQAT_TLS_FAILED;
    }
    SSL *tls = (SSL *)session;
    ERR_clear_error();
    return tls_result(tls, SSL_read(tls, bytes, length));
}

int miqat_adb_tls_write(MiqatAdbTLS *session, const uint8_t *bytes, int length) {
    if (!session || !bytes || length <= 0) {
        return MIQAT_TLS_FAILED;
    }
    SSL *tls = (SSL *)session;
    ERR_clear_error();
    return tls_result(tls, SSL_write(tls, bytes, length));
}
