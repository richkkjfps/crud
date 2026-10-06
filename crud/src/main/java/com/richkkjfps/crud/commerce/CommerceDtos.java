package com.richkkjfps.crud.commerce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CommerceDtos {

    private CommerceDtos() {}

    public record ContactRequest(
        String name,
        String phone,
        String email,
        String address,
        String suppliedItems,
        String notes
    ) {}

    public record Contact(
        Integer id,
        String name,
        String phone,
        String email,
        String address,
        String suppliedItems,
        String notes,
        BigDecimal balance
    ) {}

    public record ItemRequest(Integer productId, Integer quantity) {}

    public record SaleRequest(
        Integer customerId,
        String paymentType,
        LocalDate dueDate,
        BigDecimal initialPayment,
        String notes,
        UUID requestId,
        List<ItemRequest> items,
        String paymentMethod
    ) {}

    public record PaymentRequest(BigDecimal amount, String notes, UUID requestId, String paymentMethod) {}

    public record Sale(
        Integer id,
        Integer customerId,
        String customerName,
        String paymentType,
        LocalDate saleDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balance,
        String status,
        String notes
    ) {}

    public record SaleItem(
        Integer productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal unitCost
    ) {}

    public record Payment(Integer id, BigDecimal amount, LocalDate paymentDate, String notes) {}

    public record SaleDetails(Sale sale, List<SaleItem> items, List<Payment> payments) {}
}
