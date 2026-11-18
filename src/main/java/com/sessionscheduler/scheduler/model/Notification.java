package com.sessionscheduler.scheduler.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Column(name = "message", nullable = false, length = 300)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean readFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() { }

    public Notification(AppUser recipient, String message) {
        this.recipient = recipient;
        this.message = message.length() > 300 ? message.substring(0, 300) : message;
    }

    public Long getId() { return id; }
    public AppUser getRecipient() { return recipient; }
    public String getMessage() { return message; }
    public boolean isReadFlag() { return readFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
