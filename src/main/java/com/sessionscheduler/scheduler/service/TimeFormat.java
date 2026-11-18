package com.sessionscheduler.scheduler.service;

import com.sessionscheduler.scheduler.config.AppProperties;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Formats UTC instants for people. Templates call it as ${@fmt.inZone(...)} and ${@fmt.display(...)}. */
@Component("fmt")
public class TimeFormat {
    private static final DateTimeFormatter PATTERN =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final ZoneId displayZone;

    public TimeFormat(AppProperties props) {
        this.displayZone = zoneOrUtc(props.displayZone());
    }

    public static ZoneId zoneOrUtc(String id) {
        try {
            return ZoneId.of(id);
        } catch (DateTimeException | NullPointerException e) {
            return ZoneId.of("UTC");
        }
    }

    public ZoneId displayZone() { return displayZone; }

    /** The instant in the given zone, for example "Wed, 18 Nov 2026, 10:00 EST". */
    public String inZone(Instant instant, String zoneId) {
        if (instant == null) {
            return "";
        }
        ZoneId zone = zoneOrUtc(zoneId);
        return PATTERN.format(instant.atZone(zone)) + " " + abbr(instant, zone.getId());
    }

    /** Short zone name at that moment: "EST" in winter, "EDT" in summer, "IST" for India. */
    public String abbr(Instant instant, String zoneId) {
        return DateTimeFormatter.ofPattern("zzz", Locale.US).format((instant == null ? Instant.now() : instant).atZone(zoneOrUtc(zoneId)));
    }

    /** Short name of the portal's zone at that moment, e.g. "IST". */
    public String displayAbbr(Instant instant) {
        return abbr(instant, displayZone.getId());
    }

    private ZoneId zone(String zoneId) {
        return zoneId == null ? displayZone : zoneOrUtc(zoneId);
    }

    /** "Nov" for the calendar tile. A null zone means the portal's display zone. */
    public String month(Instant instant, String zoneId) {
        return instant == null ? "" : DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH).format(instant.atZone(zone(zoneId)));
    }

    /** "18" for the calendar tile. */
    public String day(Instant instant, String zoneId) {
        return instant == null ? "" : String.valueOf(instant.atZone(zone(zoneId)).getDayOfMonth());
    }

    /** "Wednesday 18 November". */
    public String longDate(Instant instant, String zoneId) {
        return instant == null ? "" : DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH).format(instant.atZone(zone(zoneId)));
    }

    /** "Wed 18 Nov". */
    public String shortDate(Instant instant, String zoneId) {
        return instant == null ? "" : DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(instant.atZone(zone(zoneId)));
    }

    /** "10:00–11:30". */
    public String timeRange(Instant start, int minutes, String zoneId) {
        if (start == null) {
            return "";
        }
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
        ZoneId z = zone(zoneId);
        return hm.format(start.atZone(z)) + "\u2013" + hm.format(start.plusSeconds(minutes * 60L).atZone(z));
    }

    /** "Kolkata time (GMT+5:30)". */
    public String zoneLabel(Instant instant, String zoneId) {
        ZoneId z = zone(zoneId);
        String id = z.getId();
        String city = (id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id).replace('_', ' ');
        int secs = z.getRules().getOffset(instant == null ? Instant.now() : instant).getTotalSeconds();
        String offset = secs == 0 ? "GMT"
            : String.format(Locale.ROOT, "GMT%s%d%s", secs < 0 ? "-" : "+", Math.abs(secs) / 3600,
                Math.abs(secs) % 3600 == 0 ? "" : String.format(Locale.ROOT, ":%02d", Math.abs(secs) % 3600 / 60));
        return city + " time (" + offset + ")";
    }

    /** True when the zone shows the same wall-clock time as the portal's display zone at that instant. */
    public boolean sameAsDisplay(Instant instant, String zoneId) {
        Instant at = instant == null ? Instant.now() : instant;
        return zone(zoneId).getRules().getOffset(at).equals(displayZone.getRules().getOffset(at));
    }

    /** One part of the instant in a zone, e.g. part(t, "Asia/Kolkata", "d MMM") gives "18 Nov". */
    public String part(Instant instant, String zoneId, String pattern) {
        return instant == null ? "" : DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(instant.atZone(zoneOrUtc(zoneId)));
    }

    /** One part of the instant in the portal's display zone. */
    public String displayPart(Instant instant, String pattern) {
        return part(instant, displayZone.getId(), pattern);
    }

    /** 90 -> "1 hr 30 min", 120 -> "2 hrs", 45 -> "45 min". */
    public String length(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        String hours = h == 0 ? "" : h + (h == 1 ? " hr" : " hrs");
        String mins = m == 0 ? "" : m + " min";
        return (hours + " " + mins).trim();
    }

    public boolean isDisplayZone(String zoneId) {
        return zoneOrUtc(zoneId).equals(displayZone);
    }

    /** The instant in the portal's display zone. */
    public String display(Instant instant) {
        return instant == null ? "" : PATTERN.format(instant.atZone(displayZone));
    }
}
