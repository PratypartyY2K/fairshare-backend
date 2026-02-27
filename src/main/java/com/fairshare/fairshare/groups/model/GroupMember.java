package com.fairshare.fairshare.groups.model;

import com.fairshare.fairshare.users.User;
import jakarta.persistence.*;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"})
)
public class GroupMember {
    public enum Role {
        OWNER,
        MEMBER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.MEMBER;

    protected GroupMember() {}

    public GroupMember(Group group, User user) {
        this(group, user, Role.MEMBER);
    }

    public GroupMember(Group group, User user, Role role) {
        this.group = group;
        this.user = user;
        this.role = role == null ? Role.MEMBER : role;
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public User getUser() { return user; }
    public Role getRole() { return role; }
}
