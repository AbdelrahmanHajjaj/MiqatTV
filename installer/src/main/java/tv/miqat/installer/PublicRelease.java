package tv.miqat.installer;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Locale;

/** Anonymous downloads from this project's published GitHub releases only. */
public final class PublicRelease {
    private PublicRelease() {}
    private static final String REPO="AbdelrahmanHajjaj/MiqatTV";
    private static final String PREFIX="/"+REPO+"/releases/download/";
    private static final long MAX_BYTES=100L*1024*1024;
    public static final class Unavailable extends IOException { public Unavailable(){super("No published Miqat package is available. Try again later or choose a local file in Advanced.");} }
    public interface Progress { void on(long done,long total); }
    public static final class Asset {
        public final String name,tag;
        public final boolean preview;
        final String url,digest,sums;
        final long size;
        Asset(String name,String tag,boolean preview,String url,String digest,String sums,long size){this.name=name;this.tag=tag;this.preview=preview;this.url=url;this.digest=digest;this.sums=sums;this.size=size;}
        public File download(File directory,Progress progress)throws IOException {
            String expected=digest;
            if(expected.isEmpty())expected=checksum(text(sums,262144),name);
            HttpURLConnection connection=open(url,false);
            try(InputStream in=connection.getInputStream()){return save(in,directory,name,size,expected,progress);}
            finally{connection.disconnect();}
        }
    }
    public static Asset latest(boolean lg)throws IOException {
        // shortcut: inspect the newest 30 releases; revisit if platform releases diverge that far.
        return parse(text("https://api.github.com/repos/"+REPO+"/releases?per_page=30",2*1024*1024),lg);
    }
    static Asset parse(String text,boolean lg)throws IOException {
        try {
            JSONArray releases=new JSONArray(text);JSONObject chosen=null;String published="";
            for(int i=0;i<releases.length();i++){
                JSONObject release=releases.getJSONObject(i);String date=release.optString("published_at","");
                if(release.optBoolean("draft",true)||!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]{8}Z"))continue;
                JSONArray assets=release.getJSONArray("assets");boolean found=false;
                for(int j=0;j<assets.length();j++)if(packageName(assets.getJSONObject(j).getString("name"),lg))found=true;
                if(found&&(chosen==null||date.compareTo(published)>0)){chosen=release;published=date;}
            }
            if(chosen==null)throw new Unavailable();
            JSONArray assets=chosen.getJSONArray("assets");JSONObject pkg=null,sums=null;
            for(int i=0;i<assets.length();i++){
                JSONObject item=assets.getJSONObject(i);String name=item.getString("name");
                if(packageName(name,lg)){if(pkg!=null)throw new IOException("Ambiguous release packages");pkg=item;}
                if(name.equals("SHA256SUMS")){if(sums!=null)throw new IOException("Ambiguous checksums");sums=item;}
            }
            if(pkg==null)throw new Unavailable();
            long size=pkg.getLong("size");if(size<1024||size>MAX_BYTES||!pkg.getString("state").equals("uploaded"))throw new IOException("Invalid release package");
            String tag=chosen.getString("tag_name"),name=pkg.getString("name");
            String url=assetUrl(pkg,tag),sumUrl=sums==null?"":assetUrl(sums,tag);
            String digest=pkg.optString("digest","");if(digest.equals("null"))digest="";
            if(!digest.isEmpty()){if(!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("Invalid release digest");digest=digest.substring(7);}
            if(digest.isEmpty()&&sumUrl.isEmpty())throw new IOException("Release has no SHA-256 checksum");
            return new Asset(name,tag,chosen.getBoolean("prerelease"),url,digest,sumUrl,size);
        }catch(JSONException invalid){throw new IOException("Invalid release metadata",invalid);}
    }
    private static boolean packageName(String name,boolean lg){return lg?name.equals("miqat-webos.ipk")||name.matches("tv[.]miqat[.]webos_[0-9]+(?:[.][0-9]+)*_all[.]ipk"):name.equals("miqat.apk");}
    private static String assetUrl(JSONObject asset,String tag)throws IOException,JSONException {
        String value=asset.getString("browser_download_url");URL url=new URL(value);
        if(!url.getProtocol().equals("https")||!url.getHost().equals("github.com")||url.getPort()!=-1||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null)throw new IOException("Invalid asset source");
        try{if(!url.toURI().getPath().equals(PREFIX+tag+"/"+asset.getString("name")))throw new IOException("Asset belongs to another release");}
        catch(URISyntaxException invalid){throw new IOException("Invalid asset URL",invalid);}
        return value;
    }
    static String checksum(String sums,String name)throws IOException {
        String found=null;
        for(String line:sums.split("\\r?\\n")){
            if(line.matches("[a-fA-F0-9]{64} [ *].+" )&&line.substring(66).replaceFirst("^[.]/", "").equals(name)){
                if(found!=null)throw new IOException("Duplicate checksum");found=line.substring(0,64);
            }
        }
        if(found==null)throw new IOException("Package checksum is missing");return found;
    }
    static File save(InputStream in,File directory,String name,long size,String expected,Progress progress)throws IOException {
        if(!name.matches("[a-zA-Z0-9_.-]+")||size<1024||size>MAX_BYTES||!expected.matches("[a-fA-F0-9]{64}"))throw new IOException("Invalid download bounds");
        File part=File.createTempFile("miqat-download-",".part",directory),target=new File(directory,name);
        try {
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IOException(impossible);}
            long count=0;
            try(FileOutputStream out=new FileOutputStream(part)){
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");count+=n;if(count>size)throw new IOException("Package exceeds release size");digest.update(buffer,0,n);out.write(buffer,0,n);progress.on(count,size);}
                out.getFD().sync();
            }
            StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.US,"%02x",b&255));
            if(count!=size||!actual.toString().equalsIgnoreCase(expected))throw new IOException("Incomplete download or SHA-256 mismatch");
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return target;
        }finally{Files.deleteIfExists(part.toPath());}
    }
    private static String text(String url,int limit)throws IOException {
        HttpURLConnection c=open(url,url.startsWith("https://api.github.com/"));
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Release response too large");out.write(buffer,0,n);}return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    private static HttpURLConnection open(String address,boolean api)throws IOException {
        URL url=new URL(address);
        for(int redirects=0;redirects<=5;redirects++){
            String host=url.getHost();if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||url.getPort()!=-1||!(host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Untrusted download redirect");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Miqat-installer");
            if(api){c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");}
            int status;try{status=c.getResponseCode();}catch(IOException failure){c.disconnect();throw failure;}
            if(status==200)return c;
            String next=c.getHeaderField("Location");c.disconnect();
            if(status>=300&&status<400&&next!=null){url=new URL(url,next);continue;}
            throw new IOException("GitHub download unavailable ("+status+")");
        }
        throw new IOException("Too many download redirects");
    }
}
