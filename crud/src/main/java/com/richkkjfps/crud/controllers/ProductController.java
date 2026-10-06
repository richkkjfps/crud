package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.config.AccessContext;
import com.richkkjfps.crud.dtos.ProductsDto;
import com.richkkjfps.crud.model.Product;
import com.richkkjfps.crud.repositories.*;
import java.math.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final AccessContext access;

    public ProductController(
        ProductRepository products,
        CategoryRepository categories,
        AccessContext access
    ) {
        this.products = products;
        this.categories = categories;
        this.access = access;
    }

    @GetMapping
    public List<Product> all() {
        return products.findAllByTenantId(access.tenantId(), Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/lookup")
    public Product byBarcode(@RequestParam String barcode) {
        return products
            .findByTenantIdAndBarcode(access.tenantId(), barcode.trim())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Código de barras não cadastrado."
                )
            );
    }

    @GetMapping("/{id}")
    public Product one(@PathVariable Integer id) {
        return find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody ProductsDto dto) {
        return save(new Product(), dto);
    }

    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Product update(@PathVariable Integer id, @RequestBody ProductsDto dto) {
        Product product = products
            .findLockedByIdAndTenantId(id, access.tenantId())
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.")
            );
        return save(product, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        products.delete(find(id));
    }

    private Product find(Integer id) {
        return products
            .findByIdAndTenantId(id, access.tenantId())
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.")
            );
    }

    private Product save(Product product, ProductsDto dto) {
        String name = dto.name() == null ? "" : dto.name().trim();
        BigDecimal price = dto.price();
        if (
            name.isEmpty() ||
            name.length() > 255 ||
            price == null ||
            price.signum() < 0 ||
            price.compareTo(new BigDecimal("999999999999.99")) > 0
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Informe nome e preço válidos."
        );
        try {
            price = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "O preço deve ter no máximo duas casas decimais."
            );
        }
        String barcode = optional(dto.barcode(), 100, "Código de barras"),
            sku = optional(dto.sku(), 100, "Referência interna");
        String model = optional(dto.model(), 150, "Modelo"),
            description = optional(dto.description(), 2000, "Descrição");
        if (barcode != null && !barcode.matches("[ -~]+")) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Código de barras inválido."
        );
        if (barcode != null) products
            .findByTenantIdAndBarcode(access.tenantId(), barcode)
            .filter(other -> !Objects.equals(other.getId(), product.getId()))
            .ifPresent(other -> {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este código de barras já está cadastrado em outro produto."
                );
            });
        if (sku != null) products
            .findByTenantIdAndSkuIgnoreCase(access.tenantId(), sku)
            .filter(other -> !Objects.equals(other.getId(), product.getId()))
            .ifPresent(other -> {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Esta referência interna já está cadastrada."
                );
            });
        int quantity = dto.quantity() == null ? 0 : dto.quantity();
        if (quantity < 0) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A quantidade não pode ser negativa."
        );
        if (
            dto.categoryId() != null &&
            !categories.existsByIdAndTenantId(dto.categoryId(), access.tenantId())
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida.");
        product.setName(name);
        product.setPrice(price);
        if (dto.costPrice() != null) {
            access.administrator();
            BigDecimal cost = dto.costPrice();
            if (
                cost.signum() < 0 ||
                cost.scale() > 2 ||
                cost.compareTo(new BigDecimal("999999999999.99")) > 0
            ) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe um custo válido, com até duas casas decimais."
                );
            }
            product.setCostPrice(cost);
        }
        product.setCategoryId(dto.categoryId());
        product.setBrandName(optional(dto.brandName(), 150, "Marca"));
        product.setTenantId(access.tenantId());
        product.setBarcode(barcode);
        product.setSku(sku);
        product.setModel(model);
        product.setDescription(description);
        product.setQuantity(quantity);
        return products.saveAndFlush(product);
    }

    private String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            label + " excede o tamanho permitido."
        );
        return trimmed;
    }
}
