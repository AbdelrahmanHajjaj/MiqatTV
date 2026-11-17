package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;

/** Virtual camera ownership/release only; no physical camera or photographed QR acceptance. */
final class CameraCheck {
    static void run(Instrumentation test)throws Exception {
        if(!android.os.Build.HARDWARE.contains("ranchu")&&!android.os.Build.FINGERPRINT.contains("generic"))throw new AssertionError("Owned emulator only");
        java.lang.reflect.Field camera=ScanActivity.class.getDeclaredField("camera");camera.setAccessible(true);
        ScanActivity scan=(ScanActivity)test.startActivitySync(new Intent(test.getTargetContext(),ScanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            long deadline=SystemClock.elapsedRealtime()+12000;
            while(camera.get(scan)==null&&!scan.isFinishing()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
            if(camera.get(scan)==null)throw new AssertionError("Scanner did not open the emulator's granted virtual camera");
            test.runOnMainSync(()->{
                if(scan.getWindow().getDecorView().findViewWithTag("scan.frame")==null||scan.getWindow().getDecorView().findViewWithTag("scan.status")==null)throw new AssertionError("Scanner framing and explicit status missing");
            });
            android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();
            if(image==null)throw new AssertionError("Scanner screenshot unavailable");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(scan.getExternalFilesDir(null),"scanner.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
        }finally{test.runOnMainSync(scan::finish);}
        long deadline=SystemClock.elapsedRealtime()+6000;while(camera.get(scan)!=null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
        if(camera.get(scan)!=null)throw new AssertionError("Scanner retained camera after closing");
    }
}
