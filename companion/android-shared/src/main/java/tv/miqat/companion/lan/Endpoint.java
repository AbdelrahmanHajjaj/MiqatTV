package tv.miqat.companion.lan;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;

public final class Endpoint {
    public final String url,pin;
    public Endpoint(String url,String pin,int port,boolean allowLoopback)throws Exception {
        URI uri=new URI(url);String host=uri.getHost();
        if(!"https".equals(uri.getScheme()) || host==null || uri.getPort()!=port || uri.getRawUserInfo()!=null ||
            uri.getRawQuery()!=null || uri.getRawFragment()!=null || !"".equals(uri.getRawPath()) || !pin.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid TV endpoint");
        String[] parts=host.split("\\.",-1);
        if(parts.length!=4)throw new IllegalArgumentException("Numeric local address required");
        for(String part:parts)if(!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part)>255)throw new IllegalArgumentException("Invalid address");
        InetAddress address=InetAddress.getByName(host);
        if(!(address instanceof Inet4Address) || !(address.isSiteLocalAddress() || allowLoopback&&address.isLoopbackAddress()))
            throw new IllegalArgumentException("Local address required");
        this.url=url;this.pin=pin;
    }
}
