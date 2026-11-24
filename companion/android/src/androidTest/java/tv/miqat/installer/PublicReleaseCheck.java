package tv.miqat.installer;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Deterministic native parser and interrupted-download checks; no network or TV. */
public final class PublicReleaseCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    interface Bad {void run()throws Exception;}
    private static void rejects(Bad bad)throws Exception{try{bad.run();}catch(IOException expected){return;}throw new AssertionError("Invalid release/download accepted");}
    private static JSONObject asset(String name,String digest)throws Exception{return new JSONObject().put("name",name).put("size",2048).put("state","uploaded").put("digest",digest).put("browser_download_url","https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/download/v1.0.0-staging.1/"+name);}
    private static JSONObject release(JSONArray assets)throws Exception{return new JSONObject().put("draft",false).put("prerelease",true).put("published_at","2026-11-19T10:00:00Z").put("tag_name","v1.0.0-staging.1").put("assets",assets);}
    public static void run(Context context)throws Exception {
        String hash="a".repeat(64);JSONObject apk=asset("miqat.apk","sha256:"+hash);JSONObject r=release(new JSONArray().put(apk));
        check(PublicRelease.parse(new JSONArray().put(r).toString(),false,"staging").staging,"Staging release not labelled");
        rejects(()->PublicRelease.parse(new JSONArray().put(r).toString(),false));
        JSONObject productionApk=asset("miqat.apk","sha256:"+hash).put("browser_download_url",
                "https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/download/v0.9.0/miqat.apk");
        JSONObject production=release(new JSONArray().put(productionApk)).put("prerelease",false)
                .put("tag_name","v0.9.0").put("published_at","2026-11-18T10:00:00Z");
        JSONObject oldPreview=release(new JSONArray().put(apk)).put("tag_name","v1.0.0-preview.1")
                .put("published_at","2026-11-20T10:00:00Z");
        String mixed=new JSONArray().put(production).put(r).put(oldPreview).toString();
        check(!PublicRelease.parse(mixed,false).staging,"Production must be the default");
        check(PublicRelease.parse(mixed,false,"staging").tag.equals("v1.0.0-staging.1"),"Staging must ignore old previews");
        rejects(()->PublicRelease.parse(mixed,false,"other"));
        rejects(()->PublicRelease.parse("[]",false));rejects(()->PublicRelease.parse("broken",false));
        r.put("draft",true);rejects(()->PublicRelease.parse(new JSONArray().put(r).toString(),false,"staging"));r.put("draft",false);
        apk.put("browser_download_url","https://github.com/another/project/releases/download/v1.0.0-staging.1/miqat.apk");rejects(()->PublicRelease.parse(new JSONArray().put(r).toString(),false,"staging"));
        apk.put("browser_download_url","http://github.com/AbdelrahmanHajjaj/MiqatTV/releases/download/v1.0.0-staging.1/miqat.apk");rejects(()->PublicRelease.parse(new JSONArray().put(r).toString(),false,"staging"));
        r.put("assets",new JSONArray().put(asset("miqat.apk","")));rejects(()->PublicRelease.parse(new JSONArray().put(r).toString(),false,"staging"));
        r.put("assets",new JSONArray().put(asset("miqat.apk","")).put(asset("SHA256SUMS","")));
        check(PublicRelease.parse(new JSONArray().put(r).toString(),false,"staging").digest.isEmpty(),"Checksums fallback unavailable");
        r.put("assets",new JSONArray().put(asset("tv.miqat.webos_0.2.28_all.ipk","sha256:"+hash)));
        check(PublicRelease.parse(new JSONArray().put(r).toString(),true,"staging").name.endsWith(".ipk"),"Versioned LG asset unavailable");
        r.getJSONArray("assets").put(asset("tv.miqat.webos_0.2.29_all.ipk","sha256:"+hash));rejects(()->PublicRelease.parse(new JSONArray().put(r).toString(),true,"staging"));
        check(PublicRelease.checksum(hash+"  ./miqat.apk\n","miqat.apk").equals(hash),"sha256sum path prefix");
        rejects(()->PublicRelease.checksum(hash+"  miqat.apk\n"+hash+" *miqat.apk","miqat.apk"));
        rejects(()->PublicRelease.checksum(hash+"  other.apk","miqat.apk"));
        File directory=Files.createTempDirectory(context.getCacheDir().toPath(),"release-check-").toFile();
        byte[] bytes=new byte[2048];new Random(7).nextBytes(bytes);StringBuilder digest=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format(Locale.US,"%02x",b&255));
        File target=new File(directory,"miqat.apk");byte[] previous={1,2,3};Files.write(target.toPath(),previous);
        try {
            rejects(()->PackageImport.copy(new ByteArrayInputStream(bytes),target,4096,file->{throw new IOException("Invalid package");}));
            check(Arrays.equals(previous,Files.readAllBytes(target.toPath())),"Invalid local package replaced the previous selection");
            rejects(()->PackageImport.copy(new ByteArrayInputStream(bytes),target,1024,file->{}));
            check(Arrays.equals(previous,Files.readAllBytes(target.toPath())),"Oversized local package replaced the previous selection");
            InputStream interrupted=new InputStream(){@Override public int read()throws IOException{throw new InterruptedIOException("Lost provider");}};
            rejects(()->PackageImport.copy(interrupted,target,4096,file->{}));
            check(Arrays.equals(previous,Files.readAllBytes(target.toPath())),"Interrupted local provider destroyed the selection");
            check(directory.list().length==1,"Local selection leaked a partial package");
            // Exercise Android's real APK parser before the atomic import publishes its temp file.
            File archive=new File(directory,"archive.apk");
            PackageImport.copy(new FileInputStream(context.getApplicationInfo().sourceDir),archive,100L*1024*1024,file->{
                android.content.pm.PackageInfo info=context.getPackageManager().getPackageArchiveInfo(file.getPath(),0);
                if(info==null||!context.getPackageName().equals(info.packageName))throw new IOException("Android rejected the staged APK");
            });
            check(archive.isFile()&&archive.length()>1024,"Native APK import was not committed");Files.delete(archive.toPath());
            try{Thread.currentThread().interrupt();rejects(()->PublicRelease.save(new ByteArrayInputStream(bytes),directory,"miqat.apk",bytes.length,digest.toString(),(done,total)->{}));}finally{Thread.interrupted();}
            check(Arrays.equals(previous,Files.readAllBytes(target.toPath())),"Cancelled download destroyed the previous package");
            rejects(()->PublicRelease.save(new ByteArrayInputStream(bytes),directory,"miqat.apk",bytes.length,hash,(done,total)->{}));
            check(Arrays.equals(previous,Files.readAllBytes(target.toPath())),"Bad digest destroyed previous file");
            rejects(()->PublicRelease.save(new ByteArrayInputStream(Arrays.copyOf(bytes,1000)),directory,"miqat.apk",bytes.length,digest.toString(),(done,total)->{}));
            check(Arrays.equals(previous,Files.readAllBytes(target.toPath())),"Truncation destroyed previous file");
            rejects(()->PublicRelease.save(new ByteArrayInputStream(bytes),directory,"../escape",bytes.length,digest.toString(),(done,total)->{}));
            List<Long> progress = new ArrayList<>();
            InputStream chunks = new ByteArrayInputStream(bytes) {
                @Override public synchronized int read(byte[] buffer, int offset, int length) {
                    return super.read(buffer, offset, Math.min(length, 512));
                }
            };
            PublicRelease.save(chunks,directory,"miqat.apk",bytes.length,digest.toString(),(done,total)->{
                check(total == bytes.length, "Progress must use the release size");
                progress.add(done);
            });
            check(progress.equals(Arrays.asList(512L, 1024L, 1536L, 2048L)), "Download progress must track bytes copied");
            check(Arrays.equals(bytes,Files.readAllBytes(target.toPath())),"Verified bytes did not replace file");
            check(directory.list().length==1,"Partial download leaked");
        }finally{for(File file:Objects.requireNonNull(directory.listFiles()))Files.delete(file.toPath());Files.delete(directory.toPath());}
    }
}
