package com.fairshare.fairshare.users;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    protected User() {}

    public User(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
