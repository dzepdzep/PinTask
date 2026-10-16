package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;

/** Custom shift creator using Android clock pickers—no HH:mm typing required. */
final class CustomShiftDialog {
    static void show(Activity activity,ShiftRepository repository,Consumer<ShiftRepository.Template> saved) {
        show(activity,repository,saved,()->{},false);
    }

    static void show(Activity activity,ShiftRepository repository,Consumer<ShiftRepository.Template> saved,
            Runnable onCancel,boolean guided) {
        final boolean[] successful={false};
        LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int margin=(int)(18*activity.getResources().getDisplayMetrics().density);
        form.setPadding(margin,margin,margin,margin);
        if(guided){
            TextView header=new TextView(activity);
            header.setText("GUIDE · Create your actual shift\n1. Type a name\n2. Pick start and end times using the clock\n3. Tap Save shift");
            header.setTextColor(Color.rgb(20,94,78));header.setTextSize(15);
            header.setPadding(0,0,0,14);form.addView(header);
        }
        EditText name=field(activity,form,"Shift name (e.g. Day / Evening)","My shift",50);
        int[] from={8*60},to={20*60};
        Button start=clockField(activity,form,"Shift begins",from);
        Button end=clockField(activity,form,"Shift ends",to);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Create custom shift")
                .setMessage("Name your shift, then tap each clock to pick a start and end time. Overnight shifts work too.")
                .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save shift",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String title=name.getText().toString().trim();
            if(title.isEmpty()){name.setError("Enter a shift name");return;}
            try {
                ShiftRepository.Template template=repository.addTemplate(title,from[0],to[0]);
                successful[0]=true;dialog.dismiss();
                saved.accept(template);
            }catch(IllegalArgumentException e){
                android.widget.Toast.makeText(activity,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        }));
        dialog.setOnDismissListener(d->{if(!successful[0])onCancel.run();});
        dialog.show();
    }

    static Button clockField(Activity activity,LinearLayout form,String title,int[] selected) {
        TextView caption=new TextView(activity);caption.setText(title);
        caption.setTextColor(Color.DKGRAY);caption.setTextSize(13);form.addView(caption);
        Button button=new Button(activity);
        button.setAllCaps(false);button.setText(clock(selected[0]));
        button.setContentDescription("Choose "+title.toLowerCase(Locale.ROOT));
        button.setOnClickListener(v->new TimePickerDialog(activity,(view,hour,minute)->{
            selected[0]=hour*60+minute;
            button.setText(clock(selected[0]));
        },selected[0]/60,selected[0]%60,true).show());
        form.addView(button,new LinearLayout.LayoutParams(-1,-2));
        return button;
    }

    private static String clock(int minute){
        return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);
    }

    private static EditText field(Activity activity,LinearLayout form,String label,String initial,int limit) {
        TextView caption=new TextView(activity);caption.setText(label);caption.setTextColor(Color.DKGRAY);
        caption.setTextSize(13);form.addView(caption);
        EditText input=new EditText(activity);input.setSingleLine(true);
        input.setText(initial);input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        form.addView(input,new LinearLayout.LayoutParams(-1,-2));
        return input;
    }
}
