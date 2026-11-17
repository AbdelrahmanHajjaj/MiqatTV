package tv.miqat.companion;

import android.content.Context;
import android.content.ContextWrapper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import tv.miqat.companion.lan.*;

/** Separate encrypted test store; never removes the phone app's real records/key. */
final class PhoneFixture implements AutoCloseable {
    final File directory,file;
    final Phones store;
    final Context isolated;
    PhoneFixture(Context context)throws Exception {
        directory=Files.createTempDirectory(context.getCacheDir().toPath(),"phone-vault-").toFile();file=new File(directory,"paired-tvs.bin");
        isolated=new ContextWrapper(context){@Override public File getFilesDir(){return directory;}};store=new Phones(isolated);
    }
    void verify(TlsIdentity identity,String url,Pairing pairing)throws Exception {
        Phones.Phone phone=new Phones.Phone(url,identity.pin,identity.encodedCertificate(),pairing.token(),pairing.token(),"Fixture TV","android",true);
        store.put(phone);byte[] first=Files.readAllBytes(file.toPath());
        LanCheck.check(store.read().size()==1&&store.read().get(0).secret.equals(phone.secret));
        LanCheck.check(first[0]==1&&!new String(first,java.nio.charset.StandardCharsets.ISO_8859_1).contains(phone.secret));
        store.put(phone);byte[] second=Files.readAllBytes(file.toPath());LanCheck.check(!Arrays.equals(first,second));
        LanCheck.check(file.setReadable(false,false));
        try{LanCheck.rejects(store::read);LanCheck.rejects(()->store.put(phone));}finally{LanCheck.check(file.setReadable(true,true));}
        LanCheck.check(Arrays.equals(second,Files.readAllBytes(file.toPath())));
        byte[] tampered=second.clone();tampered[tampered.length-1]^=1;write(tampered);
        LanCheck.rejects(store::read);LanCheck.rejects(()->store.put(phone));LanCheck.check(Arrays.equals(tampered,Files.readAllBytes(file.toPath())));
        write(second);LanCheck.check(store.read().get(0).id.equals(phone.id));
        store.update(phone,current->new Phones.Phone(current.url,current.pin,current.certificate,current.id,current.secret,"Renamed TV",current.profile,false));
        store.put(phone);LanCheck.check(!store.read().get(0).pending&&store.read().get(0).name.equals("Renamed TV"));
        LanCheck.rejects(()->store.update(phone,current->new Phones.Phone(current.url,current.pin,current.certificate,pairing.token(),current.secret,current.name,current.profile,false)));
        Phones another=new Phones(isolated);java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        Thread[] writers=new Thread[2];
        for(int i=0;i<writers.length;i++) {
            final int index=i;writers[i]=new Thread(()->{try{start.await();Phones writer=index==0?store:another;
                Phones.Phone extra=new Phones.Phone(url,Pairing.hash("fixture-"+index),phone.certificate,pairing.token(),pairing.token(),"Concurrent fixture "+index,"android",false);
                for(int j=0;j<4;j++)writer.put(extra);
            }catch(Throwable failure){error.set(failure);}});writers[i].start();
        }
        start.countDown();for(Thread writer:writers){writer.join(12000);LanCheck.check(!writer.isAlive());}
        if(error.get()!=null)throw new AssertionError(error.get());LanCheck.check(store.read().size()==3&&another.read().size()==3);
        TrustStore tvStore=new TrustStore(isolated);tvStore.write(java.util.Collections.singletonList(new Pairing.Client(phone.id,"Fixture",Pairing.hash(phone.secret))));
        File tvFile=new File(directory,"companion-phones.json");LanCheck.check(tvFile.setReadable(false,false));
        try{LanCheck.rejects(tvStore::read);LanCheck.rejects(()->new Pairing(tvStore,300000,16));}finally{LanCheck.check(tvFile.setReadable(true,true));}
        LanCheck.check(tvStore.read().get(0).id.equals(phone.id));
        if(!file.delete())throw new IllegalStateException("Fixture cleanup failed");
    }
    private void write(byte[] bytes)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}}
    @Override public void close()throws Exception {for(File child:directory.listFiles())if(!child.delete())throw new IllegalStateException("Fixture cleanup failed");if(!directory.delete())throw new IllegalStateException("Fixture directory cleanup failed");}
}
