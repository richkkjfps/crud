package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.config.*;
import com.richkkjfps.crud.dtos.UserDto;
import com.richkkjfps.crud.model.Users;
import com.richkkjfps.crud.repositories.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    private final UserRepository users;
    private final TenantRepository tenants;
    private final AccessContext access;
    private final AccountWriter writer;
    private final RegistrationLimits limits;

    public AuthController(
        UserRepository users,
        TenantRepository tenants,
        AccessContext access,
        AccountWriter writer,
        RegistrationLimits limits
    ) {
        this.users = users;
        this.tenants = tenants;
        this.access = access;
        this.writer = writer;
        this.limits = limits;
    }

    @GetMapping("/auth/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me() {
        var account = access.account();
        var tenant = access.tenant();
        return Map.of(
            "id",
            account.getId(),
            "name",
            account.getName(),
            "email",
            account.getEmail(),
            "role",
            account.getRole(),
            "company",
            tenant.getSlug(),
            "companyName",
            tenant.getName()
        );
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, String> register(
        @RequestBody RegistrationDto dto,
        HttpServletRequest request
    ) {
        limits.check(request.getRemoteAddr());
        String slug = Objects.toString(dto.company(), "").trim().toLowerCase(Locale.ROOT);
        var tenant = tenants
            .findBySlugIgnoreCase(slug)
            .filter(value -> value.isActive())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não foi possível solicitar o acesso. Confira o código da empresa."
                )
            );
        String email = Objects.toString(dto.email(), "").trim().toLowerCase(Locale.ROOT);
        if (users.findByTenantIdAndEmailIgnoreCase(tenant.getId(), email).isEmpty()) {
            var account = new Users();
            account.setTenantId(tenant.getId());
            writer.save(
                account,
                new UserDto(dto.name(), email, dto.password(), "USER", false),
                true
            );
        }
        return Map.of(
            "message",
            "Solicitação recebida. Aguarde a aprovação do administrador da empresa."
        );
    }

    public record RegistrationDto(String company, String name, String email, String password) {}
}
