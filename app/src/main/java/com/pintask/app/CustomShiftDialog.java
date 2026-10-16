package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/** Minimal custom shift creator. Existing saved shifts and legacy templates are untouched. */
final class CustomShiftDialog {
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm");

    static void show(Activity activity,ShiftRepository repository,Consumer<ShiftRepository.Template> saved) {
        LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int margin=(int)(18*activity.getResources().getDisplayMetrics().density);
        form.setPadding(margin,margin,margin,margin);
        EditText name=field(activity,form,"Shift name (e.g. Day / Evening)","My shift",50);
        EditText start=field(activity,form,"Starts at (24h HH:mm)","08:00",5);
        EditText end=field(activity,form,"Ends at (24h HH:mm)","20:00",5);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Create custom shift")
                .setMessage("Name your shift and choose its hours. Overnight shifts are supported. Add tasks after scheduling the shift.")
                .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save shift",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String title=name.getText().toString().trim();
            if(title.isEmpty()){name.setError("Enter a shift name");return;}
            try {
                int from=parse(start),to=parse(end);
                ShiftRepository.Template template=repository.addTemplate(title,from,to);
                dialog.dismiss();
                saved.accept(template);
            }catch(IllegalArgumentException e){
                start.setError("Enter valid 24-hour times, for example 08:00");
                android.widget.Toast.makeText(activity,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private static int parse(EditText input) {
        String value=input.getText().toString().trim();
        if(!value.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new IllegalArgumentException("Use HH:mm, such as 20:00 or 08:00");
        try {
            LocalTime time=LocalTime.parse(value,CLOCK);
            return time.getHour()*60+time.getMinute();
        }catch(DateTimeParseException e){throw new IllegalArgumentException("Use HH:mm, such as 20:00 or 08:00");}
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
