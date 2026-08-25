package com.AudioTracking.Platform.config;

import com.AudioTracking.Platform.security.CustomUserDetailsService;
import com.AudioTracking.Platform.security.JwtAuthenticationFilter;
import com.AudioTracking.Platform.security.JwtService;
import com.AudioTracking.Platform.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(JwtService jwtService, CustomUserDetailsService userDetailsService,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           @Value("${cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // JwtAuthenticationFilter is deliberately not a Spring bean (no @Component)
        // it keeps it exclusively part of this chain instead of also being
        // auto-registered by Spring Boot as a global servlet filter applied to every request twice.
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // The deployment platform polls this, unauthenticated, to decide whether
                        // to route traffic here -- it can only ever report the aggregate UP/DOWN
                        // status (see management.endpoints.web.exposure.include in
                        // application.properties), never a secret. Every other /actuator/**
                        // path -- none of which are actually exposed anyway -- still falls
                        // through to .anyRequest().authenticated() below as defense in depth.
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Explicit origin allowlist (never "*") from cors.allowed-origins -- see application.properties.
    // Credentials aren't needed: the frontend authenticates via an "Authorization: Bearer <jwt>"
    // header, never cookies, so allowCredentials(true) would only widen the attack surface for no
    // benefit here.
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
