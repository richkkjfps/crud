package com.richkkjfps.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.richkkjfps.crud.config.AccessContext;
import com.richkkjfps.crud.controllers.ProductController;
import com.richkkjfps.crud.dtos.ProductsDto;
import com.richkkjfps.crud.model.Product;
import com.richkkjfps.crud.repositories.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GeneralProductTests {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final AccessContext access = mock(AccessContext.class);

    private ProductController controller() {
        when(access.tenantId()).thenReturn(1);
        return new ProductController(products, categories, access);
    }

    private ProductsDto dto(String barcode, String price) {
        return new ProductsDto(
            "Celular",
            new BigDecimal(price),
            null,
            "Samsung",
            barcode,
            "CEL-001",
            "Modelo A",
            "Tela de 6 polegadas",
            3
        );
    }

    @Test
    void productKeepsLeadingZerosDecimalPriceAndFreeTextBrand() {
        when(products.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = controller().create(dto("0012345678905", "1299.90"));
        assertEquals("0012345678905", result.getBarcode());
        assertEquals(new BigDecimal("1299.90"), result.getPrice());
        assertEquals("Samsung", result.getBrandName());
        assertEquals(1, result.getTenantId());
        assertEquals(3, result.getQuantity());
    }

    @Test
    void duplicateBarcodeAndSkuAreRejectedWithinSameClient() {
        var existing = new Product();
        existing.setId(1);
        when(products.findByTenantIdAndBarcode(1, "0012345678905")).thenReturn(
            Optional.of(existing)
        );
        assertEquals(
            HttpStatus.CONFLICT,
            assertThrows(ResponseStatusException.class, () ->
                controller().create(dto("0012345678905", "10.00"))
            ).getStatusCode()
        );
        when(products.findByTenantIdAndBarcode(eq(1), anyString())).thenReturn(Optional.empty());
        when(products.findByTenantIdAndSkuIgnoreCase(1, "CEL-001")).thenReturn(
            Optional.of(existing)
        );
        assertEquals(
            HttpStatus.CONFLICT,
            assertThrows(ResponseStatusException.class, () ->
                controller().create(dto("other-code", "10.00"))
            ).getStatusCode()
        );
    }

    @Test
    void anotherClientsProductAndCategoryCannotBeUsed() {
        var controller = controller();
        assertEquals(
            HttpStatus.NOT_FOUND,
            assertThrows(ResponseStatusException.class, () -> controller.one(99)).getStatusCode()
        );
        verify(products).findByIdAndTenantId(99, 1);
        var crossCategory = new ProductsDto(
            "PC",
            new BigDecimal("10.00"),
            99,
            "Dell",
            null,
            null,
            null,
            null,
            1
        );
        assertEquals(
            HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class, () ->
                controller.create(crossCategory)
            ).getStatusCode()
        );
        verify(categories).existsByIdAndTenantId(99, 1);
    }

    @Test
    void ownBarcodeCanBeKeptDuringEdit() {
        var existing = new Product();
        existing.setId(1);
        existing.setTenantId(1);
        existing.setBarcode("ABC/001");
        when(products.findLockedByIdAndTenantId(1, 1)).thenReturn(Optional.of(existing));
        when(products.findByTenantIdAndBarcode(1, "ABC/001")).thenReturn(Optional.of(existing));
        when(products.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var controller = controller();
        assertEquals(existing, controller.update(1, dto("ABC/001", "10.25")));
        assertEquals("ABC/001", controller.byBarcode(" ABC/001 ").getBarcode());
    }

    @Test
    void invalidPrecisionAndNegativeStockAreRejected() {
        assertThrows(ResponseStatusException.class, () ->
            controller().create(dto("001", "10.123"))
        );
        assertThrows(ResponseStatusException.class, () ->
            controller().create(
                new ProductsDto(
                    "PC",
                    new BigDecimal("10.00"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    -1
                )
            )
        );
    }

    @Test
    void productsWithoutBarcodeAndBrandAreAllowed() {
        when(products.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = controller().create(
            new ProductsDto(
                "Produto genérico",
                new BigDecimal("1.99"),
                null,
                " ",
                " ",
                null,
                null,
                null,
                null
            )
        );
        assertNull(result.getBarcode());
        assertNull(result.getBrandName());
        assertEquals(0, result.getQuantity());
    }
}
