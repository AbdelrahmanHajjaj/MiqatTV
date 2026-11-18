package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import tv.miqat.companion.core.SettingSpec;

/** Independent phone screens; TV settings never enter this controller. */
final class PhoneUi {
    final MainActivity a;
    PhoneData data;
    PhoneStore settings;
    int dayOffset;
    String pending="",draft="",latitude="",longitude="";
    private String artworkId="";
    private Bitmap artwork;
    private TextView countdown,status,playback;
    private long nextTime,lastTick;
    private int renderedDay;
    private String renderedZone="";
    private int civilDay(long now){Calendar day=new GregorianCalendar(settings.zone());day.setTimeInMillis(now);return day.get(Calendar.YEAR)*400+day.get(Calendar.DAY_OF_YEAR);}
    private final Handler clock=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){
        if(!a.phoneForeground)return;
        long now=System.currentTimeMillis();
        if(a.page.equals("prayers")&&settings!=null) {
            if(renderedDay!=civilDay(now)||!renderedZone.equals(settings.zone().getID())||lastTick!=0&&(now<lastTick||now-lastTick>5000)){lastTick=now;a.render();return;}
            lastTick=now;
            if(nextTime>0&&System.currentTimeMillis()>=nextTime){a.render();return;}
            if(countdown!=null&&nextTime>0){long minutes=Math.max(0,(nextTime-System.currentTimeMillis()+59999)/60000);countdown.setText(String.format(locale(),a.t("phone_remaining"),digits(String.format(Locale.US,"%02d:%02d",minutes/60,minutes%60))));}
            if(status!=null&&settings!=null)status.setText(a.t(PhoneAlarms.status(a,settings)));
        }
        if(playback!=null)playback.setVisibility(PhonePlayback.playing()?View.VISIBLE:View.GONE);
        clock.postDelayed(this,1000);
    }};
    PhoneUi(MainActivity a){this.a=a;}
    void restore(Bundle b){dayOffset=Math.max(-366,Math.min(366,b.getInt("phone-day")));pending=b.getString("phone-pending","");draft=b.getString("phone-draft","");latitude=b.getString("phone-lat","");longitude=b.getString("phone-lng","");}
    void save(Bundle b){b.putInt("phone-day",dayOffset);b.putString("phone-pending",pending);b.putString("phone-draft",draft);b.putString("phone-lat",latitude);b.putString("phone-lng",longitude);}
    void start(){clock.removeCallbacksAndMessages(null);clock.post(tick);}
    void stop(){clock.removeCallbacksAndMessages(null);}
    void palette(){
        try{data=PhoneData.get(a);settings=PhoneStore.read(a);JSONObject c=a.catalog.data.getJSONObject("themes").getJSONObject(settings.value("appearance.theme")).getJSONObject("colors");
            a.ground=Color.parseColor(c.getString("lapis-900"));a.surface=Color.parseColor(c.getString("lapis-800"));a.accent=Color.parseColor(c.getString("gold-400"));a.ink=Color.parseColor(c.getString("plaster-100"));a.muted=Color.parseColor(c.getString("mist-400"));
            a.getWindow().setStatusBarColor(a.ground);a.getWindow().setNavigationBarColor(a.ground);
        }catch(Exception invalid){settings=null;}
    }
    void render(){
        countdown=null;status=null;playback=null;nextTime=0;
        if(settings==null){a.body.addView(a.text(a.t("phone_settings_failed"),16,a.ink));reset(a.body);return;}
        try{if(a.page.equals("prayers"))home(System.currentTimeMillis());else preferences();}
        catch(Exception invalid){a.body.addView(a.text(a.t("phone_settings_failed"),16,a.ink));reset(a.body);}
        start();
    }
    String digits(String value){String mode=settings.value("display.digits");boolean eastern=mode.equals("eastern")||mode.equals("auto")&&a.ar;StringBuilder out=new StringBuilder();for(char c:value.toCharArray()){int n=Character.digit(c,10);out.append(n>=0?(char)((eastern?'٠':'0')+n):c);}return out.toString();}
    Locale locale(){return new Locale(a.ar?"ar":"en");}
    String time(double stamp){SimpleDateFormat f=new SimpleDateFormat(settings.value("display.clock").equals("12")?"h:mm a":"HH:mm",locale());f.setTimeZone(settings.zone());return digits(f.format(new Date((long)stamp)));}
    String location()throws Exception{return settings.value("prayer.location").equals("custom")?a.t("phone_custom_location"):a.catalog.label(data.city(settings.value("prayer.location")),a.ar);}
    void open(String section){pending="";a.section=section;a.page="phone-settings";a.render();}
    void home(long now)throws Exception {
        a.navigationRow(a.body,"compass",location(),settings.zone().getID(),()->open("calculation")).setTag("phone.location");
        renderedDay=civilDay(now);renderedZone=settings.zone().getID();lastTick=now;PhoneSchedule.Day today=settings.day(now,0),tomorrow=settings.day(now,1);tv.miqat.core.PrayerSelection selection=tv.miqat.companion.core.PhonePrayerRules.INSTANCE.nextPrayer(today.times,tomorrow.times,now);
        int next=selection==null?-1:selection.getNext();double target=selection==null?0:selection.getNextTime();
        boolean tomorrowNext=target>=tomorrow.start;
        LinearLayout hero=hero();hero.addView(a.text(a.t("phone_next")+(tomorrowNext?" · "+a.t("phone_tomorrow"):""),14,a.muted));
        TextView upcoming=a.text(next<0?a.t("phone_unavailable"):data.prayerName(next,a.ar)+" · "+time(target),28,a.ink);upcoming.setTypeface(a.heading);hero.addView(upcoming);
        if(next>=0){countdown=a.text("",18,a.accent);countdown.setTag("phone.countdown");hero.addView(countdown);nextTime=(long)target;}
        status=a.text(a.t(PhoneAlarms.status(a,settings)),12,a.muted);status.setTag("phone.status");hero.addView(status);
        a.button(hero,a.t(settings.locationReady?"phone_audio":"phone_choose_location"),()->open(settings.locationReady?"audio":"calculation")).setTag("phone.setup");
        playback=a.button(hero,a.t("phone_stop"),()->{PhonePlayback.stop(a);a.render();});playback.setTag("phone.stop");
        String audioError=PhoneStore.preferences(a).getString("audio-error","");if(!audioError.isEmpty())hero.addView(a.text(a.t(audioError),13,a.accent));
        LinearLayout dateBar=a.horizontal(a.body);
        Button previous=a.iconButton(dateBar,"back",a.t("phone_previous_day"),()->{dayOffset--;a.render();});previous.setEnabled(dayOffset>-366);
        PhoneSchedule.Day day=settings.day(now,dayOffset);SimpleDateFormat date=new SimpleDateFormat("EEEE, d MMMM yyyy",locale());date.setTimeZone(settings.zone());
        Button dateLabel=a.button(dateBar,digits(date.format(new Date(day.start))),()->{dayOffset=0;a.render();});a.flex(dateLabel);dateLabel.setTag("phone.today");
        Button forward=a.iconButton(dateBar,"back",a.t("phone_next_day"),()->{dayOffset++;a.render();});forward.setRotation(180);forward.setEnabled(dayOffset<366);
        android.icu.util.IslamicCalendar hijri=new android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone(settings.zone().getID()),locale());
        hijri.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);hijri.setTimeInMillis(day.start);hijri.add(android.icu.util.Calendar.DATE,settings.number("prayer.hijri-offset"));
        android.icu.text.DateFormat hf=android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG,new android.icu.util.ULocale((a.ar?"ar":"en")+"@calendar=islamic-umalqura"));hf.setCalendar(hijri);a.body.addView(a.text(digits(hf.format(hijri.getTime())),14,a.muted));
        if(day.times==null)a.body.addView(a.text(a.t("phone_unavailable"),16,a.ink));
        else {
            LinearLayout pair=null;
            for(int i=0;i<6;i++) {
                if(i%2==0){pair=a.horizontal(a.body);pair.setGravity(Gravity.TOP);}
                final int prayer=i;String name=data.prayerName(i,a.ar),at=time(day.times[i]);
                String mode=i==1?"":value("call."+PhoneStore.PRAYERS[i]+".mode",settings.value("call."+PhoneStore.PRAYERS[i]+".mode"));
                LinearLayout cell=a.column();cell.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));cell.setMinimumHeight(a.dp(88));
                LinearLayout.LayoutParams cellSize=new LinearLayout.LayoutParams(0,-1,1);cellSize.setMarginEnd(a.dp(i%2==0?4:0));cellSize.setMarginStart(a.dp(i%2==1?4:0));cellSize.bottomMargin=a.dp(8);pair.addView(cell,cellSize);
                cell.setSelected(dayOffset==0&&!tomorrowNext&&i==next);
                GradientDrawable shape=a.background(a.surface,20);if(cell.isSelected())shape.setStroke(a.dp(2),a.accent);
                cell.setBackground(new RippleDrawable(ColorStateList.valueOf((a.accent&0xffffff)|0x22000000),shape,null));
                TextView label=a.text(name,16,a.ink);label.setPadding(0,0,0,0);cell.addView(label);
                TextView stamp=a.text(at,20,a.accent);stamp.setPadding(0,a.dp(3),0,0);stamp.setTypeface(a.heading);cell.addView(stamp);
                if(!mode.isEmpty()){TextView state=a.text(mode,11,a.muted);state.setPadding(0,a.dp(3),0,0);cell.addView(state);}
                cell.setTag("phone.prayer."+PhoneStore.PRAYERS[i]);cell.setFocusable(true);cell.setContentDescription(name+", "+at+(mode.isEmpty()?"":", "+mode));cell.setOnClickListener(v->open(prayer==1?"calculation":PhoneStore.PRAYERS[prayer]));
            }
        }
        if(tomorrow.times!=null)a.body.addView(a.text(a.t("phone_tomorrow")+" · "+data.prayerName(0,a.ar)+" · "+time(tomorrow.times[0]),15,a.muted));
    }
    LinearLayout hero()throws Exception {
        FrameLayout frame=new FrameLayout(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=a.dp(16);a.body.addView(frame,lp);
        String id=settings.value("appearance.background");
        if(!id.equals("plain")) {
            if(!id.equals(artworkId)){try(java.io.InputStream in=a.getAssets().open("backgrounds/"+id+".jpg")){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;artwork=BitmapFactory.decodeStream(in,null,opts);artworkId=id;}}
            ImageView image=new ImageView(a);image.setImageBitmap(artwork);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        }
        LinearLayout content=a.column();content.setPadding(a.dp(20),a.dp(16),a.dp(20),a.dp(16));
        content.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{(a.ground&0xffffff)|0xdd000000,(a.ground&0xffffff)|0xf5000000}));frame.addView(content,new FrameLayout.LayoutParams(-1,-2));
        frame.setBackground(a.background(a.surface,28));frame.setClipToOutline(true);return content;
    }
    void preferences()throws Exception {
        if(a.section.isEmpty()) {
            a.body.addView(a.text(a.t("phone_independent"),14,a.muted));
            for(String section:new String[]{"calculation","audio","appearance","permissions"})a.navigationRow(a.body,section.equals("calculation")?"compass":section.equals("appearance")?"mihrab":"bell",a.t("phone_"+section),"",()->open(section)).setTag("phone.section."+section);
            a.body.addView(a.heading(a.t("phone_prayer_settings")));
            for(int i=0;i<6;i++)if(i!=1){final String id=PhoneStore.PRAYERS[i];a.navigationRow(a.body,"bell",data.prayerName(i,a.ar),value("call."+id+".mode",settings.value("call."+id+".mode")),()->open(id));}
            a.navigationRow(a.body,"gear",a.t("preferences"),"",()->a.navigate("preferences"));reset(a.body);return;
        }
        if(a.section.equals("permissions")){permissions();return;}
        if(a.section.equals("calculation"))locationEditor();
        for(JSONObject c:data.controls.values())if(c.getString("section").equals(a.section))control(c);
        if(a.section.equals("audio")){a.body.addView(a.text(a.t("phone_volume_hint"),13,a.muted));a.body.addView(a.text(a.t("phone_duration_hint"),13,a.muted));}
    }
    String sectionTitle(){if(Arrays.asList(PhoneStore.PRAYERS).contains(a.section))try{return data.prayerName(Arrays.asList(PhoneStore.PRAYERS).indexOf(a.section),a.ar);}catch(Exception ignored){}return a.t("phone_"+(a.section.isEmpty()?"settings":a.section));}
    String value(String id,String value)throws Exception {
        if(id.endsWith(".mode")||id.endsWith(".high-latitude")||value.equals("inherit"))return a.t("phone_"+value);
        if(id.equals("prayer.zone"))return value.equals("system")?a.t("phone_system"):value;
        if(id.equals("reminders.duration")&&value.equals("0"))return a.t("phone_full");
        if(id.equals("appearance.background")&&value.equals("plain"))return a.t("phone_plain");
        if(id.endsWith(".voice")){for(int i=0;i<data.voices.length();i++){JSONObject voice=data.voices.getJSONObject(i);if(voice.getString("id").equals(value))return a.catalog.label(voice,a.ar);}}
        String source=id.startsWith("call.")?"reminders.voice":id;
        try{return a.catalog.value("android",source,value,a.ar);}catch(Exception absent){return digits(value);}
    }
    void control(JSONObject c)throws Exception {
        String id=c.getString("id"),label=a.t(c.getString("label")),saved=settings.value(id);SettingSpec spec=data.spec(c);
        ColorStateList selection=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{a.accent,a.muted});
        LinearLayout row=a.card(a.body);row.setTag("phone."+id);TextView title=a.text(label,16,a.ink);row.addView(title);
        if(c.getString("kind").equals("toggle")) {
            Switch toggle=new Switch(a);toggle.setText(a.t(saved.equals("true")?"on":"off"));toggle.setTextColor(a.ink);toggle.setTypeface(a.regular);toggle.setMinHeight(a.dp(48));toggle.setChecked(saved.equals("true"));toggle.setThumbTintList(selection);toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{(a.accent&0xffffff)|0x66000000,(a.muted&0xffffff)|0x44000000}));toggle.setContentDescription(label);row.addView(toggle);toggle.setTag("phone."+id+".edit");
            toggle.setOnCheckedChangeListener((button,on)->{if(id.equals("reminders.enabled")&&on&&!settings.locationReady){a.notice("phone_location_needed");open("calculation");return;}save(id,Boolean.toString(on));});return;
        }
        if(c.getString("kind").equals("number")){number(row,spec,saved,label);return;}
        java.util.List<String> options=data.options(c);
        if(options.size()<=3){RadioGroup group=new RadioGroup(a);row.addView(group);for(String option:options){RadioButton radio=new RadioButton(a);radio.setText(value(id,option));radio.setTextColor(a.ink);radio.setTypeface(a.regular);radio.setMinHeight(a.dp(48));radio.setChecked(saved.equals(option));radio.setButtonTintList(selection);radio.setTag("phone."+id+".option."+option);group.addView(radio);radio.setOnClickListener(v->save(id,option));}}
        else if(id.startsWith("appearance.")){int index=options.indexOf(saved);LinearLayout track=a.horizontal(row);Button previous=a.button(track,a.ar?"›":"‹",()->save(id,options.get(index-1)));a.square(previous);previous.setEnabled(index>0);previous.setContentDescription(a.t("previous")+" "+label);Button selected=a.button(track,value(id,saved),()->choose(id,options));a.flex(selected);selected.setTag("phone."+id+".edit");Button next=a.button(track,a.ar?"‹":"›",()->save(id,options.get(index+1)));a.square(next);next.setEnabled(index+1<options.size());next.setContentDescription(a.t("next")+" "+label);}
        else a.button(row,value(id,saved)+"  ⌄",()->choose(id,options)).setTag("phone."+id+".edit");
        if(id.endsWith(".voice")){a.button(row,a.t("phone_preview"),()->preview(id)).setTag("phone."+id+".preview");a.button(row,a.t("phone_stop"),()->PhonePlayback.stop(a));}
    }
    void number(LinearLayout row,SettingSpec spec,String saved,String label) {
        String id=spec.getId();boolean editing=pending.equals(id);LinearLayout track=a.horizontal(row);
        EditText input=new EditText(a);input.setSingleLine(true);input.setTextColor(a.ink);input.setTypeface(a.regular);input.setGravity(Gravity.CENTER);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(editing?draft:saved);input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setContentDescription(label);input.setTag("phone."+id+".input");
        Runnable changed=()->{pending=id;draft=input.getText().toString();};
        Button minus=a.button(track,"−",()->{try{input.setText(spec.adjust(normalize(input.getText().toString()),-1));changed.run();}catch(Exception invalid){input.setError(a.t("invalid"));}});a.square(minus);minus.setContentDescription(a.t("decrease")+" "+label);
        track.addView(input,new LinearLayout.LayoutParams(0,-2,1));input.addTextChangedListener(MainActivity.watcher(text->{pending=id;draft=text;}));
        Button plus=a.button(track,"+",()->{try{input.setText(spec.adjust(normalize(input.getText().toString()),1));changed.run();}catch(Exception invalid){input.setError(a.t("invalid"));}});a.square(plus);plus.setContentDescription(a.t("increase")+" "+label);
        LinearLayout actions=a.horizontal(row);Button apply=a.button(actions,a.t("done"),()->{try{String next=normalize(input.getText().toString());spec.validate(next);if((Integer.parseInt(next)-spec.getMinimum())%spec.getStep()!=0)throw new IllegalArgumentException();save(id,next);}catch(Exception invalid){input.setError(a.t("invalid"));}});a.flex(apply);apply.setTag("phone."+id+".save");Button cancel=a.button(actions,a.t("cancel"),()->{pending="";a.render();});a.flex(cancel);cancel.setTag("phone."+id+".cancel");
    }
    static String normalize(String value){StringBuilder out=new StringBuilder();for(char c:value.trim().toCharArray()){int d=Character.digit(c,10);out.append(d>=0?(char)('0'+d):c=='٫'?'.':c=='−'?'-':c);}return out.toString();}
    void choose(String id,java.util.List<String> options) {
        try{java.util.List<Choices.Option> list=new ArrayList<>();for(String v:options)list.add(new Choices.Option(v,value(id,v)));picker(a.t(data.controls.get(id).getString("label")),list,choice->save(id,choice));}catch(Exception invalid){a.notice("invalid");}
    }
    interface Picked{void accept(String value);}
    void picker(String title,java.util.List<Choices.Option> options,Picked picked){
        LinearLayout layout=a.column();layout.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(8));EditText search=new EditText(a);search.setSingleLine(true);search.setHint(a.t("phone_search"));search.setContentDescription(a.t("phone_search"));layout.addView(search);
        ListView list=new ListView(a);ArrayAdapter<Choices.Option> adapter=new ArrayAdapter<>(a,android.R.layout.simple_list_item_1,options);list.setAdapter(adapter);layout.addView(list,new LinearLayout.LayoutParams(-1,a.dp(320)));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(layout).setNegativeButton(a.t("cancel"),null).create();
        search.addTextChangedListener(MainActivity.watcher(text->adapter.getFilter().filter(text)));list.setOnItemClickListener((parent,view,index,item)->{Choices.Option option=adapter.getItem(index);dialog.dismiss();if(option!=null)picked.accept(option.value);});dialog.show();
    }
    void save(String id,String value){
        Map<String,String> changes=new LinkedHashMap<>();changes.put(id,value);if(id.equals("prayer.zone"))changes.put("prayer.location-ready","false");
        save(changes,id.equals("display.size"));
        if(id.equals("reminders.enabled")&&value.equals("true")&&(!PhoneAlarms.notifications(a)||!PhoneAlarms.exact(a)))open("permissions");
    }
    void save(Map<String,String> changes,boolean recreate) {
        try{PhoneStore.read(a).changed(changes).save(a);pending="";PhoneAlarms.rebuild(a);if(recreate)a.recreate();else a.render();}
        catch(Exception failure){a.notice("phone_save_failed");}
    }
    void locationEditor()throws Exception {
        a.body.addView(a.text(a.t("phone_location_needed"),14,a.muted));
        a.button(a.body,location()+"  ⌄",()->{
            java.util.List<Choices.Option> options=new ArrayList<>();try{for(int i=0;i<data.cities.length();i++){JSONObject c=data.cities.getJSONObject(i);options.add(new Choices.Option(c.getString("id"),a.catalog.label(c,a.ar)+" · "+c.optString(a.ar?"countryAr":"countryEn")));}}catch(Exception invalid){a.notice("invalid");return;}
            picker(a.t("phone_choose_location"),options,id->{try{JSONObject city=data.city(id);Map<String,String> changes=new LinkedHashMap<>();changes.put("prayer.location",id);changes.put("prayer.latitude",city.getString("lat"));changes.put("prayer.longitude",city.getString("lng"));changes.put("prayer.method",city.getString("method"));changes.put("prayer.asr",city.optBoolean("hanafi")?"hanafi":"shafi");changes.put("prayer.location-ready","false");latitude="";longitude="";save(changes,false);}catch(Exception invalid){a.notice("invalid");}});
        }).setTag("phone.city");
        LinearLayout custom=a.card(a.body);custom.addView(a.text(a.t("phone_custom_location"),16,a.ink));
        if(latitude.isEmpty()){latitude=settings.value("prayer.latitude");longitude=settings.value("prayer.longitude");}
        EditText lat=coordinate(custom,"phone_latitude",latitude),lng=coordinate(custom,"phone_longitude",longitude);
        lat.addTextChangedListener(MainActivity.watcher(v->latitude=v));lng.addTextChangedListener(MainActivity.watcher(v->longitude=v));
        a.button(custom,a.t("done"),()->{try{double x=Double.parseDouble(normalize(latitude)),y=Double.parseDouble(normalize(longitude));if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>90||Math.abs(y)>180)throw new IllegalArgumentException();Map<String,String> values=new LinkedHashMap<>();values.put("prayer.location","custom");values.put("prayer.latitude",Double.toString(x));values.put("prayer.longitude",Double.toString(y));values.put("prayer.location-ready","false");save(values,false);}catch(Exception invalid){lat.setError(a.t("invalid"));}}).setTag("phone.coordinates.save");
        a.button(custom,a.t("cancel"),()->{latitude="";longitude="";a.render();});
        a.body.addView(a.text(settings.latitude+", "+settings.longitude+" · "+settings.zone().getID(),14,a.muted));
        a.button(a.body,a.t("phone_confirm_location"),()->save("prayer.location-ready","true")).setTag("phone.location.confirm");
    }
    EditText coordinate(LinearLayout parent,String key,String value){TextView label=a.text(a.t(key),14,a.muted);parent.addView(label);EditText field=new EditText(a);field.setId(View.generateViewId());label.setLabelFor(field.getId());field.setTextColor(a.ink);field.setTypeface(a.regular);field.setSingleLine(true);field.setTextDirection(View.TEXT_DIRECTION_LTR);field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);field.setText(value);field.setTag(key);parent.addView(field);return field;}
    void permissions(){
        a.body.addView(a.text(a.t(PhoneAlarms.status(a,settings)),18,a.ink));a.body.addView(a.text(a.t("phone_permission_info"),15,a.muted));
        if(!PhoneAlarms.notifications(a))a.button(a.body,a.t("phone_permission_notifications"),()->{if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},75);else system(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));});
        if(!PhoneAlarms.exact(a))a.button(a.body,a.t("phone_permission_exact"),()->system(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+a.getPackageName()))));
        a.button(a.body,a.t("phone_channel_settings"),()->system(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName())));
        a.body.addView(a.heading(a.t("phone_power_title")));
        a.body.addView(a.text(a.t("phone_power_info"),14,a.muted));
        a.button(a.body,a.t("phone_app_settings"),()->system(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))).setTag("phone.power-settings");
        a.button(a.body,a.t("phone_audio"),()->open("audio"));
    }
    void system(Intent intent){try{a.startActivity(intent);}catch(ActivityNotFoundException missing){a.notice("phone_permission_missing");}}
    void preview(String id){
        if(!PhoneAlarms.notifications(a)){open("permissions");return;}
        try{if(PhonePlayback.scheduled()){a.notice("phone_stop_first");return;}int prayer=id.startsWith("call.")?Arrays.asList(PhoneStore.PRAYERS).indexOf(id.split("\\.")[1]):0;String voice=id.startsWith("call.")?settings.voice(prayer):settings.value(id);a.startForegroundService(PhonePlayback.intent(a,prayer,voice,true,"preview:"+System.nanoTime()));}catch(Exception error){a.notice("phone_audio_failed");}
    }
    void reset(LinearLayout parent){a.button(parent,a.t("phone_reset"),()->new AlertDialog.Builder(a).setMessage(a.t("phone_reset_confirm")).setNegativeButton(a.t("cancel"),null).setPositiveButton(a.t("reset"),(d,w)->{try{PhonePlayback.stop(a);PhoneAlarms.cancel(a);if(!PhoneStore.preferences(a).edit().clear().commit())throw new IllegalStateException();pending="";a.section="";a.recreate();}catch(Exception failure){a.notice("phone_save_failed");}}).show());}
}
