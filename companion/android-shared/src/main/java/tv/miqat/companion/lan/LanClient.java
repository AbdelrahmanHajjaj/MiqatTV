package tv.miqat.companion.lan;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.KeyStore;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** The TV's scanned certificate is the trust anchor for this client only. */
public final class LanClient {
    public static final class Result {
        public final int status;public final JSONObject body;
        Result(int status,JSONObject body){this.status=status;this.body=body;}
    }
    private final Endpoint endpoint;
    private final LanConfig config;
    private final SSLSocketFactory factory;
    private final X509Certificate certificate;
    /** Fetch only the certificate; the scanned fingerprint must validate before any application data. */
    public static String certificate(Endpoint endpoint,LanConfig config)throws Exception {
        SSLContext tls=SSLContext.getInstance("TLS");
        javax.net.ssl.X509TrustManager pinned=new javax.net.ssl.X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String type)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("Not a client authority");}
            public void checkServerTrusted(X509Certificate[] chain,String type)throws java.security.cert.CertificateException{
                try{
                    if(chain==null||chain.length!=1||!Pairing.equal(endpoint.pin,Pairing.sha256(chain[0].getEncoded())))throw new java.security.cert.CertificateException("TV identity changed");
                    chain[0].checkValidity();chain[0].verify(chain[0].getPublicKey());
                }catch(Exception invalid){throw new java.security.cert.CertificateException("TV certificate does not match scanned identity",invalid);}
            }
        };
        tls.init(null,new javax.net.ssl.TrustManager[]{pinned},null);
        URL url=new URL(endpoint.url);
        try(java.net.Socket raw=new java.net.Socket(Proxy.NO_PROXY)){
            raw.connect(new java.net.InetSocketAddress(url.getHost(),url.getPort()),config.timeoutMillis);raw.setSoTimeout(config.timeoutMillis);
            try(javax.net.ssl.SSLSocket socket=(javax.net.ssl.SSLSocket)tls.getSocketFactory().createSocket(raw,url.getHost(),url.getPort(),true)){
                java.util.List<String> protocols=new java.util.ArrayList<>();
                for(String protocol:socket.getSupportedProtocols())if(protocol.equals("TLSv1.2")||protocol.equals("TLSv1.3"))protocols.add(protocol);
                socket.setEnabledProtocols(protocols.toArray(new String[0]));socket.setSoTimeout(config.timeoutMillis);socket.startHandshake();
                byte[] der=socket.getSession().getPeerCertificates()[0].getEncoded();
                if(!Pairing.equal(endpoint.pin,Pairing.sha256(der)))throw new java.security.cert.CertificateException("TV identity changed");
                return Base64.getEncoder().encodeToString(der);
            }
        }
    }
    public LanClient(Endpoint endpoint,LanConfig config,String encodedCertificate)throws Exception {
        this.endpoint=endpoint;this.config=config;SSLContext tls=SSLContext.getInstance("TLS");
        if(encodedCertificate.length()>4096)throw new IllegalArgumentException("TV certificate too large");
        byte[] der=Base64.getDecoder().decode(encodedCertificate);
        if(!Base64.getEncoder().encodeToString(der).equals(encodedCertificate) || !Pairing.equal(endpoint.pin,Pairing.sha256(der)))
            throw new java.security.cert.CertificateException("TV identity changed");
        try(ByteArrayInputStream in=new ByteArrayInputStream(der)) {
            certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);
            if(in.available()!=0)throw new java.security.cert.CertificateException("Invalid certificate encoding");
        }
        if(!java.util.Arrays.equals(der,certificate.getEncoded()))throw new java.security.cert.CertificateException("Noncanonical TV certificate");
        certificate.checkValidity();certificate.verify(certificate.getPublicKey());
        KeyStore anchors=KeyStore.getInstance(KeyStore.getDefaultType());anchors.load(null);anchors.setCertificateEntry("tv",certificate);
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(anchors);
        tls.init(null,trust.getTrustManagers(),null);factory=tls.getSocketFactory();
    }
    public Result call(String method,String path,JSONObject body,String credential)throws Exception {
        certificate.checkValidity();
        if(!(method.equals("GET")||method.equals("POST")) || !path.matches("/v1/[a-z-]+") || method.equals("POST")!=(body!=null))throw new IllegalArgumentException();
        HttpsURLConnection connection=(HttpsURLConnection)new URL(endpoint.url+path).openConnection(Proxy.NO_PROXY);
        try {
            connection.setSSLSocketFactory(factory);
            connection.setHostnameVerifier((host,session)->{
                try{return Pairing.equal(endpoint.pin,Pairing.sha256(session.getPeerCertificates()[0].getEncoded()));}catch(Exception e){return false;}
            });
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setConnectTimeout(config.timeoutMillis);connection.setReadTimeout(config.timeoutMillis);
            connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Connection","close");
            if(credential!=null) {
                if(!credential.matches("[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid credential");
                connection.setRequestProperty("Authorization","Bearer "+credential);
            }
            if(body!=null) {
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                if(bytes.length>config.maxBodyBytes)throw new IOException("Request too large");
                connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(bytes.length);
                try(java.io.OutputStream out=connection.getOutputStream()){out.write(bytes);}
            }
            int status=connection.getResponseCode();
            if(status>=300 && status<400)throw new IOException("TV redirect refused");
            if(!"application/json".equalsIgnoreCase(connection.getContentType()))throw new IOException("Invalid TV response type");
            if(connection.getContentLengthLong()>config.maxResponseBytes)throw new IOException("TV response too large");
            try(InputStream input=status>=400?connection.getErrorStream():connection.getInputStream()) {
                if(input==null)throw new IOException("Missing TV response");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(config.timeoutMillis);
                while((count=input.read(buffer))!=-1) {
                    if(System.nanoTime()>=deadline || bytes.size()+count>config.maxResponseBytes)throw new IOException("TV response limit exceeded");bytes.write(buffer,0,count);
                }
                return new Result(status,Json.read(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
            }
        } finally{connection.disconnect();}
    }
}
