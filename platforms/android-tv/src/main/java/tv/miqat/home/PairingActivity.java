package tv.miqat.home;

import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import tv.miqat.companion.lan.Pairing;
import tv.miqat.core.UiContract;

/** QR opens a pairing request; only controls in this non-exported TV activity approve or revoke it. */
public final class PairingActivity extends Screen {
    private String rendered="";
    private CompanionApplication host(){return (CompanionApplication)getApplication();}
    @Override void onTick(){String key=stateKey();if(!key.equals(rendered))build();}
    private String stateKey() {
        CompanionApplication h=host();StringBuilder key=new StringBuilder().append(h.failed).append(h.identity!=null).append(h.pairing!=null).append(h.endpoint()).append(Palette.LAPIS_900).append(Palette.LAPIS_700).append(Palette.GOLD_400).append(Palette.PLASTER_100);
        if(h.pairing!=null){boolean open=h.pairing.isOpen(SystemClock.elapsedRealtime());key.append(open).append(open?h.token:"").append(h.pairing.approved(SystemClock.elapsedRealtime()));
            Pairing.Pending pending=h.pairing.pending(SystemClock.elapsedRealtime());if(pending!=null)key.append(pending.nonce);
            for(Pairing.Client client:h.pairing.clients())key.append(client.id);}
        return key.toString();
    }
    @Override void build() {
        Object focus=root!=null && root.findFocus()!=null?root.findFocus().getTag():null;
        newRoot();CompanionApplication h=host();rendered=stateKey();
        TextView title=text(displayBold,60,Palette.PLASTER_100);title.setText(t.companionPhones());place(title,Tokens.SPACE_SAFE_INLINE,64,1500/Math.max(1,Ui.factor(this)),100);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.ipx(12),Ui.ipx(12),Ui.ipx(12),Ui.ipx(12));body.setClipToPadding(false);
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.addView(body);place(scroll,Tokens.SPACE_SAFE_INLINE-12,196,1100/Math.max(1,Ui.factor(this)),690);
        TextView state=text(ui400,28,Palette.MIST_400);body.addView(state);
        Pairing.Pending pending=h.pairing==null?null:h.pairing.pending(SystemClock.elapsedRealtime());
        boolean open=h.pairing!=null && h.pairing.isOpen(SystemClock.elapsedRealtime());
        if(h.failed){state.setText(t.pairingUnavailable());action(body,"pairing.open",t.pairPhone(),h::open);}
        else if(h.pairing==null)state.setText(t.pairingLoading());
        else if(pending!=null) {
            state.setText(t.pairingConfirm());
            TextView name=text(ui600,36,Palette.PLASTER_100);name.setText(pending.name);body.addView(name);
            TextView code=text(ui600,72,Palette.GOLD_400);code.setText(pending.code);code.setTextDirection(View.TEXT_DIRECTION_LTR);body.addView(code);
            if(h.pairing.approved(SystemClock.elapsedRealtime())) {
                TextView waiting=text(ui400,28,Palette.MIST_400);waiting.setText(t.pairingWaiting());body.addView(waiting);
                action(body,"pairing.cancel",t.cancel(),h::cancel);
            } else {
                action(body,"pairing.reject",t.rejectPhone(),()->h.reject(pending.nonce));
                action(body,"pairing.approve",t.approvePhone(),()->h.approve(pending.nonce));
            }
        } else if(open && !h.endpoint().isEmpty() && !h.token.isEmpty() && h.identity!=null) {
            state.setText(t.pairingScan());
            try {
                String qr=tv.miqat.companion.lan.PairingQr.encode(h.endpoint(),h.identity.pin,h.token);
                ImageView image=new ImageView(this);image.setTag("pairing.qr");
                image.setImageBitmap(tv.miqat.companion.qr.QrImage.create(this,qr,800,new int[]{Palette.LAPIS_900,Palette.LAPIS_700,Palette.GOLD_400,Palette.PLASTER_100,Tokens.PLASTER_100}));
                image.setContentDescription(t.pairingScan());
                FrameLayout symbol=new FrameLayout(this);symbol.addView(image,new FrameLayout.LayoutParams(-1,-1));
                float size=400/Math.max(1,Ui.factor(this));place(symbol,1330,220,size,size);
            }catch(Exception error){state.setText(t.pairingFailed());}
            TextView address=text(ui400,26,Palette.MIST_400);address.setText(h.endpoint());address.setTextDirection(View.TEXT_DIRECTION_LTR);body.addView(address);
            action(body,"pairing.cancel",t.cancel(),h::cancel);
        } else {
            state.setText(h.endpoint().isEmpty()?t.pairingNetwork():t.pairingForeground());
            action(body,"pairing.open",t.pairPhone(),h::open);
        }
        if(h.pairing!=null) {
            if(h.pairing.clients().isEmpty()){TextView empty=text(ui400,26,Palette.MIST_400);empty.setText(t.pairingEmpty());body.addView(empty);}
            for(Pairing.Client client:h.pairing.clients())action(body,"pairing.revoke",client.name+" · "+t.revokePhone(),()->h.revoke(client.id)).setTag(R.id.pairing_phone_id,client.id);
        }
        FocusTile back=new FocusTile(this,Tokens.RADIUS_PANEL);TextView label=text(ui500,28,Palette.PLASTER_100);label.setText(t.back());label.setGravity(Gravity.CENTER);back.addView(label,new android.widget.FrameLayout.LayoutParams(-1,-1));
        back.setOnClickListener(v->finish());back.setLook(f->label.setTextColor(f?Palette.LAPIS_900:Palette.PLASTER_100));placeTile(back,Tokens.SPACE_SAFE_INLINE,942,280,64);
        setContentView(root);View restore=focus==null?null:root.findViewWithTag(focus);if(restore!=null)restore.requestFocus();else if(body.getChildCount()>1)body.getChildAt(1).requestFocus();
    }
    private FocusTile action(LinearLayout parent,String id,String title,Runnable run) {
        UiContract.INSTANCE.bind(id,"action");FocusTile tile=new FocusTile(this,Tokens.RADIUS_PANEL);tile.setTag(id);
        TextView label=text(ui500,28,Palette.PLASTER_100);label.setText(title);label.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);label.setPadding(Ui.ipx(24),0,Ui.ipx(24),0);
        tile.addView(label,new android.widget.FrameLayout.LayoutParams(-1,-1));tile.setLook(f->label.setTextColor(f?Palette.LAPIS_900:Palette.PLASTER_100));
        tile.setOnClickListener(v->run.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.ipx(84));lp.topMargin=Ui.ipx(16);parent.addView(tile,lp);
        return tile;
    }
}
