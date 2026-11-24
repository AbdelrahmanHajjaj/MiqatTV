#ifndef MIQAT_ADB_TLS_H
#define MIQAT_ADB_TLS_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MiqatAdbTLS MiqatAdbTLS;

enum {
    MIQAT_TLS_FAILED = 0,
    MIQAT_TLS_READ = -2,
    MIQAT_TLS_WRITE = -3
};

MiqatAdbTLS *miqat_adb_tls_new(const uint8_t *private_key, size_t key_size);
void miqat_adb_tls_free(MiqatAdbTLS *session);
int miqat_adb_tls_set_fd(MiqatAdbTLS *session, int fd);
int miqat_adb_tls_connect(MiqatAdbTLS *session);
int miqat_adb_tls_export(MiqatAdbTLS *session, uint8_t output[64]);
int miqat_adb_tls_peer_digest(MiqatAdbTLS *session, uint8_t output[32]);
int miqat_adb_tls_read(MiqatAdbTLS *session, uint8_t *bytes, int length);
int miqat_adb_tls_write(MiqatAdbTLS *session, const uint8_t *bytes, int length);

#ifdef __cplusplus
}
#endif

#endif
