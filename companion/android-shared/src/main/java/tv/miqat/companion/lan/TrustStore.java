package tv.miqat.companion.lan;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TrustStore implements Pairing.Store {
    private final AtomicFile file;
    public TrustStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"companion-phones.json"));}
    @Override public List<Pairing.Client> read()throws Exception {
        List<Pairing.Client> clients=new ArrayList<>();byte[] bytes;
        try{bytes=file.readFully();}catch(FileNotFoundException absent){if(file.getBaseFile().exists())throw absent;return clients;}
        if(bytes.length>32768)throw new IllegalStateException("Invalid phone store");
        JSONObject root=Json.read(new String(bytes,StandardCharsets.UTF_8));Json.keys(root,"phones");
        JSONArray saved=root.getJSONArray("phones");
        for(int i=0;i<saved.length();i++) {
            JSONObject item=saved.getJSONObject(i);Json.keys(item,"id","name","hash");
            clients.add(new Pairing.Client(Json.string(item,"id"),Json.string(item,"name"),Json.string(item,"hash")));
        }
        return clients;
    }
    @Override public void write(List<Pairing.Client> clients)throws Exception {
        JSONArray saved=new JSONArray();
        for(Pairing.Client client:clients)saved.put(new JSONObject().put("id",client.id).put("name",client.name).put("hash",client.hash));
        byte[] bytes=Json.object("phones",saved).toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream out=file.startWrite();
        try{out.write(bytes);out.getFD().sync();file.finishWrite(out);}catch(Exception error){file.failWrite(out);throw error;}
    }
}
