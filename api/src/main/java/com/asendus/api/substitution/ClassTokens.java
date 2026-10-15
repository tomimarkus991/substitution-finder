package com.asendus.api.substitution;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** A lesson's classes text ("2.3/2.4/" from TimetablePdfParser.joinClasses) as a set of class names. */
public final class ClassTokens {
    private ClassTokens() {}

    public static Set<String> of(String classes) {
        return Arrays.stream(classes.split("/")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /** Do the two lessons share a class? */
    public static boolean overlap(String a, String b) {
        Set<String> left = of(a);
        return of(b).stream().anyMatch(left::contains);
    }
}
