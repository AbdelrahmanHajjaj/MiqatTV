#ifndef MIQAT_ADB_SPAKE_H
#define MIQAT_ADB_SPAKE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MiqatAdbSpake MiqatAdbSpake;

MiqatAdbSpake *miqat_adb_spake_begin(int server, const uint8_t *password, size_t password_size,
                                    uint8_t message[32]);
int miqat_adb_spake_finish(MiqatAdbSpake *session, const uint8_t peer[32], uint8_t key[64]);
void miqat_adb_spake_free(MiqatAdbSpake *session);
int miqat_adb_peer_seal(const uint8_t key[64], const uint8_t peer[8192], uint8_t sealed[8208]);
int miqat_adb_peer_open(const uint8_t key[64], const uint8_t sealed[8208], uint8_t peer[8192]);

#ifdef __cplusplus
}
#endif

#endif
