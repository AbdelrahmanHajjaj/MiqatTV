import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;

/** Independent decoder against actual browser rasterization, rotations and mild optical degradation. */
public final class QrStyleCheck {
    public static void main(String[] args)throws Exception {
        int count=0;
        try(var files=Files.list(Path.of(args[0]))) {
            for(Path file:files.filter(p->p.toString().endsWith(".png")).sorted().toList()) {
                BufferedImage image=ImageIO.read(file.toFile());String expected=Files.readString(Path.of(file.toString().replace(".png",".txt")));
                for(int turn=0;turn<4;turn++) {
                    decode(image,expected,file+" rotation "+turn);count++;
                    BufferedImage rotated=new BufferedImage(image.getHeight(),image.getWidth(),BufferedImage.TYPE_INT_RGB);
                    Graphics2D g=rotated.createGraphics();g.translate(rotated.getWidth(),0);g.rotate(Math.PI/2);g.drawImage(image,0,0,null);g.dispose();image=rotated;
                }
                BufferedImage blurred=new ConvolveOp(new Kernel(3,3,new float[]{0,.08f,0,.08f,.68f,.08f,0,.08f,0}),ConvolveOp.EDGE_NO_OP,null).filter(image,null);
                decode(blurred,expected,file+" blur");count++;
                BufferedImage faded=new RescaleOp(.65f,55,null).filter(image,null);
                decode(faded,expected,file+" lower contrast");count++;
            }
        }
        if(count!=144)throw new AssertionError("Missing samples: "+count);
        System.out.println("PASS: "+count+" independently decoded original styled QR samples (220/280/400 px, four rotations, blur and lower contrast)");
    }
    private static void decode(BufferedImage image,String expected,String why)throws Exception {
        int w=image.getWidth(),h=image.getHeight();int[] pixels=image.getRGB(0,0,w,h,null,0,w);
        try {
            String result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels)))).getText();
            if(!result.equals(expected))throw new AssertionError("Payload changed: "+why);
        } catch(ReaderException error){throw new AssertionError("Cannot scan "+why,error);}
    }
}
