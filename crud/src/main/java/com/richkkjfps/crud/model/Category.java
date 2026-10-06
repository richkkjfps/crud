package com.richkkjfps.crud.model;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer tenantId;

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
}
