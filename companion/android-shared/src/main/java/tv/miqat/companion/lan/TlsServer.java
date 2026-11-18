package tv.miqat.companion.lan;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public final class TlsServer implements AutoCloseable {
    public interface Handler { Response handle(Http.Request request)throws Exception; }
    public static final class Response {
        public final int status;public final String json;
        public Response(int status,String json){this.status=status;this.json=json;}
    }
    private final SSLServerSocket listener;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final Set<SSLSocket> sockets=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public TlsServer(TlsIdentity identity,LanConfig config,Handler handler,boolean allowLoopback)throws Exception {
        listener=(SSLServerSocket)identity.context().getServerSocketFactory().createServerSocket();
        try {
            listener.setReuseAddress(true);
            listener.setEnabledProtocols(intersection(listener.getSupportedProtocols(),"TLSv1.3","TLSv1.2"));
            listener.setEnabledCipherSuites(intersection(listener.getSupportedCipherSuites(),"TLS_AES_128_GCM_SHA256","TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256","TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"));
            listener.bind(new InetSocketAddress(config.port),8);
        } catch(Exception e){listener.close();workers.shutdownNow();throw e;}
        Thread accept=new Thread(()->{
            while(!closed) {
                SSLSocket socket=null;
                try {
                    socket=(SSLSocket)listener.accept();InetAddress address=socket.getInetAddress();
                    if(!(address instanceof Inet4Address) || !(address.isSiteLocalAddress() || allowLoopback&&address.isLoopbackAddress())){socket.close();continue;}
                    socket.setSoTimeout(config.timeoutMillis);sockets.add(socket);
                    final SSLSocket accepted=socket;final long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.timeoutMillis);
                    workers.execute(()->serve(accepted,deadline,config,handler));
                } catch(Exception error) {
                    if(socket!=null){sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
                    else if(!closed)close();
                }
            }
        },"companion-lan-accept");accept.start();
    }
    private static String[] intersection(String[] available,String... wanted) {
        ArrayList<String> out=new ArrayList<>();for(String value:wanted)if(Arrays.asList(available).contains(value))out.add(value);
        if(out.isEmpty())throw new IllegalStateException("Secure TLS unavailable");return out.toArray(new String[0]);
    }
    private void serve(SSLSocket socket,long deadline,LanConfig config,Handler handler) {
        try(SSLSocket owned=socket) {
            InputStream input=new FilterInputStream(owned.getInputStream()) {
                private void remaining()throws IOException {
                    long millis=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
                    if(millis<=0)throw new java.net.SocketTimeoutException();owned.setSoTimeout((int)millis);
                }
                @Override public int read()throws IOException{remaining();return in.read();}
                @Override public int read(byte[] b,int offset,int length)throws IOException{remaining();return in.read(b,offset,length);}
            };
            Response response;
            try {response=handler.handle(Http.read(input,config.maxBodyBytes));}
            catch(SecurityException denied){response=new Response(403,"{\"error\":\"denied\"}");}
            catch(IllegalArgumentException|IOException|org.json.JSONException malformed){response=new Response(400,"{\"error\":\"invalid\"}");}
            catch(Exception failed){response=new Response(500,"{\"error\":\"failed\"}");}
            byte[] body=response.json.getBytes(StandardCharsets.UTF_8);
            if(body.length>config.maxResponseBytes){response=new Response(500,"");body="{}".getBytes(StandardCharsets.UTF_8);}
            byte[] header=("HTTP/1.1 "+response.status+" Result\r\nContent-Type: application/json\r\nContent-Length: "+body.length+
                "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            // A non-reading peer must not hold either worker indefinitely.
            java.util.concurrent.ScheduledFuture<?> timeout=writeTimeout.schedule(()->{try{owned.close();}catch(IOException ignored){}},config.timeoutMillis,TimeUnit.MILLISECONDS);
            try{owned.getOutputStream().write(header);owned.getOutputStream().write(body);owned.getOutputStream().flush();}finally{timeout.cancel(false);}
        } catch(Exception ignored) {
            // Invalid peers and disconnected clients have no effect on launcher state.
        } finally {sockets.remove(socket);}
    }
    private final java.util.concurrent.ScheduledThreadPoolExecutor writeTimeout=new java.util.concurrent.ScheduledThreadPoolExecutor(1);
    {writeTimeout.setRemoveOnCancelPolicy(true);}
    @Override public void close() {
        closed=true;try{listener.close();}catch(IOException ignored){}
        for(SSLSocket socket:sockets)try{socket.close();}catch(IOException ignored){}
        workers.shutdownNow();writeTimeout.shutdownNow();
    }
}
