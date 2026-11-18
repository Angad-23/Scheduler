package com.sessionscheduler.scheduler.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One proctoring session a student asked for. The start time is stored in UTC. */
@Entity
@Table(name = "exam_sessions")
public class ExamSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.REQUESTED;

    @ManyToOne
    @JoinColumn(name = "proctor_id")
    private Proctor proctor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Instant getEndsAt() { return startsAt.plusSeconds(durationMinutes * 60L); }

    public Long getId() { return id; }
    public AppUser getStudent() { return student; }
    public void setStudent(AppUser student) { this.student = student; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Proctor getProctor() { return proctor; }
    public void setProctor(Proctor proctor) { this.proctor = proctor; }
    public Instant getCreatedAt() { return createdAt; }
}
