package com.richkkjfps.crud.repositories;

import com.richkkjfps.crud.model.Category;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findAllByTenantId(Integer tenantId, org.springframework.data.domain.Sort sort);
    Optional<Category> findByIdAndTenantId(Integer id, Integer tenantId);
    boolean existsByIdAndTenantId(Integer id, Integer tenantId);
}
