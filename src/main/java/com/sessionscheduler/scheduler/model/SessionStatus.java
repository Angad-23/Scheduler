package com.sessionscheduler.scheduler.model;

/** Session states, with the words people see on screen and in the Excel export. */
public enum SessionStatus {
    REQUESTED("Not assigned"),
    ASSIGNED("Proctor assigned"),
    CANCELLED("Cancelled");

    private final String label;

    SessionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
