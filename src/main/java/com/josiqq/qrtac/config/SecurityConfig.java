package com.josiqq.qrtac.config;

import com.josiqq.qrtac.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserService userService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Rutas completamente públicas (sin autenticación)
                .requestMatchers("/", "/home", "/events", "/events/**", 
                                "/requests/**", "/about", "/contact", 
                                "/css/**", "/js/**", "/images/**", 
                                "/register/organizer", "/login", "/error").permitAll()
                // API endpoints públicos
                .requestMatchers("/api/events/**", "/api/public/**").permitAll()
                // Solo rutas para organizadores requieren autenticación
                .requestMatchers("/organizer/**").hasRole("ORGANIZER")
                // Cualquier otra ruta requiere ser organizador (más restrictivo)
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/requests/**") // Para APIs públicas
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            
            if ("ROLE_ORGANIZER".equals(role)) {
                response.sendRedirect("/organizer/dashboard");
            } else {
                // Fallback - solo organizadores pueden loguearse
                response.sendRedirect("/login?error=true");
            }
        };
    }
}