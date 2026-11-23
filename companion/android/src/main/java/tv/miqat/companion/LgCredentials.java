package tv.miqat.companion;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Remembered Developer Mode passphrases are bound to the approved SSH identity. */
final class LgCredentials {
    private static final String ALIAS = "miqat-lg-installation";
    private final SharedPreferences prefs;

    LgCredentials(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    boolean has(String host) {
        return prefs.contains("lg-phrase-" + host);
    }

    byte[] read(String host, String pin) throws Exception {
        String value = prefs.getString("lg-phrase-" + host, "");
        if (value.isEmpty()) return new byte[0];
        byte[] data = Base64.getDecoder().decode(value);
        if (data.length < 28) throw new IllegalStateException("Invalid LG credential");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, data, 0, 12));
        cipher.updateAAD((host + ":" + pin).getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(data, 12, data.length - 12);
    }

    void save(String host, String pin, byte[] phrase) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        cipher.updateAAD((host + ":" + pin).getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(phrase);
        if (iv.length != 12) throw new IllegalStateException("Invalid LG credential IV");
        byte[] data = Arrays.copyOf(iv, iv.length + encrypted.length);
        System.arraycopy(encrypted, 0, data, iv.length, encrypted.length);
        if (!prefs.edit().putString("lg-phrase-" + host, Base64.getEncoder().encodeToString(data)).commit())
            throw new IllegalStateException("Could not save LG credential");
    }

    void forget(String host) {
        prefs.edit().remove("lg-phrase-" + host).remove("lg-key-" + host).apply();
    }

    void clearPassphrase(String host) {
        if (has(host) && !prefs.edit().remove("lg-phrase-" + host).commit())
            throw new IllegalStateException("Could not clear LG credential");
    }

    private static synchronized SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            if (!create) throw new IllegalStateException("LG credential key unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
}
