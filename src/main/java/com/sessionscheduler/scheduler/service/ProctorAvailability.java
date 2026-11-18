package com.sessionscheduler.scheduler.service;

/** One row of /api/proctors/available: whether a proctor is free for a given session. */
public record ProctorAvailability(Long id, String name, boolean available, String busyWith) { }
