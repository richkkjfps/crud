package com.richkkjfps.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.richkkjfps.crud.config.*;
import com.richkkjfps.crud.model.Tenant;
import com.richkkjfps.crud.model.Users;
import com.richkkjfps.crud.repositories.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class SecurityAccessTests {

    @Configuration
    @EnableWebSecurity
    static class Beans {

        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        TenantRepository tenants() {
            return mock(TenantRepository.class);
        }
    }

    @Test
    void rolesAndCsrfAreEnforcedBySecurityChain() throws Exception {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityConfig.class, Beans.class);
        context.refresh();
        try {
            var repository = context.getBean(UserRepository.class);
            var proxy = new FilterChainProxy(context.getBean(SecurityFilterChain.class));
            for (String role : new String[] { "USER", "ADMIN", "OWNER" }) {
                Users account = new Users();
                account.setId(1);
                account.setEmail("test@example.com");
                account.setName("Teste");
                account.setSenha("unused-hash");
                account.setRole(role);
                when(repository.findById(1)).thenReturn(Optional.of(account));
                account.setTenantId(1);
                Tenant tenant = new Tenant();
                tenant.setId(1);
                tenant.setSlug("principal");
                when(context.getBean(TenantRepository.class).findById(1)).thenReturn(
                    Optional.of(tenant)
                );
                var principal = new AccountPrincipal(account, tenant);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    principal.getAuthorities()
                );
                var session = new MockHttpSession();
                session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    new SecurityContextImpl(authentication)
                );
                var request = new MockHttpServletRequest("GET", "/users");
                request.setServletPath("/users");
                request.setSession(session);
                var response = new MockHttpServletResponse();
                proxy.doFilter(request, response, (req, res) -> res.getWriter().write("ok"));
                assertEquals(!role.equals("USER") ? 200 : 403, response.getStatus());
                var post = new MockHttpServletRequest("POST", "/products");
                post.setServletPath("/products");
                post.setSession(session);
                var postResponse = new MockHttpServletResponse();
                proxy.doFilter(post, postResponse, (req, res) -> res.getWriter().write("ok"));
                assertEquals(403, postResponse.getStatus());
            }
            var anonymous = new MockHttpServletRequest("GET", "/users");
            anonymous.setServletPath("/users");
            var response = new MockHttpServletResponse();
            proxy.doFilter(anonymous, response, (req, res) -> res.getWriter().write("ok"));
            assertEquals(401, response.getStatus());
        } finally {
            context.close();
        }
    }
}
