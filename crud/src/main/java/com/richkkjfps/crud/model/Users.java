package com.richkkjfps.crud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "app_account")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer tenantId;

    private String email;

    @JsonIgnore
    @Column(name = "password_hash")
    private String senha;

    private String role = "USER";
    private boolean active = true;

    @JsonIgnore
    private int authVersion;

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer value) {
        tenantId = value;
    }

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String value) {
        email = value;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String value) {
        senha = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        role = value;
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
}
