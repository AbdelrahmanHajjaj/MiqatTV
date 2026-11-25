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
        return read("lg-phrase-", host, pin);
    }

    private byte[] read(String slot, String host, String pin) throws Exception {
        String value = prefs.getString(slot + host, "");
        if (value.isEmpty()) return new byte[0];
        byte[] data = Base64.getDecoder().decode(value);
        if (data.length < 28) throw new IllegalStateException("Invalid LG credential");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, data, 0, 12));
        cipher.updateAAD((host + ":" + pin).getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(data, 12, data.length - 12);
    }

    void save(String host, String pin, byte[] phrase, byte[] privateKey) throws Exception {
        if (!prefs.edit()
                .putString("lg-phrase-" + host, encrypt(host, pin, phrase))
                .putString("lg-private-" + host, encrypt(host, "private:" + pin, privateKey))
                .commit()) throw new IllegalStateException("Could not save LG credentials");
    }

    private String encrypt(String host, String pin, byte[] secret) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        cipher.updateAAD((host + ":" + pin).getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(secret);
        if (iv.length != 12) throw new IllegalStateException("Invalid LG credential IV");
        byte[] data = Arrays.copyOf(iv, iv.length + encrypted.length);
        System.arraycopy(encrypted, 0, data, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(data);
    }

    void forget(String host) {
        prefs.edit().remove("lg-phrase-" + host).remove("lg-private-" + host).remove("lg-key-" + host).apply();
    }

    void clearPassphrase(String host) {
        if (has(host) && !prefs.edit().remove("lg-phrase-" + host).remove("lg-private-" + host).commit())
            throw new IllegalStateException("Could not clear LG credential");
    }

    void connectSaved(LgInstall transport, String host, String pin) throws Exception {
        if (pin.isEmpty() || !has(host)) throw new java.io.IOException("No remembered LG installation identity");
        byte[] passphrase = read(host, pin);
        byte[] privateKey = new byte[0];
        try {
            privateKey = read("lg-private-", host, "private:" + pin);
            if (privateKey.length == 0) {
                privateKey = LgInstall.downloadKey(host);
                transport.connect(host, pin, passphrase, privateKey);
                if (!prefs.edit().putString("lg-private-" + host, encrypt(host, "private:" + pin, privateKey)).commit())
                    throw new IllegalStateException("Could not save LG installation key");
            } else transport.connect(host, pin, passphrase, privateKey);
        } finally {
            Arrays.fill(passphrase, (byte) 0);
            Arrays.fill(privateKey, (byte) 0);
        }
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
