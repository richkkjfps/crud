package com.richkkjfps.crud.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginTenantFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String company = Objects.toString(request.getParameter("company"), "")
            .trim()
            .toLowerCase(Locale.ROOT);
        String email = Objects.toString(request.getParameter("username"), "")
            .trim()
            .toLowerCase(Locale.ROOT);
        String combined = company + "\n" + email;
        chain.doFilter(
            new HttpServletRequestWrapper(request) {
                @Override
                public String getParameter(String name) {
                    return "username".equals(name) ? combined : super.getParameter(name);
                }

                @Override
                public String[] getParameterValues(String name) {
                    return "username".equals(name)
                        ? new String[] { combined }
                        : super.getParameterValues(name);
                }

                @Override
                public Map<String, String[]> getParameterMap() {
                    var map = new HashMap<>(super.getParameterMap());
                    map.put("username", new String[] { combined });
                    return Collections.unmodifiableMap(map);
                }
            },
            response
        );
    }
}
