package com.richkkjfps.crud.config;

import com.richkkjfps.crud.repositories.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.*;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users, TenantRepository tenants) {
        return login -> {
            String[] parts = login.split("\n", 2);
            if (parts.length != 2) throw new UsernameNotFoundException("Acesso inválido");
            var tenant = tenants
                .findBySlugIgnoreCase(parts[0])
                .orElseThrow(() -> new UsernameNotFoundException("Acesso inválido"));
            var account = users
                .findByTenantIdAndEmailIgnoreCase(tenant.getId(), parts[1])
                .orElseThrow(() -> new UsernameNotFoundException("Acesso inválido"));
            return new AccountPrincipal(account, tenant);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        UserRepository users,
        TenantRepository tenants
    ) throws Exception {
        return http
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        "/login.html",
                        "/js/login.js",
                        "/css/**",
                        "/auth/csrf",
                        "/auth/register",
                        "/error"
                    )
                    .permitAll()
                    .requestMatchers("/api/clients", "/api/clients/**")
                    .hasRole("OWNER")
                    .requestMatchers("/api/brands", "/api/brands/**")
                    .denyAll()
                    .requestMatchers("/users", "/users/**")
                    .hasAnyRole("ADMIN", "OWNER")
                    .requestMatchers(HttpMethod.GET, "/api/categories")
                    .authenticated()
                    .requestMatchers("/api/categories", "/api/categories/**")
                    .hasAnyRole("ADMIN", "OWNER")
                    .anyRequest()
                    .authenticated()
            )
            .formLogin(form ->
                form
                    .loginPage("/login.html")
                    .loginProcessingUrl("/login")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login.html?error")
                    .permitAll()
            )
            .logout(logout -> logout.logoutSuccessUrl("/login.html?logout"))
            .addFilterBefore(new LoginTenantFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(
                new AccountAccessFilter(users, tenants),
                AnonymousAuthenticationFilter.class
            )
            .exceptionHandling(errors ->
                errors.authenticationEntryPoint((request, response, exception) -> {
                    String uri = request.getServletPath();
                    if (
                        uri.startsWith("/api/") ||
                        uri.startsWith("/products") ||
                        uri.startsWith("/users")
                    ) response.setStatus(401);
                    else response.sendRedirect("/login.html");
                })
            )
            .build();
    }
}
