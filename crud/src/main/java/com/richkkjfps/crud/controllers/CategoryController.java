package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.config.AccessContext;
import com.richkkjfps.crud.model.Category;
import com.richkkjfps.crud.repositories.*;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository repository;
    private final ProductRepository products;
    private final AccessContext access;

    public CategoryController(
        CategoryRepository repository,
        ProductRepository products,
        AccessContext access
    ) {
        this.repository = repository;
        this.products = products;
        this.access = access;
    }

    @GetMapping
    public List<Category> all() {
        return repository.findAllByTenantId(access.tenantId(), Sort.by("name"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@RequestBody NamedDto dto) {
        var account = access.administrator();
        var entry = new Category();
        entry.setTenantId(account.getTenantId());
        return save(entry, dto);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Integer id, @RequestBody NamedDto dto) {
        access.administrator();
        return save(find(id), dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Integer id) {
        var account = access.administrator();
        var entry = find(id);
        if (
            products.existsByTenantIdAndCategoryId(account.getTenantId(), id)
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Há produtos associados. Altere os produtos antes de excluir."
        );
        repository.delete(entry);
        repository.flush();
    }

    private Category find(Integer id) {
        return repository
            .findByIdAndTenantId(id, access.tenantId())
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada.")
            );
    }

    private Category save(Category entry, NamedDto dto) {
        String name = dto.name() == null ? "" : dto.name().trim();
        if (name.isEmpty() || name.length() > 100) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Informe um nome de até 100 caracteres."
        );
        entry.setName(name);
        return repository.saveAndFlush(entry);
    }

    public record NamedDto(String name) {}
}
