package tv.miqat.companion;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.miqat.companion.lan.Json;
import tv.miqat.companion.lan.Pairing;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Phone credentials are encrypted together; cached settings never enter this store. */
final class Phones {
    static final class Phone {
        final String url,pin,certificate,id,secret,name,profile;
        final boolean pending;
        Phone(String url,String pin,String certificate,String id,String secret,String name,String profile,boolean pending) {
            if(!pin.matches("[0-9a-f]{64}") || !Pairing.tokenShape(id) || !Pairing.tokenShape(secret) || name.trim().isEmpty() || name.length()>64 ||
                !Arrays.asList("","android","lg").contains(profile) || certificate.length()>4096 || url.length()>80)throw new IllegalArgumentException("Invalid paired TV");
            this.url=url;this.pin=pin;this.certificate=certificate;this.id=id;this.secret=secret;this.name=name;this.profile=profile;this.pending=pending;
        }
        String credential(){return id+"."+secret;}
        JSONObject json()throws Exception{return new JSONObject().put("url",url).put("pin",pin).put("certificate",certificate).put("id",id).put("secret",secret).put("name",name).put("profile",profile).put("pending",pending);}
        static Phone read(JSONObject value)throws Exception {
            Json.keys(value,"url","pin","certificate","id","secret","name","profile","pending");
            if(!(value.get("pending") instanceof Boolean))throw new IllegalArgumentException("Invalid grant state");
            return new Phone(Json.string(value,"url"),Json.string(value,"pin"),Json.string(value,"certificate"),Json.string(value,"id"),Json.string(value,"secret"),Json.string(value,"name"),Json.string(value,"profile"),value.getBoolean("pending"));
        }
    }
    private static final String ALIAS="companion-phone-credentials";
    // Activities overlap during recreation; AtomicFile supplies no lock.
    private static final Object LOCK=new Object();
    private static final byte[] AAD="Miqat paired TVs v1".getBytes(StandardCharsets.UTF_8);
    private final AtomicFile file;
    Phones(Context context){file=new AtomicFile(new File(context.getFilesDir(),"paired-tvs.bin"));}
    private SecretKey key(boolean create)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            if(!create)throw new java.security.KeyStoreException("Paired TV key unavailable");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    List<Phone> read()throws Exception {synchronized(LOCK){return load();}}
    private List<Phone> load()throws Exception {
        byte[] data;
        try{data=file.readFully();}catch(FileNotFoundException absent){if(file.getBaseFile().exists())throw absent;return new ArrayList<>();}
        if(data.length<30 || data.length>262144 || data[0]!=1)throw new IllegalStateException("Invalid paired TV store");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Arrays.copyOfRange(data,1,13)));cipher.updateAAD(AAD);
        JSONObject plain=Json.read(new String(cipher.doFinal(data,13,data.length-13),StandardCharsets.UTF_8));Json.keys(plain,"phones");
        JSONArray entries=plain.getJSONArray("phones");if(entries.length()>64)throw new IllegalStateException("Paired TV store too large");
        List<Phone> phones=new ArrayList<>();Set<String> pins=new HashSet<>();
        for(int i=0;i<entries.length();i++){Phone phone=Phone.read(entries.getJSONObject(i));if(!pins.add(phone.pin))throw new IllegalStateException("Duplicate paired TV");phones.add(phone);}
        return phones;
    }
    void put(Phone phone)throws Exception {synchronized(LOCK){
        List<Phone> phones=load();int index=-1;
        for(int i=0;i<phones.size();i++)if(phones.get(i).pin.equals(phone.pin))index=i;
        if(index>=0&&phone.pending&&!phones.get(index).pending&&phones.get(index).id.equals(phone.id))return;
        if(index<0){if(phones.size()>=64)throw new IllegalStateException("Paired TV limit reached");phones.add(phone);}else phones.set(index,phone);
        write(phones);
    }}
    void remove(Phone expected)throws Exception {synchronized(LOCK){
        List<Phone> phones=load();
        for(int i=0;i<phones.size();i++)if(phones.get(i).pin.equals(expected.pin)) {
            Phone current=phones.get(i);
            if(!Pairing.equal(current.id,expected.id)||!Pairing.equal(current.secret,expected.secret))throw new IllegalStateException("TV grant changed");
            phones.remove(i);write(phones);return;
        }
    }}
    Phone update(Phone expected,java.util.function.UnaryOperator<Phone> change)throws Exception {synchronized(LOCK){
        List<Phone> phones=load();
        for(int i=0;i<phones.size();i++)if(phones.get(i).pin.equals(expected.pin)) {
            Phone current=phones.get(i);if(!Pairing.equal(current.id,expected.id)||!Pairing.equal(current.secret,expected.secret))throw new IllegalStateException("TV grant changed");
            Phone next=change.apply(current);
            if(!next.pin.equals(current.pin)||!next.id.equals(current.id)||!Pairing.equal(next.secret,current.secret))throw new IllegalArgumentException("Metadata cannot replace credentials");
            if(next!=current){phones.set(i,next);write(phones);}return next;
        }
        throw new IllegalStateException("TV grant unavailable");
    }}
    private void write(List<Phone> phones)throws Exception {
        JSONArray entries=new JSONArray();for(Phone item:phones)entries.put(item.json());
        byte[] plain=Json.object("phones",entries).toString().getBytes(StandardCharsets.UTF_8);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(AAD);
        byte[] encrypted=cipher.doFinal(plain),iv=cipher.getIV();
        if(iv.length!=12 || encrypted.length+13>262144)throw new IllegalStateException("Invalid encrypted TV store size");
        FileOutputStream out=file.startWrite();
        try{out.write(1);out.write(iv);out.write(encrypted);out.getFD().sync();file.finishWrite(out);}catch(Exception error){file.failWrite(out);throw error;}
    }
}
