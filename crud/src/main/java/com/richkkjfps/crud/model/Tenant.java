package com.richkkjfps.crud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "client_panel")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String slug;
    private boolean active = true;

    @JsonIgnore
    private int authVersion;

    private Instant createdAt = Instant.now();

    public Integer getId() {
        return id;
    }

    public void setId(Integer value) {
        id = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String value) {
        slug = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        active = value;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public void setAuthVersion(int value) {
        authVersion = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
