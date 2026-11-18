package tv.miqat.companion.lan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One bounded JSON request per TLS connection. No proxy, streaming or browser routes. */
public final class Http {
    private Http() {}
    public static final class Request {
        public final String method,path,body;
        public final Map<String,String> headers;
        Request(String method,String path,String body,Map<String,String> headers) {
            this.method=method;this.path=path;this.body=body;this.headers=headers;
        }
    }
    public static Request read(InputStream input,int maximumBody) throws IOException {
        int[] budget={8192};
        String first=line(input,budget);
        if(!first.matches("(GET|POST) /v1/[a-z-]+ HTTP/1\\.[01]"))throw new IOException("Invalid request line");
        String[] parts=first.split(" ");Map<String,String> headers=new LinkedHashMap<>();
        for(String line;(line=line(input,budget)).length()>0;) {
            int split=line.indexOf(':');
            if(split<=0 || !line.substring(0,split).matches("[A-Za-z0-9-]+"))throw new IOException("Invalid header");
            String key=line.substring(0,split).toLowerCase(Locale.ROOT),value=line.substring(split+1).trim();
            if(headers.put(key,value)!=null)throw new IOException("Duplicate header");
        }
        if(!headers.containsKey("host") || headers.get("host").isEmpty() || headers.containsKey("origin") ||
            headers.containsKey("transfer-encoding") || headers.containsKey("expect"))throw new IOException("Unsupported request");
        String length=headers.get("content-length");int count=0;
        if(length!=null) {
            if(!length.matches("0|[1-9][0-9]{0,8}"))throw new IOException("Invalid length");
            count=Integer.parseInt(length);
        }
        if(count>maximumBody || parts[0].equals("GET")&&count!=0)throw new IOException("Invalid body length");
        if(parts[0].equals("POST") && (length==null || !"application/json".equalsIgnoreCase(headers.get("content-type"))))
            throw new IOException("JSON required");
        byte[] body=new byte[count];int offset=0;
        while(offset<count){int n=input.read(body,offset,count-offset);if(n<0)throw new IOException("Truncated body");offset+=n;}
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
        return new Request(parts[0],parts[1],text,headers);
    }
    private static String line(InputStream input,int[] budget)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();int b;
        while((b=input.read())!=-1) {
            if(--budget[0]<0)throw new IOException("Headers too large");
            if(b==13){if(input.read()!=10 || --budget[0]<0)throw new IOException("Invalid line ending");return out.toString("US-ASCII");}
            if(b<32 || b>126)throw new IOException("Invalid header character");out.write(b);
        }
        throw new IOException("Truncated headers");
    }
}
