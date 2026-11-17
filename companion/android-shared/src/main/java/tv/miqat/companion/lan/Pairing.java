package tv.miqat.companion.lan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Transport credentials only; launcher preferences never enter this store. */
public final class Pairing {
    public interface Store {
        List<Client> read() throws Exception;
        void write(List<Client> clients) throws Exception;
    }
    public static final class Client {
        public final String id, name, hash;
        public Client(String id, String name, String hash) {
            if (!tokenShape(id) || !validName(name) || !hash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid stored phone");
            this.id=id; this.name=name; this.hash=hash;
        }
    }
    public static final class Pending {
        public final String name, nonce, code;
        Pending(String name, String nonce, String code) { this.name=name; this.nonce=nonce; this.code=code; }
    }
    public static final class Grant {
        public final String id, secret;
        Grant(String id, String secret) { this.id=id; this.secret=secret; }
    }
    private final SecureRandom random=new SecureRandom();
    private final Store store;
    private final long windowMillis;
    private final int maxPhones;
    private List<Client> clients;
    private String window="";
    private long deadline;
    private Pending pending;
    private Grant grant;

    public Pairing(Store store, long windowMillis, int maxPhones) throws Exception {
        if (windowMillis<=0 || maxPhones<=0) throw new IllegalArgumentException();
        this.store=store; this.windowMillis=windowMillis; this.maxPhones=maxPhones;
        clients=new ArrayList<>(store.read());
        if (clients.size()>maxPhones) throw new IllegalArgumentException("Too many stored phones");
        for (int i=0;i<clients.size();i++) for (int j=0;j<i;j++)
            if (clients.get(i).id.equals(clients.get(j).id)) throw new IllegalArgumentException("Duplicate phone");
    }
    /** Callers supply a monotonic clock, never a wall-clock timestamp. */
    public synchronized String open(long now) {
        if (clients.size()>=maxPhones) throw new IllegalStateException("Phone limit reached");
        clear(); window=token(); deadline=now+windowMillis; return window;
    }
    public synchronized void close() { clear(); }
    public synchronized boolean isOpen(long now) { expire(now); return !window.isEmpty(); }
    public synchronized Pending pending(long now) { expire(now); return pending; }
    public synchronized boolean approved(long now) { expire(now); return grant!=null; }
    public synchronized Pending request(String window, String name, String nonce, long now) {
        checkWindow(window,now);
        if (!validName(name) || !tokenShape(nonce)) throw new IllegalArgumentException("Invalid pairing request");
        if (pending!=null) {
            if (!equal(pending.nonce,nonce) || !pending.name.equals(name)) throw new IllegalStateException("Pairing busy");
            return pending;
        }
        pending=new Pending(name,nonce,code(window,nonce)); return pending;
    }
    /** Only the trusted TV UI calls this method. It has no network route. */
    public synchronized void approve(String nonce, long now) {
        expire(now);
        if (pending==null || !equal(pending.nonce,nonce)) throw new IllegalStateException("No matching request");
        if (grant==null) grant=new Grant(token(),token());
    }
    public synchronized void reject(String nonce, long now) {
        expire(now);
        if (pending==null || !equal(pending.nonce,nonce)) throw new IllegalStateException("No matching request");
        clear();
    }
    public synchronized Grant poll(String window, String nonce, long now) {
        checkWindow(window,now);
        if (pending==null || !equal(pending.nonce,nonce)) throw new SecurityException("Unknown request");
        return grant;
    }
    /** Persist only after the phone possesses its grant; failures leave the exchange retryable. */
    public synchronized void finish(String id, String secret, long now) throws Exception {
        expire(now);
        if (grant==null || !equal(grant.id,id) || !equal(grant.secret,secret)) throw new SecurityException("Unknown grant");
        List<Client> next=new ArrayList<>(clients);
        next.add(new Client(id,pending.name,hash(secret)));
        store.write(new ArrayList<>(next)); clients=next; clear();
    }
    public synchronized boolean authenticate(String id, String secret) {
        if (!tokenShape(id) || !tokenShape(secret)) return false;
        String digest=hash(secret);
        for (Client client:clients) if (equal(client.id,id) && equal(client.hash,digest)) return true;
        return false;
    }
    public synchronized List<Client> clients() { return new ArrayList<>(clients); }
    public synchronized void revoke(String id) throws Exception {
        List<Client> next=new ArrayList<>(clients);
        if (!next.removeIf(client->equal(client.id,id))) throw new IllegalArgumentException("Unknown phone");
        store.write(new ArrayList<>(next)); clients=next;
    }
    private void checkWindow(String candidate,long now) {
        expire(now);
        if (window.isEmpty() || !tokenShape(candidate) || !equal(window,candidate)) throw new SecurityException("Pairing expired");
    }
    private void expire(long now) { if (!window.isEmpty() && now>=deadline) clear(); }
    private void clear() { window=""; pending=null; grant=null; deadline=0; }
    public String token() { byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static boolean tokenShape(String token) { return token!=null && token.matches("[A-Za-z0-9_-]{43}"); }
    private static boolean validName(String name) {
        if (name==null || name.trim().isEmpty() || name.length()>64) return false;
        for (int i=0;i<name.length();i++) if (Character.isISOControl(name.charAt(i)) || Character.getType(name.charAt(i))==Character.FORMAT) return false;
        return true;
    }
    public static String hash(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }
    public static String sha256(byte[] value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static String code(String window,String nonce) {
        if(!tokenShape(window)||!tokenShape(nonce))throw new IllegalArgumentException();
        return String.format(java.util.Locale.ROOT,"%06d",Long.parseLong(hash(window+":"+nonce).substring(0,8),16)%1000000);
    }
    public static boolean equal(String a,String b) {
        return a!=null && b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    }
}
