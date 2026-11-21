package tv.miqat.companion;

import com.jcraft.jsch.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Foreground Developer Mode transport. No root, arbitrary commands or saved SSH private keys. */
final class LgInstall implements AutoCloseable {
    private final JSch ssh=new JSch();
    private volatile Session session;
    private volatile boolean closed;
    static final int PORT=9922;
    static final class Trust extends IOException {
        final String key;
        Trust(String key){super("Confirm the TV SSH identity");this.key=key;}
    }
    static String host(String value)throws IOException {
        if(value==null||!value.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}"))throw new IOException("Use the TV's private IPv4 address");
        for(String part:value.split("\\."))if(Integer.parseInt(part)>255)throw new IOException("Invalid address");
        if(!InetAddress.getByName(value).isSiteLocalAddress())throw new IOException("Home-network address required");return value;
    }
    static String fingerprint(String key)throws Exception {
        return "SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(key)));
    }
    void connect(String address,String expectedKey,byte[] passphrase)throws Exception {
        host(address);final byte[][] observed={null};
        byte[] expected=expectedKey.isEmpty()?null:Base64.getDecoder().decode(expectedKey);
        ssh.setHostKeyRepository(new HostKeyRepository(){
            public int check(String name,byte[] key){observed[0]=key.clone();return expected==null?NOT_INCLUDED:MessageDigest.isEqual(expected,key)?OK:CHANGED;}
            public void add(HostKey key,UserInfo user){}
            public void remove(String host,String type){}
            public void remove(String host,String type,byte[] key){}
            public String getKnownHostsRepositoryID(){return "Miqat LG setup";}
            public HostKey[] getHostKey(){return new HostKey[0];}
            public HostKey[] getHostKey(String host,String type){return new HostKey[0];}
        });
        if(expected!=null) {
            byte[] key=downloadKey(address);
            try{ssh.addIdentity("LG Developer Mode",key,null,passphrase);}finally{Arrays.fill(key,(byte)0);}
        }
        Session next=ssh.getSession("prisoner",address,PORT);session=next;
        next.setConfig("StrictHostKeyChecking","yes");
        next.setConfig("PreferredAuthentications","publickey");
        // Old LG Developer Mode SSH needs these algorithms; ordinary companion TLS is unchanged.
        next.setConfig("server_host_key",JSch.getConfig("server_host_key")+",ssh-rsa");
        next.setConfig("PubkeyAcceptedAlgorithms",JSch.getConfig("PubkeyAcceptedAlgorithms")+",ssh-rsa");
        next.setConfig("kex",JSch.getConfig("kex")+",diffie-hellman-group14-sha1,diffie-hellman-group1-sha1");
        next.setTimeout(30000);
        try{if(closed)throw new IOException("Setup closed");next.connect(15000);}
        catch(Exception failure) {
            next.disconnect();
            if(expected==null&&observed[0]!=null)throw new Trust(Base64.getEncoder().encodeToString(observed[0]));
            throw failure;
        } finally {Arrays.fill(passphrase,(byte)0);}
    }
    private static byte[] downloadKey(String host)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://"+host+":9991/webos_rsa").openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(10000);
        try {
            if(c.getResponseCode()!=200)throw new IOException("Enable Key Server in LG Developer Mode");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("Invalid LG key");out.write(b,0,n);}}
            byte[] key=out.toByteArray();String text=new String(key,StandardCharsets.US_ASCII);
            if(!text.startsWith("-----BEGIN ")||!text.contains("PRIVATE KEY-----"))throw new IOException("Invalid LG key server response");return key;
        } finally {c.disconnect();}
    }
    String installedVersion()throws Exception {
        JSONObject result=luna("com.webos.applicationManager/dev/listApps",new JSONObject().put("subscribe",false),false);
        JSONArray apps=result.optJSONArray("apps");if(apps==null)throw new IOException("LG did not return its app list");
        for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);if(LgPackage.ID.equals(app.optString("id")))return app.getString("version");}return "";
    }
    void launch()throws Exception {luna("com.webos.applicationManager/launch",new JSONObject().put("id",LgPackage.ID),false);}
    void install(LgPackage pkg)throws Exception {
        LgPackage checked=LgPackage.read(pkg.file);
        if(!checked.version.equals(pkg.version))throw new IOException("Selected IPK changed");
        String current=installedVersion();
        if(!current.isEmpty()&&LgPackage.compareVersions(pkg.version,current)<0)throw new IOException("Downgrade refused to protect TV settings");
        String remote="/media/developer/temp/miqat-"+UUID.randomUUID()+".ipk";boolean finished=false;
        command("mkdir -p /media/developer/temp",null,false);
        try {
            command("cat > "+remote,pkg.file,false);
            String md5=command("md5sum "+remote,null,false).trim().split("\\s+",2)[0];
            MessageDigest digest=MessageDigest.getInstance("MD5");
            try(InputStream in=new FileInputStream(pkg.file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!hash.toString().equals(md5))throw new IOException("LG package transfer did not match");
            luna("com.webos.appInstallService/dev/install",new JSONObject().put("id",LgPackage.ID).put("ipkUrl",remote).put("subscribe",true),true);
            if(!pkg.version.equals(installedVersion()))throw new IOException("LG has not confirmed the selected version; check status before retrying");
            finished=true;
        } finally {if(finished)try{command("rm -f "+remote,null,false);}catch(Exception ignored){/* Installed app is verified; only our temporary package remains. */}}
    }
    private JSONObject luna(String endpoint,JSONObject params,boolean installation)throws Exception {
        String raw=command("/usr/bin/luna-send-pub "+(installation?"-i ":"-n 1 ")+"luna://"+endpoint+" '"+params.toString().replace("'","'\\''")+"'",null,installation);
        List<JSONObject> frames=frames(raw);if(frames.isEmpty())throw new IOException("LG returned no complete response");
        JSONObject result=frames.get(frames.size()-1);if(!result.optBoolean("returnValue",false))throw new IOException("LG refused the operation");return result;
    }
    private String command(String command,File input,boolean installation)throws Exception {
        Session s=session;if(closed||s==null||!s.isConnected())throw new IOException("Reconnect to LG Developer Mode");
        ChannelExec channel=(ChannelExec)s.openChannel("exec");
        try(InputStream file=input==null?null:new FileInputStream(input)) {
            channel.setCommand(command);channel.setInputStream(file);
            InputStream in=channel.getInputStream();InputStream errors=channel.getErrStream();
            channel.connect(15000);ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(180);
            byte[] b=new byte[4096];
            while(true) {
                for(InputStream stream:new InputStream[]{in,errors})while(stream.available()>0){int n=stream.read(b);if(n<0)break;ByteArrayOutputStream dest=stream==in?out:err;if(dest.size()+n>1048576)throw new IOException("LG response too large");dest.write(b,0,n);}
                String raw=new String(out.toByteArray(),StandardCharsets.UTF_8);
                if(installation)for(JSONObject frame:frames(raw)) {
                    if(frame.has("returnValue")&&!frame.optBoolean("returnValue"))throw new IOException("LG refused package installation");
                    String state=frame.optJSONObject("details")==null?"":frame.getJSONObject("details").optString("state");
                    if(state.toUpperCase(Locale.ROOT).contains("FAILED"))throw new IOException("LG package installation failed");
                    if(state.equalsIgnoreCase("installed")||state.toUpperCase(Locale.ROOT).startsWith("SUCCESS"))return raw;
                }
                if(channel.isClosed()&&in.available()==0){if(channel.getExitStatus()!=0||installation)throw new IOException("LG command was not confirmed; check Developer Mode and installed status");return raw;}
                if(closed||Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("LG operation interrupted; check installed status before retrying");
                Thread.sleep(20);
            }
        } finally {channel.disconnect();}
    }
    static List<JSONObject> frames(String value)throws Exception {
        List<JSONObject> out=new ArrayList<>();int start=-1,depth=0;boolean quoted=false,escape=false;
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(start<0){if(Character.isWhitespace(c))continue;if(c!='{')throw new IOException("Invalid LG response");start=i;depth=1;continue;}
            if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')quoted=false;}
            else if(c=='\"')quoted=true;else if(c=='{'||c=='[')depth++;else if((c=='}'||c==']')&&--depth==0){out.add(new JSONObject(value.substring(start,i+1)));start=-1;}
            if(depth>32)throw new IOException("LG response nesting too deep");
        }
        return out;
    }
    @Override public void close(){closed=true;Session s=session;if(s!=null)s.disconnect();try{ssh.removeAllIdentity();}catch(JSchException ignored){/* Session is already closed. */}}
}
