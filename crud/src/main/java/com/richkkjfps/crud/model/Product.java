package com.richkkjfps.crud.model;

import jakarta.persistence.*;

@Entity(name = "product")
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @Column(precision = 21, scale = 2)
    private java.math.BigDecimal price;

    @Column(precision = 21, scale = 2)
    private java.math.BigDecimal costPrice;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.math.BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(java.math.BigDecimal value) {
        costPrice = value;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("costPrice")
    public java.math.BigDecimal visibleCostPrice() {
        var authentication =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        boolean manager = authentication
            .getAuthorities()
            .stream()
            .anyMatch(authority ->
                java.util.Set.of("ROLE_ADMIN", "ROLE_OWNER").contains(authority.getAuthority())
            );
        return manager ? costPrice : null;
    }

    private String barcode;
    private String sku;
    private String model;
    private String description;
    private Integer quantity = 0;
    private Integer categoryId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer tenantId;

    private String brandName;

    public Product(Integer id, String name, java.math.BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Product() {}

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String value) {
        barcode = value;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String value) {
        sku = value;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String value) {
        model = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer value) {
        quantity = value;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer value) {
        tenantId = value;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer value) {
        categoryId = value;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String value) {
        brandName = value;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public void setPrice(java.math.BigDecimal price) {
        this.price = price;
    }
}
