package tv.miqat.companion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Inspect the IPK control record without extracting or executing package contents. */
final class LgPackage {
    static final String ID = "tv.miqat.webos";
    static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final String VERSION="[0-9]{1,8}(?:\\.[0-9]{1,8}){1,3}";
    final String version;
    final File file;
    private LgPackage(File file, String version) { this.file=file;this.version=version; }

    static LgPackage read(File file) throws IOException {
        if (file.length()<128 || file.length()>MAX_BYTES) throw new IOException("Invalid IPK size");
        String control=null;boolean data=false;Set<String> members=new HashSet<>();
        try (InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            if (!new String(exact(in,8),StandardCharsets.US_ASCII).equals("!<arch>\n")) throw new IOException("Not an IPK");
            while (true) {
                int first=in.read();if(first<0)break;
                byte[] header=new byte[60];header[0]=(byte)first;readFully(in,header,1,59);
                String name=new String(header,0,16,StandardCharsets.US_ASCII).trim();
                if(name.endsWith("/"))name=name.substring(0,name.length()-1);
                if(header[58]!='`'||header[59]!='\n'||!members.add(name))throw new IOException("Invalid IPK member");
                long size=number(new String(header,48,10,StandardCharsets.US_ASCII).trim(),10,MAX_BYTES);
                if(name.equals("control.tar.gz")) {
                    if(size>1024*1024)throw new IOException("IPK control too large");
                    control=control(exact(in,(int)size));
                } else {
                    if(name.equals("data.tar.gz"))data=true;
                    skip(in,size);
                }
                if((size&1)!=0 && in.read()<0)throw new EOFException("Truncated IPK padding");
            }
        }
        if(control==null||!data)throw new IOException("IPK needs gzip control and data members");
        Map<String,String> fields=new HashMap<>();
        for(String line:control.split("\n")) {
            int colon=line.indexOf(':');if(colon<1)continue;
            String key=line.substring(0,colon),value=line.substring(colon+1).trim();
            if(fields.put(key,value)!=null)throw new IOException("Duplicate IPK field");
        }
        String version=fields.get("Version");
        if(!ID.equals(fields.get("Package"))||version==null||!version.matches(VERSION))
            throw new IOException("Choose a Miqat webOS IPK");
        return new LgPackage(file,version);
    }
    static int compareVersions(String left,String right)throws IOException {
        if(!left.matches(VERSION)||!right.matches(VERSION))throw new IOException("Invalid Miqat version");
        String[] a=left.split("\\."),b=right.split("\\.");
        for(int i=0;i<Math.max(a.length,b.length);i++){
            int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0;
            if(x!=y)return Integer.compare(x,y);
        }
        return 0;
    }

    private static String control(byte[] compressed) throws IOException {
        try(InputStream in=new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            long total=0;String control=null;
            while(true) {
                byte[] header=exact(in,512);total+=512;
                if(header[0]==0)break;
                String name=new String(header,0,100,StandardCharsets.US_ASCII).split("\u0000",2)[0];
                long size=number(new String(header,124,12,StandardCharsets.US_ASCII).replace("\u0000","").trim(),8,65536);
                long padded=((size+511)/512)*512;total+=padded;
                if(total>1024*1024)throw new IOException("IPK control expands too far");
                if(name.equals("control")||name.equals("./control")) {
                    if(control!=null)throw new IOException("Duplicate control record");
                    control=new String(exact(in,(int)size),StandardCharsets.UTF_8);skip(in,padded-size);
                } else skip(in,padded);
            }
            if(control==null)throw new IOException("IPK control record missing");return control;
        }
    }
    private static long number(String value,int radix,long max)throws IOException {
        try{long n=Long.parseLong(value,radix);if(n<0||n>max)throw new NumberFormatException();return n;}
        catch(NumberFormatException error){throw new IOException("Invalid archive length",error);}
    }
    private static byte[] exact(InputStream in,int count)throws IOException {byte[] out=new byte[count];readFully(in,out,0,count);return out;}
    private static void readFully(InputStream in,byte[] out,int offset,int count)throws IOException {
        while(count>0){int n=in.read(out,offset,count);if(n<0)throw new EOFException("Truncated IPK");offset+=n;count-=n;}
    }
    private static void skip(InputStream in,long count)throws IOException {
        byte[] buffer=new byte[8192];while(count>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,count));if(n<0)throw new EOFException("Truncated IPK");count-=n;}
    }
}
