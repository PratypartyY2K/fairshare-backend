package com.fairshare.fairshare.groups.model;

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

    //noinspection FieldMayBeFinal
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(updatable = false)
    private Long createdByUserId;

    protected Group() {
    }

    public Group(String name) {
        this(name, null);
    }

    public Group(String name, Long createdByUserId) {
        this.name = name;
        this.createdByUserId = createdByUserId;
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

    public Long getCreatedByUserId() {
        return createdByUserId;
    }
}
