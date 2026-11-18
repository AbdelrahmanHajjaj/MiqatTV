package tv.miqat.companion;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;

/** Camera frames are decoded locally, never photographed, saved or uploaded. */
public final class ScanActivity extends Activity implements SurfaceHolder.Callback {
    private final HandlerThread thread=new HandlerThread("companion-qr-camera");
    private Handler cameraHandler;
    private volatile Camera camera;
    private SurfaceView preview;
    private FrameLayout root;
    private volatile boolean active,surfaceReady,delivered;
    private volatile int displayRotation;
    private String cameraFailed;
    private TextView status;
    private String detected,otherCode;
    private final MultiFormatReader decoder=qrReader();

    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(MainActivity.localized(base));}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Catalog catalog=new Catalog(this);boolean ar=getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ar");
            int ink=getColor(R.color.companion_gold_400),ground=getColor(R.color.companion_lapis_900);
            cameraFailed=catalog.label(catalog.data.getJSONObject("text").getJSONObject("camera_failed"),ar);
            detected=catalog.label(catalog.data.getJSONObject("text").getJSONObject("scan_detected"),ar);otherCode=catalog.label(catalog.data.getJSONObject("text").getJSONObject("scan_other"),ar);
            getWindow().setStatusBarColor(ground);getWindow().setNavigationBarColor(ground);
            root=new FrameLayout(this);root.setBackgroundColor(ground);root.setLayoutDirection(ar?android.view.View.LAYOUT_DIRECTION_RTL:android.view.View.LAYOUT_DIRECTION_LTR);
            preview=new SurfaceView(this);preview.getHolder().addCallback(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
            android.view.View guide=new Guide(this,ink,ground);guide.setTag("scan.frame");guide.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(guide,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);int padding=dp(20);controls.setPadding(padding,padding,padding,padding);controls.setBackgroundColor(ground);
            TextView title=new TextView(this);title.setText(catalog.label(catalog.data.getJSONObject("text").getJSONObject("scan_qr"),ar));title.setTypeface(getResources().getFont(R.font.alexandria_regular));title.setTextSize(20);title.setTextColor(ink);controls.addView(title);
            TextView hint=new TextView(this);hint.setText(catalog.label(catalog.data.getJSONObject("text").getJSONObject("scan_hint"),ar));hint.setTextColor(android.graphics.Color.WHITE);hint.setTextSize(15);hint.setPadding(0,dp(8),0,dp(8));controls.addView(hint);
            status=new TextView(this);status.setText(catalog.label(catalog.data.getJSONObject("text").getJSONObject("scan_searching"),ar));status.setTextColor(ink);status.setTextSize(14);status.setTag("scan.status");status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);controls.addView(status);
            String cancelLabel=catalog.label(catalog.data.getJSONObject("text").getJSONObject("cancel"),ar);
            ImageButton cancel=new ImageButton(this);cancel.setImageDrawable(Icons.get(this,"close",ink));cancel.setContentDescription(cancelLabel);cancel.setTooltipText(cancelLabel);cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ground));cancel.setOnClickListener(v->finish());
            FrameLayout.LayoutParams close=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);close.topMargin=dp(12);close.setMarginEnd(dp(12));root.addView(cancel,close);
            root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(root);
            thread.start();cameraHandler=new Handler(thread.getLooper());
        }catch(Exception unavailable){finish();}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onResume() {
        super.onResume();active=true;displayRotation=getWindowManager().getDefaultDisplay().getRotation();
        if(checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.CAMERA},1);
        else if(cameraHandler!=null)cameraHandler.post(this::open);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==1){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(cameraHandler!=null)cameraHandler.post(this::open);}else unavailable();}
    }
    @Override protected void onPause(){active=false;if(cameraHandler!=null)cameraHandler.post(this::release);super.onPause();}
    @Override protected void onDestroy(){if(cameraHandler!=null){cameraHandler.post(this::release);thread.quitSafely();}super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder holder){surfaceReady=true;if(cameraHandler!=null)cameraHandler.post(this::open);}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;if(cameraHandler!=null)cameraHandler.post(this::release);}
    private void open() {
        if(!active||!surfaceReady||delivered||camera!=null||checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try {
            int count=Camera.getNumberOfCameras();if(count==0)throw new IllegalStateException();int id=0;
            Camera.CameraInfo info=new Camera.CameraInfo();
            for(int i=0;i<count;i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){id=i;break;}}
            Camera.getCameraInfo(id,info);camera=Camera.open(id);Camera.Parameters parameters=camera.getParameters();
            if(!parameters.getSupportedPreviewFormats().contains(ImageFormat.NV21))throw new IllegalStateException();
            Camera.Size size=null,smallest=null;
            for(Camera.Size candidate:parameters.getSupportedPreviewSizes()) {
                if(smallest==null || candidate.width*candidate.height<smallest.width*smallest.height)smallest=candidate;
                if(Math.max(candidate.width,candidate.height)<=1280 && Math.min(candidate.width,candidate.height)<=960 && (size==null || candidate.width*candidate.height>size.width*size.height))size=candidate;
            }
            if(size==null)size=smallest;if(size==null||size.width*size.height>1920*1080)throw new IllegalStateException();
            final int width=size.width,height=size.height;parameters.setPreviewSize(width,height);parameters.setPreviewFormat(ImageFormat.NV21);
            List<String> focus=parameters.getSupportedFocusModes();
            if(focus!=null&&focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);
            int degrees=displayRotation*90;
            int orientation=info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT?(360-(info.orientation+degrees)%360)%360:(info.orientation-degrees+360)%360;
            camera.setDisplayOrientation(orientation);camera.setPreviewDisplay(preview.getHolder());
            final float ratio=orientation%180==0?(float)width/height:(float)height/width;
            runOnUiThread(()->{if(isDestroyed())return;int w=root.getWidth(),h=root.getHeight();if(w==0||h==0)return;int pw=w,ph=Math.round(w/ratio);if(ph<h){ph=h;pw=Math.round(h*ratio);}preview.setLayoutParams(new FrameLayout.LayoutParams(pw,ph,Gravity.CENTER));});
            camera.setErrorCallback((error,source)->{release();if(active)unavailable();});
            camera.setPreviewCallbackWithBuffer((bytes,source)->{
                if(!active||delivered||source!=camera)return;
                String decoded=decode(decoder,bytes,width,height);
                if(decoded!=null&&!recognizesQr(decoded)){runOnUiThread(()->{if(!isFinishing()&&!status.getText().toString().equals(otherCode))status.setText(otherCode);});decoded=null;}
                final String code=decoded;
                if(code!=null){delivered=true;release();runOnUiThread(()->{if(!isFinishing()){status.setText(detected);setResult(RESULT_OK,new Intent().putExtra("qr",code));finish();}});}
                else cameraHandler.postDelayed(()->{if(active&&source==camera)try{source.addCallbackBuffer(bytes);}catch(RuntimeException closed){release();}},150);
            });
            camera.addCallbackBuffer(new byte[width*height*3/2]);camera.startPreview();
        }catch(Exception failure){release();if(active)unavailable();}
    }
    static String decode(MultiFormatReader reader,byte[] bytes,int width,int height) {
        if(bytes==null||width<=0||height<=0||width>1920||height>1920||width*height>1920*1080||bytes.length<width*height*3/2)return null;
        byte[] plane=bytes;int w=width,h=height;
        for(int turn=0;turn<4;turn++) {
            try{return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(plane,w,h,0,0,w,h,false)))).getText();}
            catch(ReaderException invalid){if(turn==3)return null;}finally{reader.reset();}
            // Preview orientation does not rotate camera bytes; retry the luminance plane without changing its buffer.
            byte[] rotated=new byte[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)rotated[(w-1-x)*h+y]=plane[y*w+x];
            plane=rotated;int previous=w;w=h;h=previous;
        }
        return null;
    }
    static boolean recognizesQr(String code){
        if(code==null||code.length()>4096)return false;
        if(code.startsWith("MIQ3:")){try{tv.miqat.companion.lan.PairingQr.decode(code);return true;}catch(IllegalArgumentException invalid){return false;}}
        try{
            org.json.JSONObject value=tv.miqat.companion.lan.Json.read(code);Object version=value.get("version");
            if(!(version instanceof Number)||!(value.getDouble("version")==1||value.getDouble("version")==2))return false;
            return value.optString("type").equals("miqat-tv-pair")&&value.optString("url").startsWith("https://")&&value.optString("pin").matches("[0-9a-f]{64}")&&tv.miqat.companion.lan.Pairing.tokenShape(value.optString("token"))&&(value.getInt("version")==2||value.has("certificate"));
        }catch(Exception invalid){return false;}
    }
    private static final class Guide extends android.view.View {
        final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path frame=new android.graphics.Path(),shade=new android.graphics.Path();
        final float unit;final int accent,ground;
        Guide(android.content.Context context,int accent,int ground){super(context);unit=getResources().getDisplayMetrics().density;this.accent=accent;this.ground=ground;}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            float size=Math.min(getWidth()-48*unit,getHeight()*.46f),left=(getWidth()-size)/2,right=left+size,top=Math.max(96*unit,getHeight()*.4f-size/2);
            MiqatShape.set(frame,size,size*1.2f,false);frame.offset(left,top-size*.2f);
            shade.reset();shade.setFillType(android.graphics.Path.FillType.EVEN_ODD);shade.addRect(0,0,getWidth(),getHeight(),android.graphics.Path.Direction.CW);shade.addPath(frame);
            paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor((ground&0x00ffffff)|0xb3000000);canvas.drawPath(shade,paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(2*unit);paint.setColor(accent);canvas.drawPath(frame,paint);
        }
    }
    static MultiFormatReader qrReader() {
        MultiFormatReader reader=new MultiFormatReader();Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);reader.setHints(hints);return reader;
    }
    private void release(){if(camera!=null){try{camera.setPreviewCallbackWithBuffer(null);camera.stopPreview();}catch(RuntimeException ignored){}camera.release();camera=null;}}
    private void unavailable(){runOnUiThread(()->{if(!isFinishing()){Toast.makeText(this,cameraFailed,Toast.LENGTH_LONG).show();finish();}});}
}
