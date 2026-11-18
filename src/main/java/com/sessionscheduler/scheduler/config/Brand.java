package com.sessionscheduler.scheduler.config;

import org.springframework.stereotype.Component;

/**
 * The product name shown in the app, page titles and emails. Set it with APP_BRAND_NAME,
 * e.g. "Trivium Scheduler". Templates use it as ${@brand.name}.
 */
@Component("brand")
public class Brand {
    private final String name;

    public Brand(AppProperties props) {
        String n = props.brandName();
        this.name = n == null || n.isBlank() ? "Session Scheduler" : n.trim();
    }

    public String getName() {
        return name;
    }
}
