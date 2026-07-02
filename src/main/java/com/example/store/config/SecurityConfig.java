package com.example.store.config;

import com.example.store.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtFilter jwtFilter, RateLimitFilter rateLimitFilter,
                          CustomUserDetailsService userDetailsService) {
        this.jwtFilter          = jwtFilter;
        this.rateLimitFilter    = rateLimitFilter;
        this.userDetailsService = userDetailsService;
    }

    // ─── PASSWORD ENCODER ─────────────────────────────────────────
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ─── AUTHENTICATION PROVIDER ──────────────────────────────────
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ─── AUTHENTICATION MANAGER ───────────────────────────────────
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ─── SECURITY FILTER CHAIN ────────────────────────────────────
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── PUBLIC routes (no login needed) ──
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/", "/index.html", "/in.css", "/App.js",
                                "/login.html", "/login.css", "/login.js").permitAll()

                        // ── ITEMS ──
                        .requestMatchers(HttpMethod.GET,    "/items/**").hasAnyRole("CUSTOMER", "STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/items/**").hasAnyRole("STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/items/**").hasAnyRole("STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/items/**").hasAnyRole("STAFF", "ADMIN", "SUPER_ADMIN")

                        // ── ORDERS ──
                        .requestMatchers(HttpMethod.GET,    "/orders/**").hasAnyRole("CUSTOMER", "STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/orders/**").hasAnyRole("CUSTOMER", "STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/orders/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── USERS (admin only) ──
                        .requestMatchers("/users/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── anything else needs login ──
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtFilter.class);

        return http.build();
    }
}