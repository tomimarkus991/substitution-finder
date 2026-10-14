package com.asendus.api.duty;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the surnames out of a duty cell such as "sööklas: Lepp, Pihlakas // 6.S ÕVT (12:35 -13:10)". */
final class DutyNames {
    private DutyNames() {}

    /** "10:45-11:15", also with spaces or an en dash around the dash ("12:55- 13:20"). */
    static final Pattern RANGE = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[-–]\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final String CANTEEN = "sööklas:";

    static boolean isCanteen(String cell) {
        return lower(cell.trim()).startsWith(CANTEEN);
    }

    /** Strip "sööklas:", cut at "//", drop "(…)", keep what follows " - ", split on , & / */
    static List<String> surnames(String cell) {
        String t = cell.trim();
        if (isCanteen(t)) t = t.substring(CANTEEN.length());
        int cut = t.indexOf("//");
        if (cut >= 0) t = t.substring(0, cut);
        t = PARENS.matcher(t).replaceAll(" ");
        int dash = t.lastIndexOf(" - ");
        if (dash >= 0) t = t.substring(dash + 3);
        return Arrays.stream(t.split("[,&/]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** True when the text surnames() throws away mentions another duty (a time range or "õvt"). */
    static boolean hidesDuty(String cell) {
        int cut = cell.indexOf("//");
        String head = cut >= 0 ? cell.substring(0, cut) : cell;
        StringBuilder dropped = new StringBuilder(cut >= 0 ? cell.substring(cut) : "");
        Matcher m = PARENS.matcher(head);
        while (m.find()) dropped.append(' ').append(m.group());
        String d = lower(dropped.toString());
        return d.contains("õvt") || RANGE.matcher(d).find();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
