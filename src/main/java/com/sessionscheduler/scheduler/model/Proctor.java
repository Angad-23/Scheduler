package com.sessionscheduler.scheduler.model;

import jakarta.persistence.*;

@Entity
@Table(name = "proctors")
public class Proctor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Proctor() { }

    public Proctor(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
