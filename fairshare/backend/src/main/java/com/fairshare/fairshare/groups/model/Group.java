package com.fairshare.fairshare.groups;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "groups")
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Group() {
    }

    public Group(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // Allow updating name when handling PATCH/update operations
    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
