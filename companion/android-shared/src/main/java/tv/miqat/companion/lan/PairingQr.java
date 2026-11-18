package tv.miqat.companion.lan;

import java.util.Base64;

/** QR schema 3; full 256-bit pin/token, numeric IPv4 endpoint and unchanged remote approval. */
public final class PairingQr {
    private static final String ALPHABET="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
    public static final class Data {
        public final String url,pin,token;
        Data(String url,String pin,String token){this.url=url;this.pin=pin;this.token=token;}
    }
    public static Data decode(String text) {
        if(text==null||text.length()!=112||!text.startsWith("MIQ3:"))throw invalid();
        byte[] bytes=new byte[71];int at=0;
        for(int i=5;i<text.length();i+=3) {
            int a=ALPHABET.indexOf(text.charAt(i)),b=ALPHABET.indexOf(text.charAt(i+1)),c=i+2<text.length()?ALPHABET.indexOf(text.charAt(i+2)):0;
            if(a<0||b<0||c<0)throw invalid();int n=a+b*45+c*2025;
            if(n>(i+2<text.length()?65535:255))throw invalid();
            if(i+2<text.length())bytes[at++]=(byte)(n>>>8);bytes[at++]=(byte)n;
        }
        int port=(bytes[5]&255)*256+(bytes[6]&255);if(bytes[0]!=3||port==0)throw invalid();
        String url="https://"+(bytes[1]&255)+"."+(bytes[2]&255)+"."+(bytes[3]&255)+"."+(bytes[4]&255)+":"+port;
        StringBuilder pin=new StringBuilder();for(int i=7;i<39;i++)pin.append(Character.forDigit((bytes[i]&255)>>>4,16)).append(Character.forDigit(bytes[i]&15,16));
        return new Data(url,pin.toString(),Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes,39,71)));
    }
    public static String encode(String url,String pin,String token) {
        if(url==null||pin==null||token==null||!pin.matches("[0-9a-f]{64}")||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("https://([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+):([0-9]+)").matcher(url);
        if(!match.matches())throw invalid();byte[] bytes=new byte[71];bytes[0]=3;int port;
        try {
            for(int i=1;i<=5;i++){int n=Integer.parseInt(match.group(i));if(!Integer.toString(n).equals(match.group(i))||n>(i==5?65535:255)||i==5&&n==0)throw invalid();if(i<5)bytes[i]=(byte)n;}
            port=Integer.parseInt(match.group(5));
        }catch(NumberFormatException bad){throw invalid();}
        bytes[5]=(byte)(port>>>8);bytes[6]=(byte)port;
        for(int i=0;i<32;i++)bytes[7+i]=(byte)Integer.parseInt(pin.substring(i*2,i*2+2),16);
        byte[] secret=Base64.getUrlDecoder().decode(token);
        if(secret.length!=32||!Base64.getUrlEncoder().withoutPadding().encodeToString(secret).equals(token))throw invalid();
        System.arraycopy(secret,0,bytes,39,32);StringBuilder text=new StringBuilder("MIQ3:");
        for(int i=0;i<bytes.length;i+=2){int n=bytes[i]&255;if(i+1<bytes.length)n=n*256+(bytes[i+1]&255);text.append(ALPHABET.charAt(n%45));n/=45;text.append(ALPHABET.charAt(n%45));if(i+1<bytes.length)text.append(ALPHABET.charAt(n/45));}
        return text.toString();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid compact pairing QR");}
    private PairingQr() {}
}
