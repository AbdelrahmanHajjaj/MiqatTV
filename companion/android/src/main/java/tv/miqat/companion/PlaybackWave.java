package tv.miqat.companion;

import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** A decorative playback band; audio capture is unnecessary for this status animation. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic view; never inflated from XML.
final class PlaybackWave extends View {
    private final MainActivity activity;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();

    PlaybackWave(MainActivity activity) {
        super(activity);this.activity=activity;
        setTag("phone.playback-wave");setTag(R.id.companion_action,"playback.controls");setFocusable(true);setClickable(true);
        setContentDescription(activity.t("phone_adhan_controls"));
        setOnClickListener(v->PhonePlayback.openControls(activity));
        sync();
    }

    void sync() {
        setVisibility(PhonePlayback.scheduled()?VISIBLE:GONE);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean playing=PhonePlayback.outputStarted();
        boolean moving=playing && activity.phoneForeground && activity.views.motionEnabled();
        double phase=moving?SystemClock.uptimeMillis()/280.0:0;
        float width=getWidth(),height=Math.min(getHeight(),activity.views.dp(20));
        canvas.translate(0,getHeight()-height);
        if(width<=0 || height<=0)return;
        path.reset();
        for(int x=0;x<=width;x+=2) {
            double wave=playing?(Math.sin(x/width*Math.PI*8-phase)+.35*Math.sin(x/width*Math.PI*14+phase))*.65:0;
            float y=height*.55f+(float)(wave*height*.28*Math.sin(x/width*Math.PI));
            if(x==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        paint.setColor(activity.accent);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(activity.views.dp(2));paint.setAlpha(playing?200:90);
        canvas.drawPath(path,paint);
        path.lineTo(width,height);path.lineTo(0,height);path.close();
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(30);canvas.drawPath(path,paint);
        if(moving && isShown() && getWindowVisibility()==VISIBLE)postInvalidateOnAnimation();
    }
}
