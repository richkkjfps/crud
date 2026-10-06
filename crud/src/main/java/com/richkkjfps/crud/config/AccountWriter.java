package com.richkkjfps.crud.config;

import com.richkkjfps.crud.dtos.UserDto;
import com.richkkjfps.crud.model.Users;
import com.richkkjfps.crud.repositories.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountWriter {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AccountWriter(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public Users save(Users account, UserDto dto, boolean creating) {
        String name = dto.name() == null ? "" : dto.name().trim(),
            email = dto.email() == null ? "" : dto.email().trim().toLowerCase(Locale.ROOT);
        if (
            name.isEmpty() ||
            name.length() > 100 ||
            email.length() > 255 ||
            !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Informe nome e e-mail válidos."
        );
        if (
            !Set.of("ADMIN", "USER", "OWNER").contains(Objects.toString(dto.role(), ""))
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido.");
        users
            .findByTenantIdAndEmailIgnoreCase(account.getTenantId(), email)
            .filter(other -> !Objects.equals(other.getId(), account.getId()))
            .ifPresent(other -> {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este e-mail já está cadastrado nesta empresa."
                );
            });
        boolean passwordChanged = dto.senha() != null && !dto.senha().isEmpty();
        if (creating || passwordChanged) {
            if (
                dto.senha() == null ||
                dto.senha().length() < 8 ||
                dto.senha().getBytes(StandardCharsets.UTF_8).length > 72
            ) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A senha precisa ter pelo menos 8 caracteres e no máximo 72 bytes."
            );
            account.setSenha(encoder.encode(dto.senha()));
        }
        boolean active = dto.active() == null ? account.isActive() : dto.active();
        if (
            !creating &&
            (passwordChanged ||
                !email.equals(account.getEmail()) ||
                !dto.role().equals(account.getRole()) ||
                active != account.isActive())
        ) account.setAuthVersion(account.getAuthVersion() + 1);
        account.setName(name);
        account.setEmail(email);
        account.setRole(dto.role());
        account.setActive(active);
        return users.saveAndFlush(account);
    }
}
