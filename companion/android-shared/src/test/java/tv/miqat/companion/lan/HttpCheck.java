package tv.miqat.companion.lan;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class HttpCheck {
    static Http.Request read(String request)throws Exception{return Http.read(new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)),64);}
    static void denied(String request)throws Exception{try{read(request);}catch(IOException expected){return;}throw new AssertionError("Accepted invalid HTTP");}
    public static void main(String[] args)throws Exception {
        String get="GET /v1/settings HTTP/1.1\r\nHost: localhost\r\n\r\n";
        if(!read(get).path.equals("/v1/settings"))throw new AssertionError();
        String post="POST /v1/pair HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: ";
        if(!read(post+"2\r\n\r\n{}").body.equals("{}"))throw new AssertionError();
        denied(get.replace("\r\n","\n"));denied(get.replace("/v1/settings","https://host/v1/settings"));
        denied(get.replace("/v1/settings","/v1/settings?secret=a"));denied(get.replace("Host: localhost\r\n",""));
        denied(get.replace("Host: localhost","Host: localhost\r\nOrigin: https://attacker"));
        denied(get.replace("Host: localhost","Host: a\r\nhost: b"));
        denied(post+"2\r\nTransfer-Encoding: chunked\r\n\r\n{}");
        denied(post+"-1\r\n\r\n{}");denied(post+"65\r\n\r\n{}");denied(post+"999999999999999999\r\n\r\n{}");
        denied(post+"3\r\n\r\n{}");denied(post+"2\r\nContent-Length: 2\r\n\r\n{}");
        denied(post.replace("application/json","text/plain")+"2\r\n\r\n{}");
        denied(get.replace("Host: localhost","Host: localhost\r\nX: "+"a".repeat(8192)));
        byte[] invalid=(post+"1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] bytes=java.util.Arrays.copyOf(invalid,invalid.length+1);bytes[invalid.length]=(byte)0xff;
        try{Http.read(new ByteArrayInputStream(bytes),64);throw new AssertionError();}catch(IOException expected){}
        System.out.println("PASS: bounded HTTP framing, duplicate headers, browser rejection and strict UTF-8");
    }
}
