package tv.miqat.companion;

import android.content.Context;
import android.media.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Imports only bounded local audio; settings reference an immutable private content identity. */
final class PhoneAudio {
    static final long MAX_BYTES=20L*1024*1024;
    private static File directory(Context context)throws IOException {
        File directory=new File(context.createDeviceProtectedStorageContext().getFilesDir(),"custom-audio");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create private audio directory");return directory;
    }
    static String slot(String id){
        if(id.equals("reminders.voice"))return "default";
        for(String prayer:PhoneStore.PRAYERS)if(!prayer.equals("sunrise")&&id.equals("call."+prayer+".voice"))return prayer;
        throw new IllegalArgumentException("Invalid audio slot");
    }
    static void importFile(Context context,InputStream input,String id)throws Exception {
        if(input==null)throw new IOException("No audio file");
        try(InputStream in=input){
        String slot=slot(id);
        File directory=directory(context),part=File.createTempFile("import-",".part",directory);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
            try(FileOutputStream out=new FileOutputStream(part)){
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import cancelled");count+=n;if(count>MAX_BYTES)throw new IOException("Audio exceeds 20 MB");digest.update(buffer,0,n);out.write(buffer,0,n);}out.getFD().sync();
            }
            MediaMetadataRetriever metadata=new MediaMetadataRetriever();
            try{
                metadata.setDataSource(part.getPath());
                long duration=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                if(duration<1||duration>600000||metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)==null||metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)!=null)throw new IOException("Not bounded audio-only media");
            }finally{metadata.release();}
            MediaPlayer decoder=new MediaPlayer();try{decoder.setDataSource(part.getPath());decoder.prepare();}finally{decoder.release();}
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.US,"%02x",b&255));
            synchronized(PhoneStore.LOCK){
                File file=new File(directory,hash+".audio");Files.move(part.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                PhoneStore current=PhoneStore.read(context);Map<String,String> edits=new LinkedHashMap<>();edits.put(id,"custom");edits.put("audio.custom."+slot,hash.toString());current.changed(edits).save(context);
                try{cleanup(context);}catch(Exception ignored){/* Committed audio remains selected; reclaim unused files on the next import. */}
            }
        }finally{Files.deleteIfExists(part.toPath());}
        }
    }
    static File file(Context c,PhoneStore settings,int prayer,boolean defaults)throws IOException {
        String slot=defaults||settings.value("call."+PhoneStore.PRAYERS[prayer]+".voice").equals("inherit")?"default":PhoneStore.PRAYERS[prayer];
        String hash=settings.value("audio.custom."+slot);if(!hash.matches("[a-f0-9]{64}"))throw new IOException("No imported audio");
        File file=new File(directory(c),hash+".audio");if(!file.isFile()||file.length()<1||file.length()>MAX_BYTES)throw new IOException("Private audio unavailable");return file;
    }
    static void cleanup(Context c)throws Exception {
        synchronized(PhoneStore.LOCK){
            PhoneStore settings=PhoneStore.read(c);Set<String> keep=new HashSet<>();for(Map.Entry<String,String> value:settings.values.entrySet())if(value.getKey().startsWith("audio.custom.")&&!value.getValue().isEmpty())keep.add(value.getValue()+".audio");
            File[] files=directory(c).listFiles();if(files==null)throw new IOException("Cannot read private audio directory");
            for(File file:files)if(file.getName().matches("[a-f0-9]{64}[.]audio")&&!keep.contains(file.getName()))Files.delete(file.toPath());
        }
    }
}
