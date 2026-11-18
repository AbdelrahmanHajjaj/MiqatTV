package tv.miqat.companion;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import tv.miqat.companion.core.SettingSpec;
import tv.miqat.core.UiContract;
import java.util.ArrayList;
import java.util.List;

/** Native touch bindings for the shared segmented/carousel/catalog choice policy. */
final class Choices {
    private Choices() {}
    static final class Option {
        final String value, label;
        Option(String value,String label){this.value=value;this.label=label;}
        @Override public String toString(){return label;}
    }
    static void add(MainActivity a,LinearLayout row,JSONObject control,SettingSpec spec,String value) {
        String id=spec.getId(),editor=id.startsWith("remote.")?"list":UiContract.INSTANCE.choiceEditor(control.optString("choiceSource",id));
        if(editor.equals("segments")) {
            HorizontalScrollView viewport=new HorizontalScrollView(a);viewport.setHorizontalScrollBarEnabled(false);
            RadioGroup track=new RadioGroup(a);track.setOrientation(LinearLayout.HORIZONTAL);track.setPadding(a.dp(4),a.dp(4),a.dp(4),a.dp(4));track.setBackground(a.background(a.ground,24));track.setTag(id+".segments");
            for(String option:spec.getOptions()) {
                RadioButton chip=new RadioButton(a);chip.setButtonDrawable(null);chip.setMinHeight(a.dp(48));chip.setMinWidth(a.dp(72));chip.setPadding(a.dp(14),0,a.dp(14),0);chip.setTextSize(13);chip.setTypeface(a.regular);chip.setGravity(Gravity.CENTER);
                chip.setText(a.catalog.value(a.profile(),id,option,a.ar));chip.setChecked(option.equals(value));chip.setTextColor(option.equals(value)?a.ground:a.muted);chip.setBackground(a.background(option.equals(value)?a.ink:Color.TRANSPARENT,22));
                chip.setEnabled(a.editable());chip.setContentDescription(a.title(control)+", "+chip.getText());chip.setTag(id+".option."+option);
                track.addView(chip,new RadioGroup.LayoutParams(-2,-2));chip.setOnClickListener(v->{if(!option.equals(value))a.selectChoice(id,option);});
            }
            viewport.addView(track);row.addView(viewport);viewport.setTag(id+".edit");viewport.setEnabled(a.editable());
        } else if(editor.equals("carousel")) {
            int index=spec.getOptions().indexOf(value);LinearLayout track=a.horizontal(row);track.setTag(id+".edit");track.setEnabled(a.editable());
            Button previous=a.button(track,a.ar?"›":"‹",()->a.selectChoice(id,spec.getOptions().get(index-1)));
            a.square(previous);previous.setTag(id+".previous");previous.setContentDescription(a.t("previous")+" "+a.title(control));previous.setEnabled(a.editable()&&index>0);
            TextView selected=a.text(a.catalog.value(a.profile(),id,value,a.ar),16,a.ink);selected.setGravity(Gravity.CENTER);selected.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);selected.setMinHeight(a.dp(48));selected.setBackground(a.background(a.color("lapis-700"),24));selected.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));
            track.addView(selected,new LinearLayout.LayoutParams(0,-2,1));selected.setTag(id+".value");
            Button next=a.button(track,a.ar?"‹":"›",()->a.selectChoice(id,spec.getOptions().get(index+1)));
            a.square(next);next.setTag(id+".next");next.setContentDescription(a.t("next")+" "+a.title(control));next.setEnabled(a.editable()&&index+1<spec.getOptions().size());
        } else {
            AutoCompleteTextView field=new AutoCompleteTextView(a);field.setId(View.generateViewId());field.setTag(id+".dropdown");field.setSingleLine(true);field.setThreshold(0);field.setSelectAllOnFocus(true);field.setTextSize(15);field.setTypeface(a.regular);field.setTextColor(a.accent);field.setMinHeight(a.dp(48));field.setHint(a.t("search"));
            List<Option> options=new ArrayList<>();for(String option:spec.getOptions())options.add(new Option(option,a.catalog.value(a.profile(),id,option,a.ar)));
            ArrayAdapter<Option> adapter=new ArrayAdapter<>(a,android.R.layout.simple_dropdown_item_1line,options);field.setAdapter(adapter);field.setText(a.catalog.value(a.profile(),id,value,a.ar),false);field.setEnabled(a.editable());
            field.setDropDownBackgroundDrawable(a.background(a.surface,12));field.setOnClickListener(v->field.showDropDown());field.setOnItemClickListener((parent,view,position,item)->{
                Option option=(Option)parent.getItemAtPosition(position);field.dismissDropDown();
                ((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);
                a.selectChoice(id,option.value);
            });
            field.setOnFocusChangeListener((view,focused)->{if(!focused)field.setText(a.catalog.value(a.profile(),id,value,a.ar),false);});
            ((TextView)((LinearLayout)row.getChildAt(0)).getChildAt(0)).setLabelFor(field.getId());LinearLayout anchor=a.horizontal(row);anchor.addView(field,new LinearLayout.LayoutParams(0,-2,1));
            Button open=a.button(anchor,"⌄",()->{adapter.getFilter().filter(null,count->field.showDropDown());field.requestFocus();});a.square(open);open.setContentDescription(a.title(control));open.setEnabled(a.editable());open.setTag(id+".edit");
            if(id.equals("reminders.voice")||id.equals("reminders.fajr-voice")) {
                LinearLayout affordance=a.horizontal(row);
                Button preview=a.bindAction(a.iconButton(affordance,"play",a.t("preview_audio"),()->a.playAudio(value)),"preview-audio");preview.setTag(id+".preview");
                Button stop=a.bindAction(a.iconButton(affordance,"stop",a.t("stop_audio"),a::stopAudio),"stop-audio");
                if(a.isPending(id)) {
                    row.addView(a.text(a.t("voice_draft"),12,a.muted));
                    LinearLayout actions=a.horizontal(row);
                    Button done=a.bindAction(a.button(actions,a.t("done"),a::commitPending),"save-edit");a.flex(done);done.setTag(id+".save");
                    Button cancel=a.button(actions,a.t("cancel"),()->{a.stopAudio();a.clearPending();a.render();});a.flex(cancel);cancel.setTag(id+".cancel");
                }
            }
        }
    }
}
