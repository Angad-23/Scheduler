package com.sessionscheduler.scheduler.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * What a student fills in: the date, start time and time zone of their test.
 * Kept in the HTTP session between a failed POST and the redirected GET, so it must be Serializable.
 */
public class BookingForm implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "Choose a date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Choose a start time.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime time;

    @NotBlank(message = "Choose your time zone.")
    private String timeZone;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
}
