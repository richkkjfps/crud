package com.richkkjfps.crud.repositories;

import com.richkkjfps.crud.model.Product;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findAllByTenantId(Integer tenantId, org.springframework.data.domain.Sort sort);
    Optional<Product> findByIdAndTenantId(Integer id, Integer tenantId);

    @org.springframework.data.jpa.repository.Lock(
        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
    )
    @org.springframework.data.jpa.repository.Query(
        "select p from product p where p.id=:id and p.tenantId=:tenantId"
    )
    Optional<Product> findLockedByIdAndTenantId(Integer id, Integer tenantId);

    Optional<Product> findByTenantIdAndBarcode(Integer tenantId, String barcode);
    Optional<Product> findByTenantIdAndSkuIgnoreCase(Integer tenantId, String sku);
    boolean existsByTenantIdAndCategoryId(Integer tenantId, Integer categoryId);
}
