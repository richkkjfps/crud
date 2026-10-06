package com.richkkjfps.crud.config;

import com.richkkjfps.crud.model.Users;
import com.richkkjfps.crud.repositories.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class InitialAdmin {

    @Bean
    CommandLineRunner createInitialAdmin(
        UserRepository users,
        PasswordEncoder encoder,
        Environment env
    ) {
        return args -> {
            if (users.count() != 0) return;
            String email = env
                .getProperty("CRUD_ADMIN_EMAIL", "admin@crud.local")
                .trim()
                .toLowerCase(Locale.ROOT);
            String password = env.getProperty("CRUD_ADMIN_PASSWORD");
            boolean generated = password == null || password.isBlank();
            if (generated) password = UUID.randomUUID().toString();
            if (
                !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                email.length() > 255 ||
                password.length() < 8 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72
            ) throw new IllegalArgumentException(
                "Configure e-mail válido e senha de 8 a 72 bytes para o administrador."
            );
            Users admin = new Users();
            admin.setName("Administrador");
            admin.setEmail(email);
            admin.setSenha(encoder.encode(password));
            admin.setRole("OWNER");
            admin.setTenantId(1);
            users.save(admin);
            System.out.println("Administrador inicial criado. E-mail: " + email);
            if (generated) System.out.println("Senha inicial do administrador: " + password);
        };
    }
}
