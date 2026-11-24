#include "../../adb-spake.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static int same_key(const uint8_t client_password[70], const uint8_t server_password[70]) {
    uint8_t client_message[32], server_message[32], client_key[64], server_key[64];
    MiqatAdbSpake *client = miqat_adb_spake_begin(0, client_password, 70, client_message);
    MiqatAdbSpake *server = miqat_adb_spake_begin(1, server_password, 70, server_message);
    assert(client && server);
    assert(miqat_adb_spake_finish(client, server_message, client_key));
    assert(miqat_adb_spake_finish(server, client_message, server_key));
    const int equal = memcmp(client_key, server_key, 64) == 0;
    uint8_t client_info[8192] = {0}, verified[8192], sealed[8208];
    memcpy(client_info + 1, "fixture Miqat-iPhone", sizeof("fixture Miqat-iPhone"));
    assert(miqat_adb_peer_seal(client_key, client_info, sealed));
    assert(miqat_adb_peer_open(server_key, sealed, verified) == equal);
    if (equal) {
        assert(memcmp(client_info, verified, sizeof(client_info)) == 0);
        sealed[8207] ^= 1;
        assert(!miqat_adb_peer_open(server_key, sealed, verified));
    }
    assert(!miqat_adb_spake_finish(client, server_message, client_key));
    miqat_adb_spake_free(client);
    miqat_adb_spake_free(server);
    return equal;
}

int main(void) {
    static const uint8_t first_block[] = {
        0x48, 0xd1, 0x5b, 0xce, 0x1d, 0x35, 0x29, 0x08, 0xa9, 0xea, 0x08, 0xb8, 0x5e, 0x9a, 0x15, 0xd6
    };
    static const uint8_t tag[] = {
        0xf1, 0xab, 0x4a, 0xe5, 0xe2, 0x21, 0xd3, 0xa0, 0xce, 0x60, 0x14, 0xf7, 0x62, 0x68, 0x08, 0xc8
    };
    uint8_t key[64], peer[8192] = {0}, sealed[8208];
    for (size_t i = 0; i < sizeof(key); i++) {
        key[i] = (uint8_t)i;
    }
    assert(miqat_adb_peer_seal(key, peer, sealed));
    assert(memcmp(sealed, first_block, sizeof(first_block)) == 0);
    assert(memcmp(sealed + 8192, tag, sizeof(tag)) == 0);

    uint8_t password[70] = "012345";
    for (size_t i = 6; i < 70; i++) {
        password[i] = (uint8_t)(i * 3);
    }
    assert(same_key(password, password));

    uint8_t different_session[70];
    memcpy(different_session, password, 70);
    different_session[69] ^= 1;
    assert(!same_key(password, different_session));

    uint8_t different_code[70];
    memcpy(different_code, password, 70);
    different_code[0] = '1';
    assert(!same_key(password, different_code));

    uint8_t unused[32];
    assert(!miqat_adb_spake_begin(0, password, 69, unused));
    puts("BoringSSL ADB SPAKE2 and authenticated peer records verified.");
}
