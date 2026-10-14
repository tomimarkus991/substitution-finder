package com.asendus.api.timetable;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

/** A teacher's identity across timetable versions is their normalized name. */
public final class TeacherKey {
    private TeacherKey() {}

    /** Estonian alphabetical order (… s š z ž t … w õ ä ö ü …) for display names. */
    public static final Comparator<String> DISPLAY_ORDER;
    static {
        Collator et = Collator.getInstance(Locale.of("et"));
        DISPLAY_ORDER = et::compare; // RuleBasedCollator.compare is synchronized, so sharing it is safe
    }

    public static String of(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** aSc prints "Surname Firstname(s)"; tables use "Firstname(s) Surname". */
    public static String displayName(String ascName) {
        String n = Normalizer.normalize(ascName, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
        int space = n.indexOf(' ');
        return space < 0 ? n : n.substring(space + 1) + " " + n.substring(0, space);
    }
}
