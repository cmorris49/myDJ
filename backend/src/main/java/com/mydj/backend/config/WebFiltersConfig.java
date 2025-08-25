package com.mydj.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class WebFiltersConfig {

    @Bean
    public OncePerRequestFilter authNoCacheFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest req,
                                            @NonNull HttpServletResponse res,
                                            @NonNull FilterChain chain)
                    throws ServletException, IOException {
                String path = req.getServletPath();
                if ("/me".equals(path) || "/api/logout".equals(path)) {
                    res.setHeader("Cache-Control", "no-store");
                }
                chain.doFilter(req, res);
            }
        };
    }
}

