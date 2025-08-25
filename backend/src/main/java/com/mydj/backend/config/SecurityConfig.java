package com.mydj.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .logout(AbstractHttpConfigurer::disable) 
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    // static site
                    "/", "/index.html", "/css/**", "/js/**", "/images/**",
                    // auth flow
                    "/login", "/callback", "/me",
                    // QR
                    "/qr", "/qr-default",
                    // APIs used by the web/desktop
                    "/genres", "/allowedGenres", "/requests/**",
                    "/playlists/**", "/devices/**", "/playback/**", "/search/**",
                    // API logout endpoint
                    "/api/logout"
                ).permitAll()
                .anyRequest().permitAll()
            )
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }
}
