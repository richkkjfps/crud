package com.richkkjfps.crud.repositories;

import com.richkkjfps.crud.model.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Integer> {
    Optional<Tenant> findBySlugIgnoreCase(String slug);
}
