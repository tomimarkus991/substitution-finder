package com.asendus.api.duty;

import java.time.LocalTime;

/** How a duty is shown: "ÕVT 10:45–11:15", with " · pos 4" on day rows. */
public final class DutyLabels {
    private DutyLabels() {}

    public static String kindName(String kind) {
        return "OVT".equals(kind) ? "ÕVT" : "SVT";
    }

    public static String label(Duty d) {
        return kindName(d.getKind()) + " " + hm(d.getStarts()) + "–" + hm(d.getEnds());
    }

    public static String labelWithPosition(Duty d) {
        return d.getPosition().isBlank() ? label(d) : label(d) + " · pos " + d.getPosition();
    }

    private static String hm(LocalTime t) {
        return t.getHour() + ":" + String.format("%02d", t.getMinute());
    }
}
