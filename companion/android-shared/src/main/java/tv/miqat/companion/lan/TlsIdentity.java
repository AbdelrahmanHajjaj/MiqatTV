package tv.miqat.companion.lan;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;

public final class TlsIdentity {
    private final PrivateKey key;
    private final X509Certificate certificate;
    public final String pin;
    public String encodedCertificate()throws Exception{return java.util.Base64.getEncoder().encodeToString(certificate.getEncoded());}
    public TlsIdentity()throws Exception {
        String alias="companion-lan-identity";
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)) {
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN)
                // Conscrypt signs the TLS transcript digest itself.
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(new X500Principal("CN=Miqat"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(new Date(1577836800000L)).setCertificateNotAfter(new Date(2524608000000L)).build());
            generator.generateKeyPair();
        }
        key=(PrivateKey)store.getKey(alias,null);certificate=(X509Certificate)store.getCertificate(alias);
        certificate.verify(certificate.getPublicKey());certificate.checkValidity();pin=Pairing.sha256(certificate.getEncoded());
    }
    public SSLContext context()throws Exception {
        SSLContext context=SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{new X509ExtendedKeyManager(){
            public String[] getClientAliases(String type,Principal[] issuers){return null;}
            public String chooseClientAlias(String[] types,Principal[] issuers,Socket socket){return null;}
            public String[] getServerAliases(String type,Principal[] issuers){return "EC".equals(type)?new String[]{"tv"}:null;}
            public String chooseServerAlias(String type,Principal[] issuers,Socket socket){return "EC".equals(type)?"tv":null;}
            @Override public String chooseEngineServerAlias(String type,Principal[] issuers,SSLEngine engine){return "EC".equals(type)?"tv":null;}
            public X509Certificate[] getCertificateChain(String alias){return "tv".equals(alias)?new X509Certificate[]{certificate}:null;}
            public PrivateKey getPrivateKey(String alias){return "tv".equals(alias)?key:null;}
        }},null,null);return context;
    }
}
