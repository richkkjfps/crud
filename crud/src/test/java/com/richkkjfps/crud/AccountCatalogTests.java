package com.richkkjfps.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.richkkjfps.crud.config.*;
import com.richkkjfps.crud.controllers.*;
import com.richkkjfps.crud.dtos.*;
import com.richkkjfps.crud.model.*;
import com.richkkjfps.crud.repositories.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AccountCatalogTests {

    private Users account() {
        var account = new Users();
        account.setId(1);
        account.setTenantId(1);
        account.setName("Administrador");
        account.setEmail("admin@example.com");
        account.setRole("ADMIN");
        account.setSenha(new BCryptPasswordEncoder().encode("test-password-123"));
        return account;
    }

    private Tenant tenant() {
        var tenant = new Tenant();
        tenant.setId(1);
        tenant.setSlug("principal");
        tenant.setName("Principal");
        return tenant;
    }

    @Test
    void newAccountHasHashedPasswordAndNormalizedEmail() {
        var users = mock(UserRepository.class);
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var encoder = new BCryptPasswordEncoder();
        var account = new Users();
        account.setTenantId(1);
        var result = new AccountWriter(users, encoder).save(
            account,
            new UserDto(" Ana ", " ANA@example.com ", "test-password-123", "USER", true),
            true
        );
        assertEquals("Ana", result.getName());
        assertEquals("ana@example.com", result.getEmail());
        assertTrue(encoder.matches("test-password-123", result.getSenha()));
        assertNotEquals("test-password-123", result.getSenha());
    }

    @Test
    void shortPasswordAndDuplicateEmailAreRejected() {
        var users = mock(UserRepository.class);
        var writer = new AccountWriter(users, new BCryptPasswordEncoder());
        var fresh = new Users();
        fresh.setTenantId(1);
        assertEquals(
            HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class, () ->
                writer.save(fresh, new UserDto("Ana", "ana@example.com", "123", "USER", true), true)
            ).getStatusCode()
        );
        when(users.findByTenantIdAndEmailIgnoreCase(1, "admin@example.com")).thenReturn(
            Optional.of(account())
        );
        assertEquals(
            HttpStatus.CONFLICT,
            assertThrows(ResponseStatusException.class, () ->
                writer.save(
                    fresh,
                    new UserDto("Ana", "admin@example.com", "test-password-123", "USER", true),
                    true
                )
            ).getStatusCode()
        );
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotDisableOrDemoteOwnAccount() {
        var users = mock(UserRepository.class);
        var access = mock(AccessContext.class);
        var admin = account();
        when(access.administrator()).thenReturn(admin);
        when(users.findByIdAndTenantId(1, 1)).thenReturn(Optional.of(admin));
        var controller = new UserController(
            users,
            access,
            new AccountWriter(users, new BCryptPasswordEncoder())
        );
        assertThrows(ResponseStatusException.class, () ->
            controller.update(1, new UserDto("Admin", "admin@example.com", "", "USER", true))
        );
        assertThrows(ResponseStatusException.class, () ->
            controller.update(1, new UserDto("Admin", "admin@example.com", "", "ADMIN", false))
        );
    }

    @Test
    void cannotGrantOwnerRoleOrEditAnotherClientsUser() {
        var users = mock(UserRepository.class);
        var access = mock(AccessContext.class);
        when(access.administrator()).thenReturn(account());
        var controller = new UserController(
            users,
            access,
            new AccountWriter(users, new BCryptPasswordEncoder())
        );
        assertThrows(ResponseStatusException.class, () ->
            controller.create(
                new UserDto("Owner", "o@example.com", "test-password-123", "OWNER", true)
            )
        );
        assertEquals(
            HttpStatus.NOT_FOUND,
            assertThrows(ResponseStatusException.class, () ->
                controller.update(99, new UserDto("Other", "other@example.com", "", "USER", true))
            ).getStatusCode()
        );
        verify(users).findByIdAndTenantId(99, 1);
    }

    @Test
    void blankPasswordKeepsHashAndResetInvalidatesSessions() {
        var users = mock(UserRepository.class);
        var account = account();
        when(users.findByTenantIdAndEmailIgnoreCase(1, account.getEmail())).thenReturn(
            Optional.of(account)
        );
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var writer = new AccountWriter(users, new BCryptPasswordEncoder());
        String hash = account.getSenha();
        writer.save(
            account,
            new UserDto(account.getName(), account.getEmail(), "", "ADMIN", true),
            false
        );
        assertEquals(hash, account.getSenha());
        assertEquals(0, account.getAuthVersion());
        writer.save(
            account,
            new UserDto(account.getName(), account.getEmail(), "another-password", "ADMIN", true),
            false
        );
        assertEquals(1, account.getAuthVersion());
    }

    @Test
    void filterBlocksDisabledAccountsAndDisabledClients() throws Exception {
        for (boolean disabledAccount : List.of(true, false)) {
            var users = mock(UserRepository.class);
            var tenants = mock(TenantRepository.class);
            var account = account();
            var tenant = tenant();
            var principal = new AccountPrincipal(account, tenant);
            if (disabledAccount) account.setActive(false);
            else tenant.setActive(false);
            when(users.findById(1)).thenReturn(Optional.of(account));
            when(tenants.findById(1)).thenReturn(Optional.of(tenant));
            SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    principal.getAuthorities()
                )
            );
            var request = new MockHttpServletRequest("GET", "/products");
            request.setServletPath("/products");
            var session = (MockHttpSession) request.getSession();
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            try {
                new AccountAccessFilter(users, tenants).doFilter(request, response, chain);
                assertEquals(401, response.getStatus());
                assertTrue(session.isInvalid());
                assertNull(chain.getRequest());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void accessContextRejectsTenantVersionChanges() {
        var users = mock(UserRepository.class);
        var tenants = mock(TenantRepository.class);
        var account = account();
        var tenant = tenant();
        var principal = new AccountPrincipal(account, tenant);
        tenant.setAuthVersion(1);
        when(users.findById(1)).thenReturn(Optional.of(account));
        when(tenants.findById(1)).thenReturn(Optional.of(tenant));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
            )
        );
        try {
            assertThrows(ResponseStatusException.class, () ->
                new AccessContext(users, tenants).account()
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void secretsAndTenantIdAreExcludedFromJson() throws Exception {
        for (String field : List.of("senha", "authVersion", "tenantId"))
            assertNotNull(
                Users.class
                    .getDeclaredField(field)
                    .getAnnotation(com.fasterxml.jackson.annotation.JsonIgnore.class)
            );
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        var categories = mock(CategoryRepository.class);
        var products = mock(ProductRepository.class);
        var access = mock(AccessContext.class);
        when(access.administrator()).thenReturn(account());
        when(access.tenantId()).thenReturn(1);
        var category = new Category();
        category.setId(2);
        category.setTenantId(1);
        category.setName("Limpeza");
        when(categories.findByIdAndTenantId(2, 1)).thenReturn(Optional.of(category));
        when(products.existsByTenantIdAndCategoryId(1, 2)).thenReturn(true);
        assertEquals(
            HttpStatus.CONFLICT,
            assertThrows(ResponseStatusException.class, () ->
                new CategoryController(categories, products, access).delete(2)
            ).getStatusCode()
        );
        verify(categories, never()).delete(any());
    }
}
