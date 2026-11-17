package tv.miqat.companion.lan;

public final class EndpointCheck {
    static void denied(String url,String pin,boolean loopback)throws Exception {
        try{new Endpoint(url,pin,45761,loopback);}catch(Exception expected){return;}throw new AssertionError("Accepted endpoint: "+url);
    }
    public static void main(String[] args)throws Exception {
        String pin="ab".repeat(32);
        for(String host:new String[]{"10.0.0.1","172.16.0.1","172.31.255.254","192.168.0.1"})new Endpoint("https://"+host+":45761",pin,45761,false);
        for(String url:new String[]{"https://8.8.8.8:45761","https://172.32.0.1:45761","https://169.254.169.254:45761","https://127.0.0.1:45761",
            "https://0.0.0.0:45761","https://224.0.0.1:45761","https://[::1]:45761","https://tv.local:45761","https://10.0.0.256:45761",
            "https://010.0.0.1:45761","https://0x0a000001:45761","https://167772161:45761","https://10.0.0.1:45761/",
            "http://10.0.0.1:45761","https://10.0.0.1:80","https://user@10.0.0.1:45761","https://10.0.0.1:45761#fragment","https://10.0.0.1:45761?query"})denied(url,pin,false);
        denied("https://10.0.0.1:45761","no-pin",false);new Endpoint("https://127.0.0.1:45761",pin,45761,true);
        System.out.println("PASS: canonical private IPv4 endpoints only, pin/port validation, no names or URL suffixes");
    }
}
