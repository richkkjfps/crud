package com.richkkjfps.crud.config;

import com.richkkjfps.crud.model.*;
import com.richkkjfps.crud.repositories.*;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccessContext {

    private final UserRepository users;
    private final TenantRepository tenants;

    public AccessContext(UserRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    public Users account() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (
            auth == null || !(auth.getPrincipal() instanceof AccountPrincipal principal)
        ) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre novamente.");
        var account = users
            .findById(principal.getAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var tenant = tenants
            .findById(principal.getTenantId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (
            !account.isActive() ||
            !tenant.isActive() ||
            !Objects.equals(account.getTenantId(), principal.getTenantId()) ||
            account.getAuthVersion() != principal.getAuthVersion() ||
            tenant.getAuthVersion() != principal.getTenantVersion()
        ) throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Seu acesso mudou. Entre novamente."
        );
        return account;
    }

    public Integer tenantId() {
        return account().getTenantId();
    }

    public Tenant tenant() {
        return tenants
            .findById(tenantId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public Users administrator() {
        var account = account();
        if (
            !java.util.Set.of("ADMIN", "OWNER").contains(account.getRole())
        ) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return account;
    }

    public Users owner() {
        var account = account();
        if (!"OWNER".equals(account.getRole())) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN
        );
        return account;
    }
}
