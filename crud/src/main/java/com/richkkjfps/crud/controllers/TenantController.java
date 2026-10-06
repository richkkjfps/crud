package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.config.*;
import com.richkkjfps.crud.dtos.UserDto;
import com.richkkjfps.crud.model.*;
import com.richkkjfps.crud.repositories.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clients")
public class TenantController {

    private final TenantRepository tenants;
    private final CategoryRepository categories;
    private final AccountWriter accounts;
    private final AccessContext access;

    public TenantController(
        TenantRepository tenants,
        CategoryRepository categories,
        AccountWriter accounts,
        AccessContext access
    ) {
        this.tenants = tenants;
        this.categories = categories;
        this.accounts = accounts;
        this.access = access;
    }

    @GetMapping
    public List<Tenant> all() {
        access.owner();
        return tenants.findAll(Sort.by("name"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Tenant create(@RequestBody ClientDto dto) {
        access.owner();
        String name = cleanName(dto.name()),
            slug = Objects.toString(dto.slug(), "").trim().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9][a-z0-9-]{2,39}")) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "O código deve ter de 3 a 40 caracteres, com letras minúsculas, números ou hífen."
        );
        if (tenants.findBySlugIgnoreCase(slug).isPresent()) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Código de empresa já cadastrado."
        );
        var tenant = new Tenant();
        tenant.setName(name);
        tenant.setSlug(slug);
        tenant = tenants.saveAndFlush(tenant);
        for (String categoryName : List.of(
            "Comida",
            "Alimentos",
            "Limpeza",
            "Computadores",
            "Celulares",
            "Eletrônicos",
            "Eletrodomésticos",
            "Acessórios",
            "Roupas e calçados",
            "Ferramentas",
            "Móveis",
            "Papelaria",
            "Outros"
        )) {
            var category = new Category();
            category.setTenantId(tenant.getId());
            category.setName(categoryName);
            categories.save(category);
        }
        var admin = new Users();
        admin.setTenantId(tenant.getId());
        accounts.save(
            admin,
            new UserDto(dto.adminName(), dto.adminEmail(), dto.password(), "ADMIN", true),
            true
        );
        return tenant;
    }

    @PutMapping("/{id}")
    @Transactional
    public Tenant update(@PathVariable Integer id, @RequestBody ClientUpdateDto dto) {
        var actor = access.owner();
        var tenant = tenants
            .findById(id)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.")
            );
        if (
            Objects.equals(actor.getTenantId(), id) && Boolean.FALSE.equals(dto.active())
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Você não pode desativar seu próprio painel."
        );
        tenant.setName(cleanName(dto.name()));
        boolean active = dto.active() == null ? tenant.isActive() : dto.active();
        if (active != tenant.isActive()) tenant.setAuthVersion(tenant.getAuthVersion() + 1);
        tenant.setActive(active);
        return tenants.saveAndFlush(tenant);
    }

    private String cleanName(String value) {
        String name = Objects.toString(value, "").trim();
        if (name.isEmpty() || name.length() > 100) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Informe um nome de até 100 caracteres."
        );
        return name;
    }

    public record ClientDto(
        String name,
        String slug,
        String adminName,
        String adminEmail,
        String password
    ) {}

    public record ClientUpdateDto(String name, Boolean active) {}
}
