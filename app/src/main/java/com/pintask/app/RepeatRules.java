package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Retains legacy repeat values; custom values encode a bounded calendar interval. */
final class RepeatRules {
    static final String[] LABELS={"Never","Daily","Weekly","Monthly","Every 2 weeks","Every other day","Every 3 days","Every 3 weeks","Every 4 weeks","Yearly","Weekdays","Weekends","Mon / Wed / Fri"};
    static boolean valid(int code) {
        return code>=0 && code<LABELS.length || code>=1001 && code<=1365 || code>=2001 && code<=2052 || code>=3001 && code<=3024 || code>=4001 && code<=4127 || code>=5001 && code<=5024;
    }
    static int amount(int code) {
        if(code>=1000)return code%1000;
        switch(code) {case 4:case 5:return 2;case 6:case 7:return 3;case 8:return 4;default:return 1;}
    }
    static int field(int code) {
        if(code>=5000)return Calendar.HOUR_OF_DAY;
        if(code>=3000 || code==3)return Calendar.MONTH;
        if(code>=2000 || code==2 || code==4 || code==7 || code==8)return Calendar.WEEK_OF_YEAR;
        if(code==9)return Calendar.YEAR;
        return Calendar.DAY_OF_MONTH;
    }
    static String label(int code) {
        if(!valid(code))return "Unknown repeat";
        if(code<LABELS.length)return LABELS[code];
        if(code>=5000)return "Every "+amount(code)+" "+(amount(code)==1?"hour":"hours");
        if(code>=4000) {StringBuilder text=new StringBuilder();String[] days={"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};for(int i=0;i<7;i++)if(((code-4000)&(1<<i))!=0){if(text.length()>0)text.append(" / ");text.append(days[i]);}return text.toString();}
        String unit=code>=3000?"month":code>=2000?"week":"day";
        return "Every "+amount(code)+" "+unit+(amount(code)==1?"":"s");
    }
    static void bind(Activity activity,Spinner spinner,int code) {
        List<String> labels=new ArrayList<>(Arrays.asList(LABELS));
        if(code>=1000)labels.add(label(code));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setTag(code);spinner.setAdapter(adapter);spinner.setSelection(code>=1000?LABELS.length:code);
    }
    static int selected(Spinner spinner) {
        int position=spinner.getSelectedItemPosition();
        return position<LABELS.length?position:(Integer)spinner.getTag();
    }
    static int days(int code) {return code==10?31:code==11?96:code==12?21:code>=4000 && code<5000?code-4000:0;}
    static long next(long base,int code,long now) {
        if(!valid(code) || code==0)throw new IllegalArgumentException("Choose a repeat interval");
        Calendar calendar=Calendar.getInstance();calendar.setTimeInMillis(base);
        int mask=days(code);
        do {
            calendar.add(mask==0?field(code):Calendar.DAY_OF_MONTH,mask==0?amount(code):1);
        }while(calendar.getTimeInMillis()<=now || (mask!=0 && (mask & (1<<((calendar.get(Calendar.DAY_OF_WEEK)+5)%7)))==0));
        return calendar.getTimeInMillis();
    }
    static void weekdays(Activity activity,int initial,IntConsumer save) {
        boolean[] checked=new boolean[7];int mask=days(initial);for(int i=0;i<7;i++)checked[i]=(mask&(1<<i))!=0;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Repeat on selected weekdays")
            .setMultiChoiceItems(new String[]{"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"},checked,(d,i,value)->checked[i]=value)
            .setNegativeButton("Cancel",null).setPositiveButton("Use weekdays",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int chosen=0;for(int i=0;i<7;i++)if(checked[i])chosen|=1<<i;if(chosen==0){Toast.makeText(activity,"Choose at least one weekday",Toast.LENGTH_SHORT).show();return;}save.accept(4000+chosen);dialog.dismiss();}));dialog.show();
    }
    static long first(long due,int code) {
        int mask=days(code);if(mask==0 || due<=0)return due;Calendar day=Calendar.getInstance();day.setTimeInMillis(due);return (mask&(1<<((day.get(Calendar.DAY_OF_WEEK)+5)%7)))!=0?due:next(due,code,due);
    }
    static void custom(Activity activity,int initial,IntConsumer save) {
        if(days(initial)!=0)initial=0;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(20*activity.getResources().getDisplayMetrics().density);body.setPadding(padding,padding,padding,0);
        EditText count=new EditText(activity);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setHint("Repeat every…");count.setText(""+(initial==0?2:amount(initial)));body.addView(count);
        Spinner unit=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,new String[]{"Days (1–365)","Weeks (1–52)","Months (1–24)","Hours (1–24)"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);unit.setAdapter(adapter);
        int field=field(initial);unit.setSelection(field==Calendar.HOUR_OF_DAY?3:field==Calendar.MONTH?2:field==Calendar.WEEK_OF_YEAR?1:0);body.addView(unit);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Custom repeat").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Use repeat",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                int number=Integer.parseInt(count.getText().toString());int code=(unit.getSelectedItemPosition()+1)*1000+number;
                int max=new int[]{365,52,24,24}[unit.getSelectedItemPosition()];
                if(number<1 || number>max)throw new NumberFormatException();save.accept(code);dialog.dismiss();
            }catch(NumberFormatException e){count.setError("Enter a number in the selected range");}
        }));dialog.show();
    }
}
