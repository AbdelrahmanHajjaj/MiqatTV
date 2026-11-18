package tv.miqat.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.miqat.companion.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native phone settings and explicit foreground Remote, with optional isolated demo TVs. */
public final class MainActivity extends Activity {
    Catalog catalog;
    LgSetup lgSetup;
    PhoneUi phoneUi;
    final DemoTv[] tvs = new DemoTv[2];
    final List<LanTv> lanTvs=new ArrayList<>();
    final ExecutorService io=Executors.newFixedThreadPool(2);
    Phones phones;
    boolean networkBusy,vaultFailed,remoteLoading;
    int remoteGeneration;
    LanTv remoteTarget;
    String networkMessage="working";
    int networkGeneration;
    final JSONObject[] demoRemotes={new JSONObject(),new JSONObject()};
    PairRequest pairing;
    String pairState="",pairCode="";
    final android.os.Handler polling=new android.os.Handler(android.os.Looper.getMainLooper());
    int pairGeneration;
    boolean phoneForeground;
    static final class PairResume {
        final PairRequest request;final String state;
        PairResume(PairRequest request,String state){this.request=request;this.state=state;}
    }
    int selected;
    boolean ar;
    String page="prayers", section="", query="";
    SettingsSnapshot snapshot;
    SettingsDraft appearance;
    LinearLayout body, content;
    ScrollView scroll;
    String renderedLocation="";
    Toast toast;
    Typeface regular, heading;
    int ground, surface, accent, ink, muted;
    private String pendingId="", pendingValue="";
    private Map<String,String> pendingBase;
    AlertDialog editor;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(localized(base));
    }
    static android.content.Context localized(android.content.Context base) {
        String language=base.getSharedPreferences("companion",MODE_PRIVATE).getString("language","system");
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        if(!language.equals("system")) {Locale locale=new Locale(language);config.setLocale(locale);config.setLayoutDirection(locale);}
        try{config.fontScale*=PhoneStore.read(base).number("display.size")/100f;}catch(Exception unavailable){/* TV access survives invalid prayer preferences. */}
        base=base.createConfigurationContext(config);
        return base;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            catalog=new Catalog(this);
            String language=prefs().getString("language","system");
            ar=language.equals("ar") || language.equals("system") && getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ar");
            regular=getResources().getFont(R.font.alexandria_regular);
            heading=Typeface.create(regular,Typeface.BOLD);
            ground=color("lapis-900"); surface=color("lapis-800"); accent=color("gold-400"); ink=color("plaster-100"); muted=color("mist-400");
            getWindow().setStatusBarColor(ground); getWindow().setNavigationBarColor(ground);
            for(int i=0;i<tvs.length;i++) {
                tvs[i]=catalog.demo(i==0?"android":"lg",decode(prefs().getString("demo-"+i,"{}")));
                tvs[i].setOnline(prefs().getBoolean("online-"+i,true));
            }
            phoneUi=new PhoneUi(this);if(state!=null)phoneUi.restore(state);
            phones=new Phones(this);
            try{for(Phones.Phone phone:phones.read())addPhone(phone);}catch(Exception unavailable){vaultFailed=true;}
            if(state!=null) {
                selected=state.getInt("selected"); page=state.getString("page","prayers"); section=state.getString("section",""); query=state.getString("query","");
                if(selected>=2) {
                    String pin=state.getString("selected-pin","");selected=-1;
                    for(int i=0;i<lanTvs.size();i++)if(lanTvs.get(i).phone.pin.equals(pin))selected=i+2;
                    if(selected<0){selected=0;page="tvs";state=null;}
                }
            }
            if(state!=null) {
                if(state.containsKey("snapshot")) snapshot=new SettingsSnapshot(decode(state.getString("snapshot")));
                if(state.containsKey("draft-base")) {
                    appearance=new SettingsDraft(new SettingsSnapshot(decode(state.getString("draft-base"))));
                    for(Map.Entry<String,String> e:decode(state.getString("draft-edits")).entrySet()) appearance.set(spec(e.getKey()),e.getValue());
                }
                pendingId=state.getString("pending-id",""); pendingValue=state.getString("pending-value","");
                if(state.containsKey("pending-base")) pendingBase=decode(state.getString("pending-base"));
            }
            if(getIntent().getBooleanExtra("prayer-home",false)){page="prayers";section="";}
            render();
            Object retained=getLastNonConfigurationInstance();
            if(retained instanceof PairResume && page.equals("pair")) {
                PairResume resume=(PairResume)retained;pairing=resume.request;pairState=resume.state;pairCode=pairing.code;
                if(pairState.equals("requesting"))getWindow().getDecorView().post(()->requestPairing(pairing));else render();
            }
            if(!pendingId.isEmpty() && editable()) getWindow().getDecorView().post(()->edit(pendingId));
            for(LanTv tv:lanTvs)io.execute(()->{tv.connect();runOnUiThread(()->{if(!isDestroyed()&&!networkBusy){if(page.equals("overview")&&real()==tv&&tv.online&&tv.remote.length()==0)readRemote();else if(page.equals("tvs")||page.equals("overview"))render();}});});
        } catch(Exception e) { throw new IllegalStateException("Companion catalog could not be loaded",e); }
    }

    android.content.SharedPreferences prefs() { return getSharedPreferences("companion",MODE_PRIVATE); }
    static Map<String,String> decode(String json) {
        try {
            JSONObject value=new JSONObject(json); Map<String,String> out=new LinkedHashMap<>();
            Iterator<String> keys=value.keys(); while(keys.hasNext()) { String key=keys.next(); out.put(key,value.getString(key)); }
            return out;
        } catch(Exception e) { throw new IllegalArgumentException("Invalid saved demo state",e); }
    }
    static String encode(Map<String,String> values) { return new JSONObject(values).toString(); }
    LanTv real(){return selected>=2&&selected-2<lanTvs.size()?lanTvs.get(selected-2):null;}
    TvSettings backend(){return real()==null?tv():real();}
    boolean online(){return real()==null?tv().getOnline():real().online;}
    String profile() { return real()==null?(selected==0?"android":"lg"):real().key; }
    DemoTv tv() { return tvs[selected]; }
    String name(int i) { return i>=2?lanTvs.get(i-2).phone.name:prefs().getString("name-"+i,t(i==0?"living":"bedroom")); }
    String t(String key) { return catalog.label(catalog.data.optJSONObject("text").optJSONObject(key),ar); }
    int color(String key) { return Color.parseColor(catalog.data.optJSONObject("colors").optString(key)); }
    String title(JSONObject c) { return catalog.label(c.optJSONObject("title"),ar); }
    String sectionTitle(String id) { return catalog.label(catalog.data.optJSONObject("sections").optJSONObject(id),ar); }
    SettingSpec spec(String id) { for(SettingSpec s:backend().getSpecs()) if(s.getId().equals(id)) return s; throw new IllegalArgumentException("Unsupported setting: "+id); }
    boolean editable() { return !networkBusy && online() && snapshot!=null && (real()==null||!real().needsRefresh); }
    String cacheKey(){return "cache-"+(real()==null?selected:real().key);}
    void addPhone(Phones.Phone phone)throws Exception {
        LanTv next=new LanTv(this,phones,phone,catalog);
        String cache=prefs().getString("cache-"+next.key,null);if(cache!=null)next.restore(cache);
        catalog.install(next);
        for(int i=0;i<lanTvs.size();i++)if(lanTvs.get(i).key.equals(next.key)){lanTvs.set(i,next);return;}
        lanTvs.add(next);
    }
    void cacheReal(LanTv tv) {
        try{catalog.install(tv);if(!tv.cached().getValues().isEmpty())prefs().edit().putString("cache-"+tv.key,tv.cache()).apply();}
        catch(Exception invalid){tv.online=false;tv.needsRefresh=true;}
    }
    <T> void network(Callable<T> task,Consumer<T> done,Runnable failed) {
        if(networkBusy)return;networkBusy=true;final int generation=++networkGeneration;render();
        io.execute(()->{
            try{T result=task.call();runOnUiThread(()->{if(isDestroyed()||generation!=networkGeneration)return;networkBusy=false;networkMessage="working";done.accept(result);});}
            catch(Exception error){runOnUiThread(()->{if(isDestroyed()||generation!=networkGeneration)return;networkBusy=false;networkMessage="working";failed.run();});}
        });
    }
    int dp(float value) { return Math.round(getResources().getDisplayMetrics().density*value); }
    LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.START); return l; }
    TextView text(String value,int size,int color) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setTypeface(regular);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        v.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        v.setGravity(Gravity.START); v.setTextDirection(View.TEXT_DIRECTION_LOCALE); v.setPadding(0,dp(5),0,dp(5)); return v;
    }
    TextView heading(String value) { TextView v=text(value,26,ink); v.setTypeface(heading); return v; }
    GradientDrawable background(int fill,int radius) { GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); return g; }
    LinearLayout card(LinearLayout parent) {
        LinearLayout c=column(); c.setBackground(background(surface,24)); c.setPadding(dp(18),dp(16),dp(18),dp(16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(12); parent.addView(c,lp); return c;
    }
    Button button(LinearLayout parent,String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(accent); b.setTypeface(regular); b.setMinHeight(dp(48));
        GradientDrawable shape=background(surface,24);
        b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x33000000),shape,null));b.setElevation(0);b.setStateListAnimator(null);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{accent,muted}));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(5);lp.bottomMargin=dp(5);
        parent.addView(b,lp); b.setOnClickListener(v->action.run()); return b;
    }
    void leadingIcon(Button button,String name) {
        android.graphics.drawable.Drawable icon=Icons.get(this,name,accent);
        button.setCompoundDrawablesRelative(icon,null,null,null);button.setCompoundDrawablePadding(dp(8));
        button.setGravity(Gravity.CENTER);button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);button.setSingleLine(true);button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int width=icon.getBounds().width()+button.getCompoundDrawablePadding()+Math.round(button.getPaint().measureText(button.getText().toString()));
            int inset=Math.max(dp(16),(right-left-width)/2);
            if(button.getPaddingStart()!=inset||button.getPaddingEnd()!=inset)button.setPaddingRelative(inset,button.getPaddingTop(),inset,button.getPaddingBottom());
        });
    }
    Button bindAction(Button button,String id) {
        if(!CompanionContract.INSTANCE.getActions().contains(id))throw new IllegalArgumentException("Unknown companion action: "+id);
        button.setTag(R.id.companion_action,id);return button;
    }
    LinearLayout horizontal(LinearLayout parent) {LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    void square(View view){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.setMarginStart(dp(3));lp.setMarginEnd(dp(3));view.setLayoutParams(lp);}
    void flex(View view){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(dp(3));lp.setMarginEnd(dp(3));view.setLayoutParams(lp);}
    boolean isPending(String id){return id.equals(pendingId)&&pendingBase!=null;}
    Button iconButton(LinearLayout parent,String icon,String label,Runnable action){
        Button button=button(parent,"",action);square(button);button.setContentDescription(label);button.setTooltipText(label);
        button.setForeground(Icons.get(this,icon,accent));button.setForegroundGravity(Gravity.CENTER);button.setForegroundTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{accent,muted}));button.setPadding(0,0,0,0);button.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x22000000),background(Color.TRANSPARENT,24),null));return button;
    }
    LinearLayout navigationRow(LinearLayout parent,String icon,String title,String subtitle,Runnable action){
        LinearLayout row=horizontal(parent);row.setMinimumHeight(dp(72));row.setPadding(dp(18),dp(10),dp(18),dp(10));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x22000000),background(surface,20),null));
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)row.getLayoutParams();lp.bottomMargin=dp(8);
        ImageView glyph=new ImageView(this);glyph.setImageDrawable(Icons.get(this,icon,accent));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(26),dp(26));image.setMarginEnd(dp(14));row.addView(glyph,image);
        LinearLayout labels=column();row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));labels.addView(text(title,16,ink));
        if(!subtitle.isEmpty())labels.addView(text(subtitle,12,muted));
        ImageView arrow=new ImageView(this);arrow.setImageDrawable(Icons.get(this,"back",muted));arrow.setRotation(ar?0:180);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(20),dp(20)));
        row.setFocusable(true);row.setContentDescription(title+(subtitle.isEmpty()?"":", "+subtitle));row.setOnClickListener(v->action.run());return row;
    }
    String sectionIcon(String id){
        switch(id){case "prayer":case "prayer-day":case "offsets":return "compass";case "reminders":case "tests":return "bell";
            case "appearance":return "mihrab";case "display":return "sun";case "radio":return "radio";case "network":return "wifi";
            case "favourites":return "star";case "device":return "tv";default:return "gear";}
    }
    void tab(String target){
        if(networkBusy)return;
        if(page.equals(target)&&(!target.equals("settings")||section.isEmpty()))return;
        Runnable change=()->{if(phoneUi!=null)phoneUi.pending="";stopAudio();if(lgSetup!=null)lgSetup.close();cancelPairing();clearPending();appearance=null;section="";query="";page=target;
            if(target.equals("settings"))readSettings();else if(target.equals("overview"))readRemote();render();};
        if(appearance!=null&&!appearance.getChanges().isEmpty())confirmDiscard(change);else change.run();
    }
    void bottomNavigation(LinearLayout shell){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6),dp(6),dp(6),dp(6));nav.setTag("navigation");nav.setElevation(dp(8));
        GradientDrawable material=background(surface,40);material.setStroke(dp(1),(muted&0x00ffffff)|0x33000000);nav.setBackground(material);
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,-2);bar.setMargins(dp(16),dp(8),dp(16),dp(14));shell.addView(nav,bar);
        boolean tvArea=Arrays.asList("overview","settings","updates").contains(page);
        String active=tvArea?(page.equals("settings")?"settings":"overview"):page.equals("prayers")?"prayers":Arrays.asList("phone-settings","preferences").contains(page)?"phone-settings":"tvs";
        String[] targets=tvArea?new String[]{"overview","settings","tvs"}:new String[]{"prayers","tvs","phone-settings"};
        String[] icons=tvArea?new String[]{"remote","gear","tv"}:new String[]{"mihrab","tv","gear"};
        String[] labels=tvArea?new String[]{t("remote_title"),t("settings"),t("tvs")}:new String[]{t("phone_home"),t("tvs"),t("phone_settings")};
        for(int i=0;i<targets.length;i++){
            final String target=targets[i];boolean selected=target.equals(active);int tint=selected?ground:muted;
            Button item=button(nav,labels[i],()->tab(target));flex(item);item.setMinHeight(dp(56));item.setTextSize(11);item.setPadding(dp(4),dp(4),dp(4),dp(4));
            item.setCompoundDrawablesRelative(null,Icons.get(this,icons[i],tint),null,null);item.setCompoundDrawablePadding(dp(4));item.setTag("nav."+target);
            item.setContentDescription(labels[i]);item.setSelected(selected);item.setTypeface(regular,selected?Typeface.BOLD:Typeface.NORMAL);
            item.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x33000000),background(selected?accent:Color.TRANSPARENT,32),null));
            item.setTextColor(tint);if(target.equals("settings"))bindAction(item,"open-settings");
            item.setEnabled(!networkBusy);item.setAlpha(item.isEnabled()?1f:0.45f);
        }
    }
    boolean motionEnabled(){return android.animation.ValueAnimator.areAnimatorsEnabled()&&(phoneUi==null||phoneUi.settings==null||!phoneUi.settings.value("display.reduce-motion").equals("true"));}
    void disableContent(View view){
        view.setEnabled(false);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)disableContent(((ViewGroup)view).getChildAt(i));
    }
    void notice(String key) {if(toast!=null)toast.cancel();toast=Toast.makeText(this,t(key),Toast.LENGTH_LONG);toast.show();}
    void info(String title,String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(t("done"),null).show(); }
    void navigate(String target) { stopAudio();if(lgSetup!=null&&!target.equals("lg-setup"))lgSetup.close(); page=target; query=""; render(); }

    void render() {
        if(phoneUi!=null)phoneUi.palette();
        String location=page+":"+section;
        int previousY=scroll!=null&&location.equals(renderedLocation)?scroll.getScrollY():0;renderedLocation=location;
        LinearLayout shell=column();shell.setBackgroundColor(ground);shell.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        LinearLayout toolbar=horizontal(shell);toolbar.setPadding(dp(12),dp(6),dp(12),dp(6));toolbar.setMinimumHeight(dp(64));
        if(page.equals("pair")||page.equals("lg-setup")||page.equals("preferences")||page.equals("updates")||page.equals("settings")&&!section.isEmpty()||page.equals("phone-settings")&&!section.isEmpty())iconButton(toolbar,"back",t("back"),this::back).setEnabled(!networkBusy||page.equals("pair"));
        if(page.equals("tvs")) {
            ImageView brand=new ImageView(this);brand.setImageResource(R.drawable.miqat_icon);brand.setContentDescription(ar?"ميقات":"Miqat");
            LinearLayout.LayoutParams mark=new LinearLayout.LayoutParams(dp(40),dp(40));mark.setMarginEnd(dp(12));toolbar.addView(brand,mark);
        }
        LinearLayout titles=column();toolbar.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        String pageTitle=page.equals("prayers")?t("phone_home"):page.equals("phone-settings")?phoneUi.sectionTitle():page.equals("tvs")?(ar?"ميقات":"Miqat"):page.equals("overview")?t("remote_title"):page.equals("settings")?(section.isEmpty()?t("settings"):section.equals("appearance")?t("appearance_title"):sectionTitle(section)):t(page);
        TextView title=text(pageTitle,page.equals("settings")&&!section.isEmpty()?22:26,ink);title.setPadding(0,0,0,0);title.setTypeface(heading);title.setMaxLines(page.equals("phone-settings")?2:1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(title);
        String subtitle=page.equals("tvs")?t("tvs"):Arrays.asList("overview","settings","updates").contains(page)?name(selected)+" · "+t(online()?"connected":"disconnected"):"";
        if(!subtitle.isEmpty()){TextView secondary=text(subtitle,11,muted);secondary.setPadding(0,dp(2),0,0);titles.addView(secondary);}
        if(page.equals("tvs"))iconButton(toolbar,"gear",t("preferences"),()->navigate("preferences"));
        if(Arrays.asList("overview","settings").contains(page))iconButton(toolbar,"refresh",t("refresh"),()->{if(page.equals("settings"))readSettings();else readRemote();}).setEnabled(!networkBusy);
        body=column(); body.setPadding(dp(20),dp(12),dp(20),dp(20)); body.setBackgroundColor(ground);
        String screen=page.equals("settings")&&section.equals("appearance")?"appearance":page;
        if(!CompanionContract.INSTANCE.getScreens().contains(screen))throw new IllegalStateException("Unknown companion screen: "+screen);
        body.setTag(screen);
        body.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        scroll=new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(true); scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);
        ScrollView current=scroll;current.post(()->current.scrollTo(0,previousY));
        switch(page) {
            case "prayers": case "phone-settings": phoneUi.render();break;
            case "lg-setup": if(lgSetup==null)lgSetup=new LgSetup(this);lgSetup.render(body);break;
            case "tvs": renderTvs(); break;
            case "overview": renderOverview(); break;
            case "settings": renderSettings(); break;
            case "preferences": renderPreferences(); break;
            case "pair": renderPair(); break;
            case "updates": renderUpdates(); break;
            default: throw new IllegalStateException("Unknown screen");
        }
        if(page.equals("settings")&&section.equals("appearance")&&appearance!=null){
            LinearLayout actions=horizontal(shell);actions.setPadding(dp(20),dp(8),dp(20),dp(8));actions.setBackgroundColor(ground);
            Button cancel=bindAction(iconButton(actions,"close",t("cancel"),()->{appearance=null;section="";render();}),"cancel-appearance");cancel.setTag("appearance.cancel");cancel.setEnabled(!networkBusy);
            Button apply=bindAction(button(actions,t("apply"),this::applyAppearance),"apply-appearance");flex(apply);apply.setTag("appearance.apply");apply.setEnabled(editable()&&!appearance.getChanges().isEmpty());apply.setMinHeight(dp(48));apply.setBackground(background(accent,28));apply.setTextColor(ground);apply.setAlpha(apply.isEnabled()?1f:0.4f);
        }
        if(networkBusy&&!page.equals("pair")){
            disableContent(body);
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(motionEnabled());if(!motionEnabled())progress.setProgress(50);progress.setIndeterminateTintList(ColorStateList.valueOf(accent));progress.setContentDescription(t(networkMessage));progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);shell.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        }
        bottomNavigation(shell);
    }

    void renderTvs() {
        if(vaultFailed)body.addView(text(t("vault_failed"),15,accent));
        if(lanTvs.isEmpty()){
            LinearLayout empty=card(body);ImageView glyph=new ImageView(this);glyph.setImageDrawable(Icons.get(this,"mihrab",accent));empty.addView(glyph,new LinearLayout.LayoutParams(-1,dp(72)));
            empty.addView(heading(t("no_tvs")));empty.addView(text(t("no_tvs_hint"),15,muted));
        }
        for(int i=0;i<lanTvs.size();i++) {
            final int index=i+2;LanTv tv=lanTvs.get(i);
            LinearLayout row=navigationRow(body,"tv",tv.phone.name,(tv.phone.profile.equals("android")?"Android TV":"LG webOS")+" · "+t(tv.online?"connected":"disconnected"),()->{selected=index;snapshot=null;appearance=null;section="";clearPending();page="overview";readRemote();render();});row.setTag("tv."+tv.key);
            row.removeViewAt(row.getChildCount()-1);
            Button more=iconButton(row,"more",t("more_actions"),()->{
                PopupMenu menu=new PopupMenu(this,row);menu.getMenu().add(t("forget_tv")).setOnMenuItemClickListener(item->{confirmForget(tv);return true;});menu.show();
            });more.setTag(tv.key+".forget");
        }
        Button pair=button(body,t("pair"),()->navigate("pair"));leadingIcon(pair,"add");pair.setTag("pair.open");pair.setEnabled(!vaultFailed);pair.setTextColor(ground);pair.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),background(accent,28),null));pair.setCompoundDrawableTintList(ColorStateList.valueOf(ground));pair.setMinHeight(dp(56));
        navigationRow(body,"tv",t("install_android"),t("install_android_hint"),()->startActivityForResult(
            new android.content.Intent(this,SetupActivity.class),72)).setTag("install.open");
        navigationRow(body,"tv",t("lg-setup"),t("lg_setup_hint"),()->navigate("lg-setup")).setTag("lg.open");
        if(!prefs().getBoolean("show-demo",false))return;
        body.addView(text(t("demo"),13,muted));
        for(int i=0;i<tvs.length;i++) {
            final int index=i;
            navigationRow(body,"tv",name(i),(i==0?"Android TV":"LG webOS")+" · "+t(tvs[i].getOnline()?"connected":"disconnected"),()->{selected=index;snapshot=null;appearance=null;section="";navigate("overview");});
        }
    }

    void renderOverview() {
        renderRemote();
        LinearLayout options=horizontal(body);options.setGravity(Gravity.END);
        iconButton(options,"more",t("more_actions"),()->{
            PopupMenu menu=new PopupMenu(this,options);
            menu.getMenu().add(t("rename")).setOnMenuItemClickListener(item->{renameTv();return true;});
            menu.getMenu().add(t("updates")).setOnMenuItemClickListener(item->{navigate("updates");return true;});
            if(real()!=null){LanTv target=real();menu.getMenu().add(t("forget_tv")).setOnMenuItemClickListener(item->{confirmForget(target);return true;});}menu.show();
        });
        if(real()!=null||!prefs().getBoolean("show-demo",false))return;
        LinearLayout tools=card(body); tools.addView(text(t("demo_tools"),16,ink));
        button(tools,t(tv().getOnline()?"disconnect":"reconnect"),()->{
            tv().setOnline(!tv().getOnline()); prefs().edit().putBoolean("online-"+selected,tv().getOnline()).apply(); render();
        });
        button(tools,t("fail_next"),()->{ tv().setFailNextSave(true); notice("fail_armed"); });
    }
    void confirmForget(LanTv target){
        new AlertDialog.Builder(this).setTitle(t("forget_tv")).setMessage(t("forget_tv_info"))
            .setNegativeButton(t("cancel"),null).setPositiveButton(t("forget_tv"),(dialog,which)->forgetTv(target)).show();
    }
    void forgetTv(LanTv target){
        LanTv selectedTv=real();
        network(()->{phones.remove(target.phone);return target;},removed->{
            remoteGeneration++;remoteLoading=false;lanTvs.remove(removed);prefs().edit().remove("cache-"+removed.key).apply();
            if(selectedTv==removed){cancelPairing();snapshot=null;appearance=null;clearPending();section="";selected=0;page="tvs";}
            else if(selectedTv!=null)selected=lanTvs.indexOf(selectedTv)+2;
            render();
        },()->{render();notice("vault_failed");});
    }
    void readRemote(){
        if(real()==null){if(!tv().getOnline())tv().setOnline(true);render();return;}
        LanTv target=real();if(remoteLoading&&remoteTarget==target)return;remoteTarget=target;final int generation=++remoteGeneration;remoteLoading=true;render();
        io.execute(()->{
            try{if(!target.online&&!target.connect())throw new java.io.IOException();target.readRemote();
                runOnUiThread(()->{if(isDestroyed()||generation!=remoteGeneration)return;remoteLoading=false;if(real()==target&&page.equals("overview"))render();});
            }catch(Exception unavailable){runOnUiThread(()->{if(isDestroyed()||generation!=remoteGeneration)return;remoteLoading=false;if(real()==target&&page.equals("overview")){render();notice("remote_failed");}});}
        });
    }
    JSONObject remoteData(){
        if(real()!=null)return real().remote;
        JSONObject data=demoRemotes[selected];
        try{if(!data.has("commands"))data.put("commands",catalog.data.getJSONArray("commands")).put("inputs",new JSONArray().put(new JSONObject().put("value","HDMI_1").put("label","HDMI 1")).put(new JSONObject().put("value","HDMI_2").put("label","HDMI 2"))).put("media",new JSONObject().put("kind","").put("state","idle"));}
        catch(Exception error){throw new IllegalStateException(error);}return data;
    }
    boolean commandOffered(String id){JSONArray commands=remoteData().optJSONArray("commands");if(commands!=null)for(int i=0;i<commands.length();i++)if(id.equals(commands.optString(i)))return true;return false;}
    void sendCommand(String id,String value){
        if(real()==null){
            try{JSONObject data=remoteData();if(id.equals("radio.play")||id.equals("tests.adhan-short"))data.put("media",new JSONObject().put("kind",id.equals("radio.play")?"radio":"adhan").put("state","playing"));
                if(id.equals("radio.stop")||id.equals("tests.cancel"))data.put("media",new JSONObject().put("kind","").put("state","idle"));
                if(id.equals("device.inputs"))data.put("input",value);
            }catch(Exception error){throw new IllegalStateException(error);}render();notice("demo");return;
        }
        LanTv target=real();networkMessage="sending_command";
        network(()->{
            boolean stopping=id.equals("radio.stop")||id.equals("tests.cancel");
            if(stopping&&!target.online&&!target.connect())throw new java.io.IOException();
            JSONObject result=target.command(id,value);
            if(result.optString("status").equals("accepted"))try{target.readRemote();}catch(Exception disconnected){}
            return result;
        },reply->{String status=reply.optString("status");render();notice(status.equals("accepted")?"remote_accepted":status.equals("busy")?"tv_busy":status.equals("uncertain")?"remote_uncertain":"unavailable");},()->{render();notice("remote_uncertain");});
    }
    Button commandButton(LinearLayout parent,String id,String icon,String label){
        Button button=iconButton(parent,icon,label,()->sendCommand(id,""));button.setTag(id);
        boolean stopping=id.equals("radio.stop")||id.equals("tests.cancel");
        button.setEnabled(!networkBusy&&commandOffered(id)&&(stopping||online()&&(real()==null||!real().remoteUncertain)));return button;
    }
    void renderRemote(){
        JSONObject state=remoteData();JSONArray offered=state.optJSONArray("commands");
        if(real()!=null&&real().remoteUncertain)body.addView(text(t("remote_uncertain"),14,accent));
        if(!online())body.addView(text(t("disconnected"),14,accent));
        if(offered==null||offered.length()==0){body.addView(text(t(remoteLoading?"loading_controls":offered==null?"remote_failed":"remote_unavailable"),15,muted));return;}
        LinearLayout navigation=horizontal(body);((LinearLayout.LayoutParams)navigation.getLayoutParams()).bottomMargin=dp(12);
        Button home=commandButton(navigation,"device.home","mihrab",t("tv_home"));flex(home);home.setForeground(null);home.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x22000000),background(surface,24),null));home.setText(t("tv_home"));home.setMinHeight(dp(88));home.setCompoundDrawablesRelative(null,Icons.get(this,"mihrab",accent),null,null);home.setCompoundDrawablePadding(dp(12));home.setPadding(dp(12),dp(8),dp(12),dp(8));
        Button inputs=commandButton(navigation,"device.inputs","input",t("tv_inputs"));flex(inputs);inputs.setForeground(null);inputs.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x22000000),background(surface,24),null));inputs.setText(t("tv_inputs"));inputs.setMinHeight(dp(88));inputs.setCompoundDrawablesRelative(null,Icons.get(this,"input",accent),null,null);inputs.setCompoundDrawablePadding(dp(12));inputs.setPadding(dp(12),dp(8),dp(12),dp(8));
        inputs.setOnClickListener(v->{
            JSONArray items=state.optJSONArray("inputs");if(items==null||items.length()==0){sendCommand("device.inputs","");return;}
            String[] labels=new String[items.length()+1];labels[0]=t("tv_inputs");for(int i=0;i<items.length();i++)labels[i+1]=items.optJSONObject(i).optString("label");
            ListPopupWindow menu=new ListPopupWindow(this);menu.setAnchorView(inputs);menu.setModal(true);menu.setWidth(dp(240));menu.setHeight(dp(Math.min(6,labels.length)*48));
            menu.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));menu.setOnItemClickListener((p,view,index,position)->{menu.dismiss();sendCommand("device.inputs",index==0?"":items.optJSONObject(index-1).optString("value"));});menu.show();
        });
        if(commandOffered("device.exit-input"))navigationRow(body,"tv",t("tv_input"),state.optString("input"),()->sendCommand("device.exit-input","")).setEnabled(online()&&(real()==null||!real().remoteUncertain));
        JSONObject media=state.optJSONObject("media");String kind=media==null?"":media.optString("kind"),status=media==null?"idle":media.optString("state");
        for(String section:new String[]{"radio","adhan"}){
            LinearLayout card=card(body);card.setPadding(dp(18),dp(10),dp(18),dp(10));LinearLayout row=horizontal(card);ImageView glyph=new ImageView(this);glyph.setImageDrawable(Icons.get(this,section.equals("radio")?"radio":"bell",accent));row.addView(glyph,new LinearLayout.LayoutParams(dp(28),dp(28)));
            LinearLayout labels=column();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(dp(12));row.addView(labels,lp);labels.addView(text(t(section.equals("radio")?"radio_title":"adhan_preview"),16,ink));
            labels.addView(text(t(!kind.equals(section)?"idle":status.equals("playing")?"playing":status.equals("loading")?"loading":"idle"),12,muted));
            commandButton(row,section.equals("radio")?"radio.play":"tests.adhan-short","play",t("play")+" · "+t(section.equals("radio")?"radio_title":"adhan_preview"));
            commandButton(row,section.equals("radio")?"radio.stop":"tests.cancel","stop",t("stop")+" · "+t(section.equals("radio")?"radio_title":"adhan_preview"));
        }
    }
    void renameTv(){
            EditText input=new EditText(this); input.setText(name(selected)); input.setSingleLine(true); input.setHint(t("rename"));
            AlertDialog d=new AlertDialog.Builder(this).setTitle(t("rename")).setView(input).setNegativeButton(t("cancel"),null).setPositiveButton(t("done"),null).create();
            d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{
                String n=input.getText().toString().trim();if(n.isEmpty()||n.length()>60){input.setError(t("invalid"));return;}d.dismiss();
                if(real()==null){prefs().edit().putString("name-"+selected,n).apply();render();}
                else {LanTv tv=real();network(()->tv.rename(n),renamed->render(),()->{notice("vault_failed");render();});}
            })); d.show();
    }

    void readSettings() {
        if(real()!=null) {
            LanTv tv=real();networkMessage="reading_settings";network(()->{if(tv.connect())tv.read();return tv.cached();},actual->{
                cacheReal(tv);if(real()==tv){
                    snapshot=actual.getValues().isEmpty()?null:actual;
                    if(snapshot!=null&&!tv.needsRefresh){
                        if(appearance!=null&&appearance.getChanges().entrySet().stream().allMatch(edit->edit.getValue().equals(snapshot.getValues().get(edit.getKey()))))appearance=null;
                        if(!pendingId.isEmpty()&&pendingValue.equals(snapshot.getValues().get(pendingId)))clearPending();
                    }
                    render();
                }
            },()->{cacheReal(tv);if(real()==tv){snapshot=tv.cached().getValues().isEmpty()?null:tv.cached();render();notice("read_failed");}});return;
        }
        if(tv().connect()) {
            snapshot=tv().read(); prefs().edit().putString("cache-"+selected,encode(snapshot.getValues())).apply();
        } else {
            String cache=prefs().getString("cache-"+selected,null); snapshot=cache==null?null:new SettingsSnapshot(decode(cache));
        }
        if(appearance!=null&&appearance.getChanges().isEmpty())appearance=null;
    }

    void renderSettings() {
        if(!online() || real()!=null && real().needsRefresh) {
            body.addView(text(t(snapshot==null?"no_cache":"stale"),14,accent));
            bindAction(button(body,t(real()==null?"reconnect":"refresh"),()->{if(real()==null){tv().setOnline(true);prefs().edit().putBoolean("online-"+selected,true).apply();}readSettings();render();}),"connect");
        }
        if(snapshot==null) return;
        if(section.equals("appearance")) {
            if(appearance==null) appearance=new SettingsDraft(snapshot);
            body.addView(text(t("preview"),13,muted));
            HomePreview preview=new HomePreview(this,catalog,appearance.getValues(),ar,real()==null?catalog.data.optString("demoSystemLanguage"):real().systemLanguage); preview.setTag("appearance-preview");
            body.addView(preview,new LinearLayout.LayoutParams(-1,-2));
            if(!appearance.getChanges().isEmpty()) body.addView(text(t("unsaved"),13,accent));
        }
        EditText search=new EditText(this); search.setSingleLine(true); search.setHint(t("search")); search.setText(query); search.setTextSize(15);search.setTextColor(ink);search.setHintTextColor(muted);search.setPadding(dp(18),dp(12),dp(18),dp(12));search.setBackground(background(surface,28));android.graphics.drawable.Drawable searchIcon=getDrawable(android.R.drawable.ic_menu_search).mutate();searchIcon.setTint(muted);searchIcon.setBounds(0,0,dp(22),dp(22));search.setCompoundDrawablesRelative(searchIcon,null,null,null);search.setCompoundDrawablePadding(dp(12));
        LinearLayout.LayoutParams searchLayout=new LinearLayout.LayoutParams(-1,dp(56));searchLayout.bottomMargin=dp(12);body.addView(search,searchLayout);
        content=column(); body.addView(content); populateSettings();
        search.addTextChangedListener(watcher(value->{query=value;populateSettings();}));
    }

    void populateSettings() {
        content.removeAllViews();
        if(section.isEmpty() && query.isEmpty()) {
            for(String id:new String[]{"prayer","reminders","prayer-day","offsets","appearance","display","radio","favourites","device","network","tests"}) {
                navigationRow(content,sectionIcon(id),sectionTitle(id),"",()->openSection(id)).setTag("section."+id);
            }
            return;
        }
        JSONArray all=catalog.controls(profile()); int count=0;
        for(int i=0;i<all.length();i++) {
            JSONObject c=all.optJSONObject(i); String id=c.optString("id"), group=c.optString("section");
            if(!section.isEmpty()&&!group.equals(section)) continue;
            if(!query.isEmpty()&&!title(c).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            if(id.equals("appearance.apply")||id.equals("appearance.cancel")||id.equals("appearance.home")) continue;
            if(group.equals("remote") && !section.equals("remote")) continue;
            count++;
            LinearLayout row=card(content); row.setTag(id);
            LinearLayout top=horizontal(row);TextView label=text(title(c),16,ink);top.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(!c.optBoolean("available")) {row.addView(text(t("unavailable"),12,muted));continue;}
            String kind=c.optString("kind");
            if(kind.equals("action")||group.equals("tests")) {
                Button b=iconButton(top,"back",title(c),()->action(c));b.setRotation(180);b.setTag(id+".activate");b.setEnabled(editable());
                row.setOnClickListener(v->{if(editable())action(c);});row.setFocusable(true);row.setEnabled(editable());row.setContentDescription(title(c));b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);b.setFocusable(false);
            } else {
                String value=values().get(id);
                if(kind.equals("toggle")) {
                    Switch toggle=new Switch(this);toggle.setMinHeight(dp(48));toggle.setTextColor(accent);toggle.setTypeface(regular);
                    toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));toggle.setTrackTintList(ColorStateList.valueOf((muted&0x00ffffff)|0x55000000));
                    toggle.setChecked(value.equals("true"));toggle.setEnabled(editable());toggle.setTag(id+".edit");toggle.setContentDescription(title(c)+", "+display(id,value));top.addView(toggle,new LinearLayout.LayoutParams(-2,dp(48)));
                    toggle.setOnClickListener(v->{
                        String next=String.valueOf(toggle.isChecked());toggle.setChecked(value.equals("true"));
                        if(c.optString("save").equals("appearance")) {
                            if(appearance==null){openSection("appearance");return;}
                            appearance.set(spec(id),next);render();
                        } else save(snapshot.getValues(),Collections.singletonMap(id,next),false);
                    });continue;
                }
                if(kind.equals("choice")) {Choices.add(this,row,c,spec(id),isPending(id)?pendingValue:value);continue;}
                if(kind.equals("number")&&tv.miqat.core.UiContract.INSTANCE.numberEditor(id).equals("inline")) {
                    numberControls(row,c,spec(id),value);continue;
                }
                row.addView(text(display(id,value),14,accent));
                Button b=iconButton(top,"edit",t("edit"),()->{
                    if(c.optString("save").equals("appearance")&&!section.equals("appearance")) {openSection("appearance");return;}
                    edit(id);
                });
                b.setContentDescription(title(c)+", "+display(id,value)); b.setEnabled(editable()); b.setTag(id+".edit");
            }
        }
        if(count==0) content.addView(text(t("no_results"),15,muted));
    }

    void openSection(String target) {
        stopAudio();clearPending();
        if(appearance!=null&&!appearance.getChanges().isEmpty()&&!target.equals("appearance")) { confirmDiscard(()->{appearance=null;section=target;render();});return; }
        if(!target.equals("appearance")) appearance=null;
        section=target;query="";render();
    }
    Map<String,String> values() { return appearance!=null&&section.equals("appearance")?appearance.getValues():snapshot.getValues(); }
    String display(String id,String value) {
        if(value==null||value.isEmpty()) return t("empty");
        if(id.equals("prayer.facing")&&value.equals("-1"))return t("not_set");
        SettingSpec spec=spec(id);
        if(spec.getKind().equals("toggle")) return t(value.equals("true")?"on":"off");
        if(spec.getKind().equals("list")) {
            List<String> labels=new ArrayList<>(); for(String item:value.split(",")) labels.add(catalog.value(profile(),id,item,ar)); return String.join(" · ",labels);
        }
        if(spec.getKind().equals("text")) return value.isEmpty()?t("empty"):t("done");
        return catalog.value(profile(),id,value,ar);
    }

    void edit(String id) {
        if(!editable()) return;
        if(!pendingId.isEmpty()&&!pendingId.equals(id)){clearPending();render();}
        stopAudio(); JSONObject c=catalog.control(profile(),id); SettingSpec s=spec(id);
        if(s.getKind().equals("choice")) {
            View field=body.findViewWithTag(id+".dropdown");if(field!=null){field.requestFocus();((AutoCompleteTextView)field).showDropDown();}return;
        }
        boolean restoring=id.equals(pendingId)&&pendingBase!=null;
        if(!restoring) { pendingId=id;pendingValue=values().get(id);pendingBase=new LinkedHashMap<>(appearance!=null&&s.getGroup().equals("appearance")?appearance.getBase().getValues():snapshot.getValues());if(id.equals("prayer.facing")&&pendingValue.equals("-1"))pendingValue="0"; }
        if(s.getKind().equals("number")&&tv.miqat.core.UiContract.INSTANCE.numberEditor(id).equals("inline")){editNumber(c,s);return;}
        LinearLayout box=column(); box.setTag("editor");box.setPadding(dp(20),dp(4),dp(20),dp(4));
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(title(c)).setView(box).setNegativeButton(t("cancel"),(d,w)->clearPending());
        switch(s.getKind()) {
            case "toggle": {
                Switch toggle=new Switch(this); toggle.setText(title(c));toggle.setMinHeight(dp(48));toggle.setChecked(pendingValue.equals("true"));toggle.setOnCheckedChangeListener((v,on)->pendingValue=String.valueOf(on));box.addView(toggle);break;
            }
            case "number": {
                box.addView(text(s.getMinimum()+" – "+s.getMaximum(),14,muted));
                EditText input=input(box,title(c),pendingValue,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
                button(box,"−",()->adjust(s,input,-1)); button(box,"+",()->adjust(s,input,1)); break;
            }
            case "list": listEditor(box,s); break;
            default:
                box.addView(text(t(id.equals("appearance.custom-theme")?"theme_format":"redirect_format"),13,muted));
                if(id.equals("appearance.custom-theme"))themeEditor(box);else redirectEditor(box);
        }
        if(real()==null&&!s.getGroup().equals("appearance")&&!s.getKind().equals("text")&&!s.getKind().equals("list")) button(box,t("simulate_conflict"),()->simulateConflict(s));
        if(s.getKind().equals("list")||s.getKind().equals("text")) {
            ScrollView listScroll=new ScrollView(this);listScroll.addView(box);builder.setView(listScroll);
        }
        builder.setPositiveButton(t("done"),null);
        editor=builder.create(); editor.setOnCancelListener(d->clearPending());
        editor.setOnDismissListener(d->{if(editor==d){stopAudio();editor=null;}});
        editor.show();
        bindAction(editor.getButton(-1),"save-edit");
        editor.getButton(-1).setOnClickListener(v->commitPending());
    }

    void selectChoice(String id,String value) {
        if(!editable())return;
        SettingSpec s=spec(id);s.validate(value);stopAudio();
        if(s.getGroup().equals("appearance")&&!section.equals("appearance")){openSection("appearance");return;}
        if(!isPending(id)){clearPending();pendingId=id;pendingBase=new LinkedHashMap<>(s.getGroup().equals("appearance")?appearance.getBase().getValues():snapshot.getValues());}
        pendingValue=value;
        if(id.equals("reminders.voice")||id.equals("reminders.fajr-voice")){render();return;}
        commitPending();
    }

    void commitPending() {
        if(pendingId.isEmpty()||!editable())return;
        String id=pendingId;SettingSpec s=spec(id);
            try {
                validateSpecial(s,pendingValue);s.validate(pendingValue);
            } catch(Exception e) {notice("invalid");return;}
            if(s.getKind().equals("number"))pendingValue=String.valueOf(Integer.parseInt(pendingValue));
            if(s.getGroup().equals("appearance")) {
                appearance.set(s,pendingValue);
                if(id.equals("appearance.custom-theme")&&!pendingValue.isEmpty()) appearance.set(spec("appearance.theme"),"custom");
                if(editor!=null)editor.dismiss();clearPending();render();
            } else {
                Map<String,String> changes=new LinkedHashMap<>();changes.put(id,pendingValue);
                Map<String,String> base=new LinkedHashMap<>(pendingBase);if(editor!=null)editor.dismiss();save(base,changes,false);
            }
    }

    EditText input(LinearLayout parent,String label,String value,int type) {
        EditText input=new EditText(this);input.setId(View.generateViewId());input.setInputType(type);input.setText(value);input.setHint(label);
        TextView heading=text(label,14,muted);heading.setLabelFor(input.getId());parent.addView(heading);parent.addView(input);
        input.addTextChangedListener(watcher(v->pendingValue=v));return input;
    }
    interface Changed {void accept(String value);}
    static TextWatcher watcher(Changed changed) {return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){changed.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    void adjust(SettingSpec s,EditText input,int direction) {try{input.setText(s.adjust(input.getText().toString(),direction));}catch(IllegalArgumentException e){input.setError(t("invalid"));}}
    void clearPending() {pendingId="";pendingValue="";pendingBase=null;}

    void editNumber(JSONObject c,SettingSpec spec) {
        String id=spec.getId();LinearLayout row=body.findViewWithTag(id);row.removeAllViews();
        row.addView(text(title(c),16,ink));row.addView(text(spec.getMinimum()+" – "+spec.getMaximum(),13,muted));
        LinearLayout fields=column();fields.setTag(id+".number-editor");row.addView(fields);
        LinearLayout track=horizontal(fields);
        EditText input=new EditText(this);input.setId(View.generateViewId());input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(pendingValue);input.setGravity(Gravity.CENTER);input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setTag(id+".number-input");
        ((TextView)row.getChildAt(0)).setLabelFor(input.getId());input.addTextChangedListener(watcher(value->pendingValue=value));
        Button minus=button(track,"−",()->adjust(spec,input,-1));square(minus);minus.setContentDescription(t("decrease")+" "+title(c));
        track.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Button plus=button(track,"+",()->adjust(spec,input,1));square(plus);plus.setContentDescription(t("increase")+" "+title(c));
        LinearLayout actions=horizontal(fields);
        Button done=bindAction(button(actions,t("done"),()->{
            try{spec.validate(pendingValue);}catch(IllegalArgumentException e){input.setError(t("invalid"));return;}
            String next=String.valueOf(Integer.parseInt(pendingValue));
            if(spec.getGroup().equals("appearance")){appearance.set(spec,next);clearPending();render();}
            else save(new LinkedHashMap<>(pendingBase),Collections.singletonMap(id,next),false);
        }),"save-edit");done.setTag(id+".save");flex(done);
        Button cancel=button(actions,t("cancel"),()->{clearPending();render();});cancel.setTag(id+".cancel");flex(cancel);
        input.requestFocus();
    }

    void numberControls(LinearLayout row,JSONObject c,SettingSpec s,String value) {
        String id=s.getId();LinearLayout track=horizontal(row);
        Button minus=button(track,"−",()->stepNumber(id,-1));square(minus);minus.setEnabled(editable()&&Integer.parseInt(value)>s.getMinimum());minus.setTag(id+".minus");minus.setContentDescription(t("decrease")+" "+title(c));
        Button amount=button(track,display(id,value),()->edit(id));flex(amount);amount.setTag(id+".edit");amount.setEnabled(editable());amount.setContentDescription(title(c)+", "+display(id,value));
        Button plus=button(track,"+",()->stepNumber(id,1));square(plus);plus.setEnabled(editable()&&Integer.parseInt(value)<s.getMaximum());plus.setTag(id+".plus");plus.setContentDescription(t("increase")+" "+title(c));
    }
    void stepNumber(String id,int direction) {
        if(!editable())return;
        SettingSpec s=spec(id);
        if(s.getGroup().equals("appearance")) {
            if(appearance==null){openSection("appearance");return;}
            appearance.set(s,s.adjust(values().get(id),direction));render();
        } else {
            edit(id);EditText field=body.findViewWithTag(id+".number-input");adjust(s,field,direction);
        }
    }

    void listEditor(LinearLayout parent,SettingSpec spec) {
        LinearLayout items=column();parent.addView(items);
        Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            items.removeAllViews();List<String> order=new ArrayList<>();if(!pendingValue.isEmpty())order.addAll(Arrays.asList(pendingValue.split(",")));
            for(int i=0;i<order.size();i++) {
                final int index=i;String item=order.get(i);LinearLayout row=card(items);row.addView(text(catalog.value(profile(),spec.getId(),item,ar),15,ink));
                LinearLayout actions=horizontal(row);
                Button earlier=button(actions,"↑",()->{Collections.swap(order,index,index-1);pendingValue=String.join(",",order);refresh[0].run();});square(earlier);earlier.setEnabled(index>0);earlier.setContentDescription(t("earlier"));
                Button later=button(actions,"↓",()->{Collections.swap(order,index,index+1);pendingValue=String.join(",",order);refresh[0].run();});square(later);later.setEnabled(index+1<order.size());later.setContentDescription(t("later"));
                Button remove=button(actions,t("remove"),()->{order.remove(index);pendingValue=String.join(",",order);refresh[0].run();});flex(remove);
            }
            Button add=button(items,t("add"),()->{});
            add.setOnClickListener(view->{
                List<String> choices=new ArrayList<>(spec.getOptions());choices.removeAll(order);
                chooseApp(view,choices,picked->{order.add(picked);pendingValue=String.join(",",order);refresh[0].run();});
            });add.setEnabled(order.size()<spec.getMaximum()&&order.size()<spec.getOptions().size());
        };refresh[0].run();
    }

    void validateSpecial(SettingSpec s,String value) throws Exception {
        if(s.getId().equals("appearance.custom-theme")&&!value.isEmpty()) {
            JSONObject theme=new JSONObject(tv.miqat.companion.lan.Json.read(value).toString());tv.miqat.companion.lan.Json.keys(theme,"version","colors","background","lighting","surface");
            if(!(theme.get("version") instanceof Integer)||theme.getInt("version")!=1)throw new IllegalArgumentException();
            if(theme.has("lighting")&&(!(theme.get("lighting") instanceof Integer)||theme.getInt("lighting")<0||theme.getInt("lighting")>100))throw new IllegalArgumentException();
            if(theme.has("background")) {
                String background=tv.miqat.companion.lan.Json.string(theme,"background");if(background.equals("theme")||!spec("appearance.background").getOptions().contains(background))throw new IllegalArgumentException();
            }
            if(theme.has("surface")){String surface=tv.miqat.companion.lan.Json.string(theme,"surface");boolean found=false;JSONArray surfaces=catalog.data.getJSONArray("themeSurfaces");for(int i=0;i<surfaces.length();i++)if(surfaces.getString(i).equals(surface))found=true;if(!found)throw new IllegalArgumentException();}
            JSONObject colors=theme.has("colors")?theme.getJSONObject("colors"):new JSONObject();Iterator<String> keys=colors.keys();
            List<String> allowed=new ArrayList<>();JSONArray palette=catalog.data.optJSONArray("themeKeys");for(int i=0;i<palette.length();i++)allowed.add(palette.optString(i));
            while(keys.hasNext()){String key=keys.next();if(!allowed.contains(key)||!colors.getString(key).matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException();}
        }
        if(s.getId().equals("device.redirects")&&!value.isEmpty()) {
            List<String> apps=spec("favourites.order").getOptions();Set<String> sources=new HashSet<>();
            Map<String,String> targets=new HashMap<>();
            for(String pair:value.split(",",-1)){String[] parts=pair.trim().split("=",-1);if(parts.length!=2||parts[0].equals(parts[1])||!apps.contains(parts[0])||!apps.contains(parts[1])||!sources.add(parts[0]))throw new IllegalArgumentException();targets.put(parts[0],parts[1]);}
            for(String source:targets.keySet()){Set<String> visited=new HashSet<>();String next=source;while(targets.containsKey(next)){if(!visited.add(next))throw new IllegalArgumentException();next=targets.get(next);}}
        }
    }

    void themeEditor(LinearLayout parent) {
        JSONObject theme;
        try{theme=new JSONObject(pendingValue);}catch(Exception e){theme=new JSONObject();}
        JSONObject palette=theme.optJSONObject("colors");if(palette==null)palette=new JSONObject();
        final JSONObject draft=palette;
        final JSONObject result=theme;
        JSONArray keys=catalog.data.optJSONArray("themeKeys");
        for(int i=0;i<keys.length();i++) {
            String key=keys.optString(i);EditText field=new EditText(this);field.setId(View.generateViewId());field.setSingleLine(true);field.setTextDirection(View.TEXT_DIRECTION_LTR);
            JSONObject selected=catalog.data.optJSONObject("themes").optJSONObject(values().get("appearance.theme"));
            String fallback=selected!=null?selected.optJSONObject("colors").optString(key,catalog.data.optJSONObject("colors").optString(key)):catalog.data.optJSONObject("colors").optString(key);
            field.setText(draft.optString(key,fallback));
            try{draft.put(key,field.getText().toString());}catch(Exception e){throw new IllegalStateException(e);}
            TextView label=text(t(key),14,muted);label.setLabelFor(field.getId());parent.addView(label);parent.addView(field);
            field.addTextChangedListener(watcher(value->{try{draft.put(key,value);result.put("version",1);result.put("colors",draft);pendingValue=result.toString();}catch(Exception e){throw new IllegalStateException(e);}}));
        }
        try{result.put("version",1);result.put("colors",draft);pendingValue=result.toString();}catch(Exception error){throw new IllegalStateException(error);}
    }

    void redirectEditor(LinearLayout parent) {
        LinearLayout items=column();parent.addView(items);Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            items.removeAllViews();List<String> pairs=new ArrayList<>();if(!pendingValue.isEmpty())pairs.addAll(Arrays.asList(pendingValue.split(",")));
            for(int i=0;i<pairs.size();i++) {
                final int index=i;String[] pair=pairs.get(i).split("=");
                LinearLayout row=card(items);row.addView(text(catalog.value(profile(),"favourites.order",pair[0],ar)+" → "+catalog.value(profile(),"favourites.order",pair[1],ar),14,ink));
                button(row,t("remove"),()->{pairs.remove(index);pendingValue=String.join(",",pairs);refresh[0].run();});
            }
            List<String> apps=spec("favourites.order").getOptions();List<String> names=new ArrayList<>();for(String app:apps)names.add(catalog.value(profile(),"favourites.order",app,ar));
            Spinner[] pickers=new Spinner[2];
            for(int i=0;i<2;i++) {
                TextView label=text(t(i==0?"source_app":"target_app"),14,muted);items.addView(label);
                Spinner picker=new Spinner(this,Spinner.MODE_DROPDOWN);picker.setId(View.generateViewId());picker.setMinimumHeight(dp(48));picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));picker.setPopupBackgroundDrawable(background(surface,12));
                if(!apps.isEmpty())picker.setSelection(Math.min(i,apps.size()-1));label.setLabelFor(picker.getId());items.addView(picker);pickers[i]=picker;
            }
            button(items,t("add"),()->{pairs.add(apps.get(pickers[0].getSelectedItemPosition())+"="+apps.get(pickers[1].getSelectedItemPosition()));pendingValue=String.join(",",pairs);refresh[0].run();}).setEnabled(apps.size()>1);
        };refresh[0].run();
    }
    void chooseApp(View anchor,List<String> apps,Changed picked) {
        List<String> names=new ArrayList<>();for(String app:apps)names.add(catalog.value(profile(),"favourites.order",app,ar));
        ListPopupWindow dropdown=new ListPopupWindow(this);dropdown.setAnchorView(anchor);dropdown.setWidth(anchor.getWidth());dropdown.setHeight(-2);dropdown.setModal(true);dropdown.setBackgroundDrawable(background(surface,12));
        dropdown.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,names));dropdown.setOnItemClickListener((parent,view,index,id)->{dropdown.dismiss();picked.accept(apps.get(index));});dropdown.show();
    }

    void simulateConflict(SettingSpec s) {
        Map<String,String> now=tv().read().getValues();String value=now.get(s.getId()),next=value;
        if(s.getKind().equals("toggle")) next=String.valueOf(!value.equals("true"));
        else if(s.getKind().equals("choice")) {int i=s.getOptions().indexOf(value);next=s.getOptions().get((i+1)%s.getOptions().size());}
        else if(s.getKind().equals("number")) next=s.adjust(value,value.equals(String.valueOf(s.getMaximum()))?-1:1);
        tv().apply(now,Collections.singletonMap(s.getId(),next));persistDemo();notice("sim_action");
    }

    void save(Map<String,String> base,Map<String,String> changes,boolean isAppearance) {
        if(!editable()) {notice("disconnected");return;}
        if(real()!=null) {
            LanTv tv=real();Map<String,String> baseline=new LinkedHashMap<>(base),patch=new LinkedHashMap<>(changes);
            network(()->tv.apply(baseline,patch),result->{cacheReal(tv);if(real()==tv)saveResult(result,baseline,patch,isAppearance);},()->{tv.needsRefresh=true;tv.online=false;render();info(t("settings"),t("uncertain"));});return;
        }
        saveResult(tv().apply(base,changes),base,changes,isAppearance);
    }
    void saveResult(SaveResult result,Map<String,String> base,Map<String,String> changes,boolean isAppearance) {
        if(result.getStatus().equals("saved")) {
            snapshot=result.getSnapshot();if(real()==null){persistDemo();prefs().edit().putString(cacheKey(),encode(snapshot.getValues())).apply();}
            if(isAppearance)appearance=null;clearPending();render();notice(real()==null?"saved":"saved_tv");
        } else if(result.getStatus().equals("uncertain")||result.getStatus().equals("busy")) {
            snapshot=result.getSnapshot();render();info(t("settings"),t(result.getStatus()));
        } else if(result.getStatus().equals("failed")) {
            new AlertDialog.Builder(this).setMessage(t("failed")).setNegativeButton(t("cancel"),(d,w)->{if(!isAppearance){clearPending();render();}})
                .setPositiveButton(t("done"),(d,w)->save(base,changes,isAppearance)).setOnCancelListener(d->{if(!isAppearance){clearPending();render();}}).show();
        } else {
            StringBuilder detail=new StringBuilder(t("conflict"));
            for(String id:result.getConflicts()) detail.append("\n\n").append(title(catalog.control(profile(),id))).append("\n").append(t("current")).append(": ").append(display(id,result.getSnapshot().getValues().get(id)))
                .append("\n").append(t("your_edit")).append(": ").append(display(id,changes.getOrDefault(id,base.get(id))));
            new AlertDialog.Builder(this).setMessage(detail).setNegativeButton(t("keep_current"),(d,w)->{snapshot=result.getSnapshot();if(real()==null)prefs().edit().putString(cacheKey(),encode(snapshot.getValues())).apply();if(isAppearance)appearance=null;clearPending();render();})
                .setPositiveButton(t("replace"),(d,w)->save(result.getSnapshot().getValues(),changes,isAppearance)).show();
        }
    }
    void persistDemo() {prefs().edit().putString("demo-"+selected,encode(tv().read().getValues())).apply();}
    void applyAppearance() {
        if(appearance==null||appearance.getChanges().isEmpty()||!editable())return;
        if("custom".equals(appearance.getValues().get("appearance.theme"))&&appearance.getValues().get("appearance.custom-theme").isEmpty()){edit("appearance.custom-theme");return;}
        save(appearance.getBase().getValues(),appearance.getChanges(),true);
    }

    void action(JSONObject c) {
        String route=c.optString("route"), id=c.optString("id");
        if(commandOffered(id)){sendCommand(id,"");return;}
        if(c.optString("section").equals("tests")||route.equals("tv-action")) {info(title(c),t(real()==null?"sim_action":"tv_step"));return;}
        switch(route) {
            case "reset-appearance": {
                if(appearance==null)appearance=new SettingsDraft(snapshot);
                section="appearance";query="";
                Map<String,String> defaults=catalog.defaults(profile());for(SettingSpec s:backend().getSpecs())if(CompanionContract.INSTANCE.getAppearanceLayoutControls().contains(s.getId()))appearance.set(s,defaults.get(s.getId()));render();break;
            }
            case "reset-offsets": {
                Map<String,String> changes=new LinkedHashMap<>();for(SettingSpec s:backend().getSpecs())if(s.getId().startsWith("offsets."))changes.put(s.getId(),"0");save(snapshot.getValues(),changes,false);break;
            }
            case "health":info(title(c),t(real()==null?"health":"tv_step"));break;
            case "today": {
                if(real()!=null){info(title(c),t("tv_step"));break;}
                StringBuilder times=new StringBuilder(t("sample_times"));for(int i=0;i<6;i++)times.append("\n").append(catalog.data.optJSONObject("prayerNames").optJSONArray(ar?"ar":"en").optString(i)).append("  ").append(new String[]{"05:10","06:30","12:00","15:15","17:30","19:00"}[i]);info(title(c),times.toString());break;
            }
            case "tv-step":case "inputs":case "apps":info(title(c),t("tv_step"));break;
            case "overview":navigate("overview");break;
            case "apply-appearance":applyAppearance();break;
            case "cancel-appearance":appearance=null;section="";render();break;
            default:
                if(route.equals("settings")){section="";render();}
                else openSection(route);
        }
    }

    void renderPreferences() {
        String[] labels={t("system"),"العربية","English"};String[] values={"system","ar","en"};
        RadioGroup choices=new RadioGroup(this);body.addView(choices);
        for(int i=0;i<labels.length;i++){
            final String value=values[i];RadioButton option=new RadioButton(this);option.setText(labels[i]);option.setTextColor(ink);option.setTypeface(regular);option.setTextSize(16);option.setMinHeight(dp(56));option.setPadding(dp(12),0,dp(12),0);
            option.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));
            choices.addView(option);option.setChecked(prefs().getString("language","system").equals(value));
            bindAction(option,"choose-language");option.setOnClickListener(v->{prefs().edit().putString("language",value).apply();recreate();});
        }
        button(body,t("try_demo"),()->{prefs().edit().putBoolean("show-demo",true).apply();navigate("tvs");});
    }
    void renderPair() {
        if(pairState.equals("requesting")||pairState.equals("waiting")) {
            boolean waiting=pairState.equals("waiting");
            LinearLayout progress=card(body);progress.setTag("pair.progress");
            pairStep(progress,t("pair_found"),true,false);
            pairStep(progress,t("pair_verify"),waiting,!waiting);
            pairStep(progress,t("pair_approve"),false,waiting);
            if(waiting){
                body.addView(text(t("pair_match"),16,ink));TextView code=text(pairCode,44,accent);code.setTag("pair.code");code.setLetterSpacing(.16f);code.setTextDirection(View.TEXT_DIRECTION_LTR);code.setGravity(Gravity.CENTER);code.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);body.addView(code);
            }
            TextView status=text(t(waiting?"pair_waiting":"pair_connecting"),15,muted);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
            Button cancel=button(body,t("cancel"),()->{cancelPairing();render();});leadingIcon(cancel,"close");cancel.setTag("pair.cancel");return;
        }
        body.addView(text(t("pair_info"),16,ink));
        body.addView(text(t("phone_name"),13,muted));EditText name=new EditText(this);name.setSingleLine(true);name.setText(prefs().getString("phone-name",android.os.Build.MODEL));name.setHint(t("phone_name"));name.setTextColor(ink);name.setHintTextColor(muted);name.setPadding(dp(16),dp(12),dp(16),dp(12));name.setBackground(background(surface,14));body.addView(name);
        Button scan=bindAction(button(body,t("scan_qr"),()->{
            String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>64){name.setError(t("invalid"));return;}
            prefs().edit().putString("phone-name",value).apply();cancelPairing();startActivityForResult(new android.content.Intent(this,ScanActivity.class),71);
        }),"scan-pairing-code");scan.setEnabled(!vaultFailed);scan.setTag("pair.scan");
        leadingIcon(scan,"scan");
        if(prefs().getBoolean("show-demo",false))bindAction(button(body,t("simulate_pair"),()->info(t("pair"),t("pair_done"))),"simulate-pairing");
    }
    void pairStep(LinearLayout parent,String label,boolean complete,boolean active){
        LinearLayout row=horizontal(parent);row.setMinimumHeight(dp(56));
        if(active&&motionEnabled()){ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminateTintList(ColorStateList.valueOf(accent));spinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(spinner,new LinearLayout.LayoutParams(dp(24),dp(24)));}
        else{ImageView glyph=new ImageView(this);glyph.setImageDrawable(Icons.get(this,complete?"check":"tv",complete?accent:muted));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(glyph,new LinearLayout.LayoutParams(dp(24),dp(24)));}
        TextView title=text(label,15,complete||active?ink:muted);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(dp(16));row.addView(title,lp);
    }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(request==71 && result==RESULT_OK && data!=null){String qr=data.getStringExtra("qr");if(qr!=null)beginPairing(qr);}
        if(request==72 && result==RESULT_OK)navigate("pair");
        if(request==73 && result==RESULT_OK && data!=null && data.getData()!=null){if(lgSetup==null)lgSetup=new LgSetup(this);lgSetup.select(data.getData());}
    }
    void beginPairing(String qr) {
        cancelPairing();page="pair";
        try{pairing=new PairRequest(qr,this,phones);requestPairing(pairing);}
        catch(Exception invalid){render();notice("invalid_qr");}
    }
    void requestPairing(PairRequest request) {
        if(request==null)return;final int generation=pairGeneration;pairState="requesting";networkMessage="pair_connecting";
        String name=prefs().getString("phone-name",android.os.Build.MODEL);if(name.length()>64)name=name.substring(0,64);final String phoneName=name;
        network(()->{Phones.Phone known=request.reconnect();if(known==null)request.request(phoneName);return known;},known->{
            if(generation!=pairGeneration){render();return;}
            if(known!=null){paired(known);return;}
            pairState="waiting";pairCode=request.code;render();schedulePoll();
        },()->{if(generation==pairGeneration){cancelPairing();render();notice("pair_failed");}else render();});
    }
    void schedulePoll() {
        if(!phoneForeground || !page.equals("pair") || !pairState.equals("waiting") || pairing==null)return;
        final int generation=pairGeneration;PairRequest request=pairing;polling.removeCallbacksAndMessages(null);
        polling.postDelayed(()->io.execute(()->{
            try{Phones.Phone phone=request.poll();runOnUiThread(()->{if(isDestroyed()||generation!=pairGeneration)return;if(phone!=null)paired(phone);else schedulePoll();});}
            catch(Exception closed){runOnUiThread(()->{if(isDestroyed()||generation!=pairGeneration)return;cancelPairing();render();notice("pair_failed");});}
        }),1500);
    }
    void paired(Phones.Phone phone) {
        try {
            addPhone(phone);selected=2;for(int i=0;i<lanTvs.size();i++)if(lanTvs.get(i).phone.pin.equals(phone.pin))selected=i+2;
            real().online=true;snapshot=null;appearance=null;clearPending();cancelPairing();page="overview";readRemote();notice("paired");
        }catch(Exception failed){cancelPairing();render();notice("vault_failed");}
    }
    void cancelPairing(){if(pairState.equals("requesting")&&networkBusy){networkGeneration++;networkBusy=false;networkMessage="working";}pairGeneration++;polling.removeCallbacksAndMessages(null);pairing=null;pairState="";pairCode="";}
    @Override public Object onRetainNonConfigurationInstance(){return pairing==null?null:new PairResume(pairing,pairState);}
    void renderUpdates() {
        if(real()!=null){body.addView(text(t("real_updates"),16,ink));return;}
        body.addView(text(t("update_info"),16,ink));
        bindAction(button(body,t("simulate_update"),()->new AlertDialog.Builder(this).setTitle(t("update_confirm")).setMessage(t("demo"))
            .setNegativeButton(t("cancel"),null).setPositiveButton(t("done"),(d,w)->info(t("updates"),t("update_done"))).show()),"simulate-update").setEnabled(tv().getOnline());
    }

    void playAudio(String voice) {
        if(PhonePlayback.scheduled()){notice("phone_stop_first");return;}
        if(!PhoneAlarms.notifications(this)){phoneUi.open("permissions");return;}
        startForegroundService(PhonePlayback.intent(this,0,voice,true,"preview:"+System.nanoTime()));
    }
    void stopAudio() {PhonePlayback.stopPreview(this);}
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("prayer-home",false))tab("prayers");}
    @Override protected void onResume(){super.onResume();if(phoneUi!=null){PhoneAlarms.rebuild(this);if(page.equals("prayers")||page.equals("phone-settings"))render();phoneUi.start();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==75){PhoneAlarms.rebuild(this);render();}}
    @Override protected void onStart(){super.onStart();phoneForeground=true;schedulePoll();if(lgSetup!=null&&page.equals("lg-setup"))render();}
    @Override protected void onStop(){phoneForeground=false;if(phoneUi!=null)phoneUi.stop();polling.removeCallbacksAndMessages(null);stopAudio();if(lgSetup!=null)lgSetup.close();if(toast!=null)toast.cancel();super.onStop();}
    @Override protected void onDestroy(){cancelPairing();if(lgSetup!=null)lgSetup.close();io.shutdownNow();super.onDestroy();}
    void confirmDiscard(Runnable next) {new AlertDialog.Builder(this).setMessage(t("discard")).setNegativeButton(t("cancel"),null).setPositiveButton(t("discard_yes"),(d,w)->next.run()).show();}
    void back() {
        if(networkBusy&&!page.equals("pair"))return;
        stopAudio();
        if(page.equals("phone-settings")&&!section.isEmpty()){phoneUi.pending="";section="";render();return;}
        if(page.equals("phone-settings")||page.equals("tvs")){tab("prayers");return;}
        if(page.equals("prayers")){finish();return;}
        if(page.equals("preferences")){navigate("phone-settings");return;}
        if(page.equals("pair")){cancelPairing();navigate("tvs");return;}
        if(!pendingId.isEmpty()&&editor==null){clearPending();render();return;}
        if(page.equals("settings")&&appearance!=null&&!appearance.getChanges().isEmpty()){confirmDiscard(()->{appearance=null;section="";render();});return;}
        if(page.equals("settings")&&!section.isEmpty()){appearance=null;section="";query="";render();}
        else if(page.equals("settings")||page.equals("updates"))navigate("overview");
        else if(!page.equals("tvs"))navigate("tvs");else finish();
    }
    @Override public void onBackPressed(){back();}
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);if(phoneUi!=null)phoneUi.save(state);state.putInt("selected",selected);state.putString("page",page);state.putString("section",section);state.putString("query",query);
        if(real()!=null)state.putString("selected-pin",real().phone.pin);
        if(snapshot!=null)state.putString("snapshot",encode(snapshot.getValues()));
        if(appearance!=null){state.putString("draft-base",encode(appearance.getBase().getValues()));state.putString("draft-edits",encode(appearance.getChanges()));}
        if(!pendingId.isEmpty()){state.putString("pending-id",pendingId);state.putString("pending-value",pendingValue);state.putString("pending-base",encode(pendingBase));}
    }
}
