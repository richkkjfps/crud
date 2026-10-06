package com.richkkjfps.crud.config;

import com.richkkjfps.crud.repositories.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountAccessFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final TenantRepository tenants;

    public AccountAccessFilter(UserRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            var account = users.findById(principal.getAccountId());
            var tenant = tenants.findById(principal.getTenantId());
            if (
                account.isEmpty() ||
                tenant.isEmpty() ||
                !account.get().isActive() ||
                !tenant.get().isActive() ||
                !Objects.equals(account.get().getTenantId(), principal.getTenantId()) ||
                account.get().getAuthVersion() != principal.getAuthVersion() ||
                tenant.get().getAuthVersion() != principal.getTenantVersion()
            ) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                String uri = request.getServletPath();
                if (
                    uri.startsWith("/api/") ||
                    uri.startsWith("/products") ||
                    uri.startsWith("/users")
                ) response.setStatus(401);
                else response.sendRedirect("/login.html");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
