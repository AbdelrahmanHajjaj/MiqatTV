package tv.miqat.companion.lan;

import java.nio.file.*;
import java.util.*;

public final class PairingQrCheck {
    static void check(boolean value){if(!value)throw new AssertionError("Compact QR mismatch");}
    static void rejects(Runnable task){try{task.run();throw new AssertionError("Invalid QR accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception {
        List<String> vectors=Files.readAllLines(Path.of("companion/android-shared/src/test/resources/qr-vectors.tsv"));
        for(String line:vectors){String[] v=line.split("\t");PairingQr.Data value=PairingQr.decode(v[3]);check(value.url.equals(v[0])&&value.pin.equals(v[1])&&value.token.equals(v[2]));check(PairingQr.encode(v[0],v[1],v[2]).equals(v[3]));}
        String text=vectors.get(0).split("\t")[3];PairingQr.Data sample=PairingQr.decode(text);
        for(int i=0;i<text.length();i++){String shortText=text.substring(0,i);rejects(()->PairingQr.decode(shortText));}
        for(String invalid:new String[]{text.substring(0,5)+"TF0"+text.substring(8),text.substring(0,11)+"000000"+text.substring(17),text+"0","miq3:"+text.substring(5),"ITV3:"+text.substring(5),"ITV2:"+text.substring(5),text.substring(0,5)+"GGW"+text.substring(8),text.substring(0,text.length()-2)+"::",text.substring(0,text.length()-1)+"_"})rejects(()->PairingQr.decode(invalid));
        for(String url:new String[]{"https://192.168.1.1:0","https://256.1.1.1:45761","https://01.1.1.1:45761","https://1.1.1.1:045761","https://host:45761","http://1.1.1.1:45761","https://1.1.1.1:45761/path","https://1.1.1.1:65536"})rejects(()->PairingQr.encode(url,sample.pin,sample.token));
        rejects(()->PairingQr.encode(sample.url,sample.pin,"_".repeat(43)));
        Random random=new Random(42);for(int i=0;i<256;i++){byte[] pin=new byte[32],token=new byte[32];random.nextBytes(pin);random.nextBytes(token);StringBuilder hex=new StringBuilder();for(byte b:pin)hex.append(String.format("%02x",b&255));String credential=Base64.getUrlEncoder().withoutPadding().encodeToString(token);PairingQr.Data actual=PairingQr.decode(PairingQr.encode(sample.url,hex.toString(),credential));check(actual.pin.equals(hex.toString())&&actual.token.equals(credential));}
        System.out.println("PASS: compact QR JVM/ES5 shared vectors, full pin/token round trips and strict rejection");
    }
}
