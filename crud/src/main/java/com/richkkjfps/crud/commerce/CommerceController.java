package com.richkkjfps.crud.commerce;

import com.richkkjfps.crud.commerce.CommerceDtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommerceController {

    private final CommerceService commerce;

    public CommerceController(CommerceService commerce) {
        this.commerce = commerce;
    }

    @GetMapping("/customers")
    public List<Contact> customers() {
        return commerce.contacts(false);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Contact createCustomer(@RequestBody ContactRequest request) {
        return commerce.saveContact(false, null, request);
    }

    @PutMapping("/customers/{id}")
    public Contact updateCustomer(@PathVariable Integer id, @RequestBody ContactRequest request) {
        return commerce.saveContact(false, id, request);
    }

    @GetMapping("/suppliers")
    public List<Contact> suppliers() {
        return commerce.contacts(true);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Contact createSupplier(@RequestBody ContactRequest request) {
        return commerce.saveContact(true, null, request);
    }

    @PutMapping("/suppliers/{id}")
    public Contact updateSupplier(@PathVariable Integer id, @RequestBody ContactRequest request) {
        return commerce.saveContact(true, id, request);
    }

    @GetMapping("/sales")
    public List<Sale> sales() {
        return commerce.sales(null);
    }

    @GetMapping("/customers/{id}/sales")
    public List<Sale> statement(@PathVariable Integer id) {
        return commerce.customerSales(id);
    }

    @GetMapping("/sales/{id}")
    public SaleDetails sale(@PathVariable Integer id) {
        return commerce.details(id);
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDetails createSale(@RequestBody SaleRequest request) {
        return commerce.createSale(request);
    }

    @PostMapping("/sales/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDetails payment(@PathVariable Integer id, @RequestBody PaymentRequest request) {
        return commerce.pay(id, request);
    }
}
