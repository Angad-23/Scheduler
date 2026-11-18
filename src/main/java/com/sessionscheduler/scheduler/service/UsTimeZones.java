package com.sessionscheduler.scheduler.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The time zones students can pick (all students are in the US). Each maps to a real IANA zone,
 * so daylight saving (EST/EDT, PST/PDT, ...) is handled automatically.
 */
public final class UsTimeZones {
    public static final String DEFAULT = "America/New_York";

    private static final Map<String, String> OPTIONS = new LinkedHashMap<>();
    static {
        OPTIONS.put("America/New_York", "Eastern (EST/EDT)");
        OPTIONS.put("America/Chicago", "Central (CST/CDT)");
        OPTIONS.put("America/Denver", "Mountain (MST/MDT)");
        OPTIONS.put("America/Phoenix", "Arizona (MST, no daylight saving)");
        OPTIONS.put("America/Los_Angeles", "Pacific (PST/PDT)");
        OPTIONS.put("America/Anchorage", "Alaska (AKST/AKDT)");
        OPTIONS.put("Pacific/Honolulu", "Hawaii (HST)");
    }

    private UsTimeZones() { }

    /** Zone id -> label, in display order. */
    public static Map<String, String> options() {
        return OPTIONS;
    }

    public static boolean isAllowed(String zoneId) {
        return zoneId != null && OPTIONS.containsKey(zoneId);
    }
}
