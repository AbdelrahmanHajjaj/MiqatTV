package tv.miqat.companion.qr;

import android.content.Context;
import android.graphics.*;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;

/** Native rendering of the approved canonical medallions and contrast-safe active-theme palette. */
public final class QrImage {
    public static Bitmap create(Context context,String text,int size,int[] colors)throws Exception {
        if(colors.length!=5)throw new IllegalArgumentException("QR theme colours required");
        JSONObject style;
        try(java.io.InputStream input=context.getAssets().open("qr-style.json")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[1024];int length;
            while((length=input.read(buffer))!=-1)bytes.write(buffer,0,length);
            style=new JSONObject(bytes.toString("UTF-8"));
        }
        int[][] palette=palette(colors,style.getJSONObject("palette"));int paper=palette[3][0],accent=palette[4][0];
        Path pupil=path(style.getJSONArray("pupil"));float r=(float)style.getDouble("cornerRadius"),stroke=(float)style.getDouble("inlayWidth");
        ByteMatrix matrix=Encoder.encode(text,ErrorCorrectionLevel.M,Collections.singletonMap(EncodeHintType.CHARACTER_SET,"UTF-8")).getMatrix();
        int n=matrix.getWidth(),margin=style.getInt("quietZone");Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(paper);canvas.scale(size/(n+2f*margin),size/(n+2f*margin));canvas.translate(margin,margin);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setShader(gradient(0,0,n,n,palette[0]));Path cell=new Path();
        for(int y=0;y<n;y++)for(int x=0;x<n;x++) {
            if(!dark(matrix,x,y))continue;
            if(x==6||y==6){canvas.drawRect(x,y,x+1,y+1,paint);continue;}
            float tl=!dark(matrix,x-1,y)&&!dark(matrix,x,y-1)?r:0,tr=!dark(matrix,x+1,y)&&!dark(matrix,x,y-1)?r:0;
            float br=!dark(matrix,x+1,y)&&!dark(matrix,x,y+1)?r:0,bl=!dark(matrix,x-1,y)&&!dark(matrix,x,y+1)?r:0;
            cell.reset();cell.moveTo(x+tl,y);cell.lineTo(x+1-tr,y);cell.quadTo(x+1,y,x+1,y+tr);cell.lineTo(x+1,y+1-br);
            cell.quadTo(x+1,y+1,x+1-br,y+1);cell.lineTo(x+bl,y+1);cell.quadTo(x,y+1,x,y+1-bl);cell.lineTo(x,y+tl);cell.quadTo(x,y,x+tl,y);cell.close();canvas.drawPath(cell,paint);
        }
        for(int[] at:new int[][]{{0,0},{n-7,0},{0,n-7}}) {
            canvas.save();canvas.translate(at[0],at[1]);paint.setStyle(Paint.Style.FILL);paint.setShader(gradient(0,0,7,7,palette[1]));round(canvas,paint,style.getJSONArray("eye"));
            paint.setShader(null);paint.setColor(paper);round(canvas,paint,style.getJSONArray("hole"));
            paint.setShader(gradient(1.8f,1.75f,5.2f,5.2f,palette[2]));canvas.drawPath(pupil,paint);
            paint.setShader(null);paint.setColor(accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(stroke);
            round(canvas,paint,style.getJSONArray("inlay"));canvas.drawPath(pupil,paint);canvas.restore();
        }
        return bitmap;
    }
    private static boolean dark(ByteMatrix m,int x,int y){int n=m.getWidth();return x>=0&&y>=0&&x<n&&y<n&&!(x<7&&y<7||x>=n-7&&y<7||x<7&&y>=n-7)&&m.get(x,y)==1;}
    private static LinearGradient gradient(float x,float y,float right,float bottom,int[] colors){return new LinearGradient(x,y,right,bottom,colors[0],colors[1],Shader.TileMode.CLAMP);}
    private static void round(Canvas canvas,Paint paint,JSONArray r)throws Exception{float x=(float)r.getDouble(0),y=(float)r.getDouble(1),radius=(float)r.getDouble(4);canvas.drawRoundRect(x,y,x+(float)r.getDouble(2),y+(float)r.getDouble(3),radius,radius,paint);}
    private static double luminance(int color){double sum=0;double[] weights={.2126,.7152,.0722};int[] channels={Color.red(color),Color.green(color),Color.blue(color)};for(int i=0;i<3;i++){double c=channels[i]/255d;sum+=(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4))*weights[i];}return sum;}
    private static int shade(int color,double factor){return Color.rgb((int)Math.round(Color.red(color)*factor),(int)Math.round(Color.green(color)*factor),(int)Math.round(Color.blue(color)*factor));}
    private static int dark(int color,int paper,JSONObject policy)throws Exception{return dark(color,paper,policy,policy.getDouble("minContrast"));}
    private static int dark(int color,int paper,JSONObject policy,double minimum)throws Exception{for(int i=0;i<64&&(luminance(paper)+.05)/(luminance(color)+.05)<minimum;i++)color=shade(color,policy.getDouble("darken"));return color;}
    private static int[][] palette(int[] c,JSONObject p)throws Exception {
        int paper=luminance(c[3])>=p.getDouble("minPaperLuminance")?c[3]:c[4];
        boolean warm=Color.red(c[0])>Color.green(c[0])*p.getDouble("warmRedRatio")&&Color.green(c[0])>Color.blue(c[0])*p.getDouble("warmGreenRatio");
        JSONArray shades=p.getJSONArray("accentShades");int[] accent={dark(shade(c[2],shades.getDouble(0)),paper,p),dark(shade(c[2],shades.getDouble(1)),paper,p)};
        int[] body={dark(c[0],paper,p),dark(c[1],paper,p)};return new int[][]{warm?accent:body,warm?body:accent,warm?accent:body,{paper},{dark(c[2],paper,p,p.getDouble("minInlayContrast"))}};
    }
    private static Path path(JSONArray commands)throws Exception {
        Path p=new Path();
        for(int i=0;i<commands.length();i++) {
            JSONArray c=commands.getJSONArray(i);String op=c.getString(0);
            if(op.equals("Z"))p.close();else if(op.equals("M"))p.moveTo((float)c.getDouble(1),(float)c.getDouble(2));
            else if(op.equals("L"))p.lineTo((float)c.getDouble(1),(float)c.getDouble(2));
            else if(op.equals("Q"))p.quadTo((float)c.getDouble(1),(float)c.getDouble(2),(float)c.getDouble(3),(float)c.getDouble(4));
            else throw new IllegalArgumentException("Unknown QR geometry");
        }
        return p;
    }
    private QrImage() {}
}
