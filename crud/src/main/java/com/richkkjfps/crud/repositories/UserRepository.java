package com.richkkjfps.crud.repositories;

import com.richkkjfps.crud.model.Users;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByTenantIdAndEmailIgnoreCase(Integer tenantId, String email);
    Optional<Users> findByIdAndTenantId(Integer id, Integer tenantId);
    List<Users> findAllByTenantId(Integer tenantId, org.springframework.data.domain.Sort sort);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select u from Users u where u.tenantId=:tenantId and u.role in ('ADMIN','OWNER') order by u.id"
    )
    List<Users> lockAdministrators(Integer tenantId);
}
