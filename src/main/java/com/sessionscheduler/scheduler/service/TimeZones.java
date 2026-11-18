package com.sessionscheduler.scheduler.service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Time zones grouped by region (Asia, Europe, America, ...) for the Region → Time zone dropdowns. */
public final class TimeZones {
    private static final List<String> REGIONS = List.of(
        "Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific");

    private static final Map<String, List<String>> BY_REGION = build();

    private TimeZones() { }

    private static Map<String, List<String>> build() {
        Map<String, List<String>> map = new TreeMap<>(ZoneId.getAvailableZoneIds().stream()
            .filter(id -> id.indexOf('/') > 0 && REGIONS.contains(id.substring(0, id.indexOf('/'))))
            .sorted()
            .collect(Collectors.groupingBy(id -> id.substring(0, id.indexOf('/')))));
        map.put("UTC", List.of("UTC"));
        return map;
    }

    public static List<String> regions() {
        return List.copyOf(BY_REGION.keySet());
    }

    public static List<String> zones(String region) {
        return BY_REGION.getOrDefault(region == null ? "" : region, List.of());
    }

    public static List<String> all() {
        return BY_REGION.values().stream().flatMap(List::stream).sorted().toList();
    }

    public static String regionOf(String zoneId) {
        if (zoneId == null) {
            return "UTC";
        }
        int slash = zoneId.indexOf('/');
        return slash > 0 ? zoneId.substring(0, slash) : "UTC";
    }
}
