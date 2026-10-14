package com.asendus.api.duty;

import java.time.LocalTime;
import java.util.List;

public record ParsedDuties(List<ParsedDuty> duties, List<String> warnings) {
    /** One teacher (by surname, as written in the sheet) on one duty. kind is "OVT" or "SVT". */
    public record ParsedDuty(int weekday, int period, String kind, LocalTime starts, LocalTime ends,
                             String classGroup, String position, String surname) {}
}
