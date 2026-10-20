package com.pintask.app;

import android.content.Context;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Three explicitly selected speech locales. Unrecognized schedules are not silently guessed. */
final class VoiceLanguages {
    static final String[] TAGS={"en-US","de-DE","hr-HR"};
    static final String[] NAMES={"English","Deutsch","Hrvatski"};
    static final String[] SHORT={"EN","DE","HR"};
    private static final String PREF="voice_language_v37";
    private VoiceLanguages(){}

    static int selection(Context c){
        int index=c.getSharedPreferences("settings",Context.MODE_PRIVATE).getInt(PREF,0);
        return Math.max(0,Math.min(TAGS.length-1,index));
    }
    static void select(Context c,int index){
        if(index<0||index>=TAGS.length)throw new IllegalArgumentException("Unknown voice language");
        c.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putInt(PREF,index).apply();
    }
    static String language(Context c){return TAGS[selection(c)];}
    static String prompt(String tag){
        switch(tag){
            case "de-DE":return "Sag, was du erledigen musst und wann";
            case "hr-HR":return "Reci što trebaš napraviti i kada";
            default:return "Tell PinTask what to do and when";
        }
    }

    static SpokenTask parse(String spoken,ZonedDateTime now,String tag) {
        if(!"de-DE".equals(tag)&&!"hr-HR".equals(tag)&&!"en-US".equals(tag))
            throw new IllegalArgumentException("Select English, German, or Croatian.");
        String adapted=normalize(spoken,tag);
        SpokenTask parsed=SpokenTask.parse(adapted,now);
        // Preserve exactly what the recognizer returned in the confirmation transcript.
        return parsed.withOriginal(spoken.trim());
    }

    static String normalize(String input,String tag) {
        if(input==null||input.trim().isEmpty())return input;
        if("en-US".equals(tag))return input;
        String s=input.trim().replaceAll("\\s+"," ");
        if("de-DE".equals(tag)){
            s=replace(s,"^(?:bitte\\s+)?(?:erinnere\\s+mich\\s+(?:daran\\s*,?\\s*(?:dass\\s+ich\\s+)?|an\\s+|zu\\s+)?|ich\\s+(?:muss|möchte|will)\\s+|denk\\s+daran\\s*,?\\s*)","");
            s=replace(s,"\\b(?:eine?\\s+)?halbe\\s+stunde\\s+(?:vorher|früher|davor)\\b","half an hour before");
            s=replace(s,"\\b(?:eine?\\s+)?halbe\\s+stunde\\b","30 minutes");
            s=replace(s,"\\b(?:jeden|jede)\\s+zweiten\\b","every other");
            s=replace(s,"\\balle\\s+(?:zwei|2)\\s+wochen\\b","every two weeks");
            s=replace(s,"\\b(?:jeden|jede|jedem|jede[nrsm]?)\\b","every");
            s=replace(s,"\\b(?:alle\\s+)?(\\d{1,2})\\s+stunden\\b","every $1 hours");
            s=replace(s,"\\b(?:täglich|taeglich)\\b","every day");
            s=replace(s,"\\b(?:heute)\\b","today");
            s=replace(s,"\\b(?:morgen)\\b","tomorrow");
            s=replace(s,"\\b(?:nächsten|naechsten|nächste|kommenden)\\s+(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)\\b","next $1");
            s=replace(s,"\\b(?:montags?|dienstags?|mittwochs?|donnerstags?|freitags?|samstags?|sonntags?)\\b", match->{
                String n=match.toLowerCase(Locale.ROOT).replaceAll("s$","");
                switch(n){case "montag":return "Monday";case "dienstag":return "Tuesday";
                    case "mittwoch":return "Wednesday";case "donnerstag":return "Thursday";
                    case "freitag":return "Friday";case "samstag":return "Saturday";
                    case "sonntag":return "Sunday";default:return match;}
            });
            s=replace(s,"\\b(?:tag)\\b","day");
            s=replace(s,"\\b(?:stunde)\\b","hour");
            s=replace(s,"\\b(\\d{1,4}|[\\p{L}ß]+)\\s+(?:minuten?|mins?)\\s+(?:vorher|früher|davor)\\b", m->numeral(m.group(1),"de-DE")+" minutes before");
            s=replace(s,"\\b(\\d{1,3}|[\\p{L}ß]+)\\s+(?:stunden?)\\s+(?:vorher|früher|davor)\\b", m->numeral(m.group(1),"de-DE")+" hours before");
            s=replace(s,"\\bin\\s+(\\d{1,4}|[\\p{L}ß]+)\\s+(?:minuten?|mins?)\\b",m->"in "+numeral(m.group(1),"de-DE")+" minutes");
            s=replace(s,"\\bin\\s+(\\d{1,3}|[\\p{L}ß]+)\\s+(?:stunden?)\\b",m->"in "+numeral(m.group(1),"de-DE")+" hours");
            s=replace(s,"\\bum\\s+(\\d{1,2}|[\\p{L}ß]+)(:\\d{1,2})?\\s*(?:uhr)?\\b",m->"at "+numeral(m.group(1),"de-DE")+(m.group(2)==null?"":m.group(2)));
            s=replace(s,"\\bum\\s+(\\d{1,2}|[\\p{L}ß]+)(:\\d{1,2})?\\s*$",m->"at "+numeral(m.group(1),"de-DE")+(m.group(2)==null?"":m.group(2)));
        } else {
            s=replace(s,"^(?:molim\\s+te\\s+)?(?:podsjeti\\s+me\\s+(?:da\\s+)?|podsjeti\\s+me\\s+|moram\\s+|trebam\\s+|treba\\s+mi\\s+)","");
            s=replace(s,"\\bpola\\s+sata\\s+(?:prije|ranije)\\b","half an hour before");
            s=replace(s,"\\bpola\\s+sata\\b","30 minutes");
            s=replace(s,"\\b(?:svaki\\s+drugi|svakog\\s+drugog)\\b","every other");
            s=replace(s,"\\b(?:svaka\\s+(?:dva|2)\\s+tjedna|svaka\\s+(?:dva|2)\\s+tjeda[nn]a)\\b","every two weeks");
            s=replace(s,"\\b(?:svaki|svakog|svaka|svako)\\b","every");
            s=replace(s,"\\b(?:svakodnevno)\\b","every day");
            s=replace(s,"\\b(?:danas)\\b","today");
            s=replace(s,"\\b(?:sutra)\\b","tomorrow");
            s=replace(s,"\\b(?:sljedeći|sljedeceg|sljedećeg|idući|idućeg)\\s+(ponedjeljak|utorak|srijeda|četvrtak|petak|subota|nedjelja)\\b","next $1");
            s=replace(s,"\\b(?:ponedjeljkom|ponedjeljka|ponedjeljak)\\b","Monday");
            s=replace(s,"\\b(?:utorkom|utorka|utorak)\\b","Tuesday");
            s=replace(s,"\\b(?:srijedom|srijede|srijeda)\\b","Wednesday");
            s=replace(s,"\\b(?:četvrtkom|četvrtka|četvrtak)\\b","Thursday");
            s=replace(s,"\\b(?:petkom|petka|petak)\\b","Friday");
            s=replace(s,"\\b(?:subotom|subote|subota)\\b","Saturday");
            s=replace(s,"\\b(?:nedjeljom|nedjelje|nedjelja)\\b","Sunday");
            s=replace(s,"\\b(?:dan)\\b","day");
            s=replace(s,"\\b(?:sat)\\b","hour");
            s=replace(s,"\\b(\\d{1,4}|[\\p{L}]+)\\s+(?:minuta|minutu|minute)\\s+(?:prije|ranije)\\b",m->numeral(m.group(1),"hr-HR")+" minutes before");
            s=replace(s,"\\b(\\d{1,3}|[\\p{L}]+)\\s+(?:sati|sat)\\s+(?:prije|ranije)\\b",m->numeral(m.group(1),"hr-HR")+" hours before");
            s=replace(s,"\\bza\\s+(\\d{1,4}|[\\p{L}]+)\\s+(?:minuta|minutu|minute)\\b",m->"in "+numeral(m.group(1),"hr-HR")+" minutes");
            s=replace(s,"\\bza\\s+(\\d{1,3}|[\\p{L}]+)\\s+(?:sati|sat)\\b",m->"in "+numeral(m.group(1),"hr-HR")+" hours");
            s=replace(s,"\\bu\\s+(\\d{1,2}|[\\p{L}]+)(:\\d{1,2})?\\s*(?:sati|sat)?\\b",m->"at "+numeral(m.group(1),"hr-HR")+(m.group(2)==null?"":m.group(2)));
            s=replace(s,"\\bu\\s+(\\d{1,2}|[\\p{L}]+)(:\\d{1,2})?\\s*$",m->"at "+numeral(m.group(1),"hr-HR")+(m.group(2)==null?"":m.group(2)));
        }
        return s.replaceAll("\\s+"," ").trim();
    }

