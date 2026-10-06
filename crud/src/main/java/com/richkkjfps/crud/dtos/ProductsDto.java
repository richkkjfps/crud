package com.richkkjfps.crud.dtos;

import java.math.BigDecimal;

public record ProductsDto(
    String name,
    BigDecimal price,
    Integer categoryId,
    String brandName,
    String barcode,
    String sku,
    String model,
    String description,
    Integer quantity,
    BigDecimal costPrice
) {
    public ProductsDto(
        String name,
        BigDecimal price,
        Integer categoryId,
        String brandName,
        String barcode,
        String sku,
        String model,
        String description,
        Integer quantity
    ) {
        this(name, price, categoryId, brandName, barcode, sku, model, description, quantity, null);
    }
}
