package com.fairshare.fairshare.groups.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "groups")
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Allow updating name when handling PATCH/update operations
    @Setter
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private final Instant createdAt = Instant.now();

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

}