    private static String numeral(String text,String lang){
        String n=text.toLowerCase(Locale.ROOT);
        if(n.matches("\\d+"))return n;
        if("de-DE".equals(lang)){
            switch(n){
                case "ein":case "eins":case "eine":case "einen":case "einer":case "einem":return "1";
                case "zwei":return "2";case "drei":return "3";case "vier":return "4";
                case "fünf":case "fuenf":return "5";case "sechs":return "6";
                case "sieben":return "7";case "acht":return "8";case "neun":return "9";
                case "zehn":return "10";case "elf":return "11";case "zwölf":case "zwoelf":return "12";
                case "dreizehn":return "13";case "vierzehn":return "14";case "fünfzehn":return "15";
                case "sechzehn":return "16";case "siebzehn":return "17";
                case "achtzehn":return "18";case "neunzehn":return "19";
                case "zwanzig":return "20";case "dreißig":case "dreissig":return "30";
                case "vierzig":return "40";case "fünfzig":return "50";case "sechzig":return "60";
                default:return text;
            }
        }else{
            switch(n){
                case "jedan":case "jedna":case "jednu":case "jedno":return "1";
                case "dva":case "dvije":return "2";case "tri":return "3";case "četiri":return "4";
                case "pet":return "5";case "šest":return "6";case "sedam":return "7";
                case "osam":return "8";case "devet":return "9";case "deset":return "10";
                case "jedanaest":return "11";case "dvanaest":return "12";
                case "trinaest":return "13";case "četrnaest":return "14";case "petnaest":return "15";
                case "dvadeset":return "20";case "trideset":return "30";
                case "četrdeset":return "40";case "pedeset":return "50";case "šezdeset":return "60";
                default:return text;
            }
        }
    }
    private static String replace(String s,String pattern,String output){
        return Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)
                .matcher(s).replaceAll(output);
    }
    private interface WordTransform{String apply(String match);}
    private interface GroupTransform{String apply(Matcher match);}
    private static String replace(String s,String pattern,WordTransform action){
        return replace(s,pattern,match->action.apply(match.group()),true);
    }
    private static String replace(String s,String pattern,GroupTransform action){
        return replace(s,pattern,action,true);
    }
    private static String replace(String s,String pattern,GroupTransform action,boolean unused){
        Matcher matcher=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(s);
        StringBuffer out=new StringBuffer();
        while(matcher.find())matcher.appendReplacement(out,Matcher.quoteReplacement(action.apply(matcher)));
        matcher.appendTail(out);
        return out.toString();
    }
}
