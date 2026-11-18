package com.sessionscheduler.scheduler.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The content of one email. Every email uses the same branded layout (templates/mail/message.html);
 * this object fills it. Build with {@link #to(String)}. Only plain strings go in, so it is safe to
 * hand to the background mail thread.
 */
public final class MailMessage {

    /** Colour of the status line under the heading. */
    public enum Tone { INFO, WAITING, SUCCESS }

    private final String to;
    private String subject = "";
    private String preheader = "";
    private String heading = "";
    private String greeting;
    private final List<String> paragraphs = new ArrayList<>();
    private String status;
    private Tone tone = Tone.INFO;
    private final Map<String, String> details = new LinkedHashMap<>();
    private String buttonLabel;
    private String buttonUrl;
    private final List<String> footnotes = new ArrayList<>();
    private String reason = "You're receiving this email because you have an account with us.";

    private MailMessage(String to) {
        this.to = to;
    }

    public static MailMessage to(String to) {
        return new MailMessage(to);
    }

    public MailMessage subject(String v) { this.subject = v; return this; }
    /** Short preview text shown next to the subject in the inbox list. */
    public MailMessage preheader(String v) { this.preheader = v; return this; }
    public MailMessage heading(String v) { this.heading = v; return this; }
    public MailMessage greeting(String v) { this.greeting = v; return this; }
    public MailMessage paragraph(String v) { this.paragraphs.add(v); return this; }
    public MailMessage status(String label, Tone tone) { this.status = label; this.tone = tone; return this; }
    /** Adds a row to the details table; blank values are skipped. */
    public MailMessage detail(String label, String value) {
        if (value != null && !value.isBlank()) {
            this.details.put(label, value);
        }
        return this;
    }
    public MailMessage button(String label, String url) { this.buttonLabel = label; this.buttonUrl = url; return this; }
    public MailMessage footnote(String v) { this.footnotes.add(v); return this; }
    /** The "why am I getting this" line in the footer. */
    public MailMessage reason(String v) { this.reason = v; return this; }

    public String getTo() { return to; }
    public String getSubject() { return subject; }
    public String getPreheader() { return preheader; }
    public String getHeading() { return heading; }
    public String getGreeting() { return greeting; }
    public List<String> getParagraphs() { return paragraphs; }
    public String getStatus() { return status; }
    public Tone getTone() { return tone; }
    public Map<String, String> getDetails() { return details; }
    public String getButtonLabel() { return buttonLabel; }
    public String getButtonUrl() { return buttonUrl; }
    public List<String> getFootnotes() { return footnotes; }
    public String getReason() { return reason; }

    /** Plain-text version, sent alongside the HTML for mail apps that don't show HTML. */
    public String toPlainText(String brandName) {
        StringBuilder sb = new StringBuilder();
        sb.append(heading).append("\n\n");
        if (greeting != null) {
            sb.append(greeting).append("\n\n");
        }
        if (status != null) {
            sb.append("Status: ").append(status).append("\n\n");
        }
        paragraphs.forEach(p -> sb.append(p).append("\n\n"));
        if (!details.isEmpty()) {
            details.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }
        if (buttonUrl != null) {
            sb.append(buttonLabel).append(": ").append(buttonUrl).append("\n\n");
        }
        footnotes.forEach(f -> sb.append(f).append("\n"));
        sb.append("\n--\n").append(brandName).append("\n").append(reason).append("\n");
        return sb.toString();
    }
}
