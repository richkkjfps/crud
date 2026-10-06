package com.richkkjfps.crud.config;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RegistrationLimits {

    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void check(String ip) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().until < now);
        Window window = windows.computeIfAbsent(ip, key -> new Window(now + 3600000));
        if (window.count >= 20 || windows.size() > 10000) throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Muitas solicitações. Tente novamente mais tarde."
        );
        window.count++;
    }

    private static class Window {

        final long until;
        int count;

        Window(long until) {
            this.until = until;
        }
    }
}
