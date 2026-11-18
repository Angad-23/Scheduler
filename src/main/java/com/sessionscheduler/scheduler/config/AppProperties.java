package com.sessionscheduler.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "app." in application.yml, filled from environment variables in production. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String brandName, String baseUrl, String displayZone, String mailFrom, String mailFromName, boolean mailLogOnly,
                            String mailProvider, String brevoApiKey,
                            int verificationHours, boolean seedDemoProctors, int sessionMinutes, Admin admin) {
    /** True when email goes through Brevo's web API (works where SMTP ports are blocked, e.g. Render's free plan). */
    public boolean usesBrevo() {
        return "brevo".equalsIgnoreCase(mailProvider == null ? "" : mailProvider.trim());
    }

    /** The product name, "Session Scheduler" when APP_BRAND_NAME isn't set. */
    public String brandNameOrDefault() {
        return brandName == null || brandName.isBlank() ? "Session Scheduler" : brandName.trim();
    }

    /** How long a booked test is assumed to last, used to stop a proctor being double-booked. */
    public int sessionLength() {
        return sessionMinutes > 0 ? sessionMinutes : 60;
    }

    public record Admin(String email, String password, String name) { }
}
