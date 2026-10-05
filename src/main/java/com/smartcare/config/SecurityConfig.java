package com.smartcare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Role-based access control (RBAC): every URL prefix belongs to exactly one role. */
@Configuration
public class SecurityConfig {

    private final AccountStatusFilter accountStatusFilter;

    public SecurityConfig(AccountStatusFilter accountStatusFilter) {
        this.accountStatusFilter = accountStatusFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/img/**", "/fonts/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/pharmacist/**").hasRole("PHARMACIST")
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/supplier/**").hasRole("SUPPLIER")
                .requestMatchers("/delivery/**").hasRole("DELIVERY_STAFF")
                .requestMatchers("/manager/**").hasRole("STORE_MANAGER")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/home", true).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout").permitAll())
            // Ends the session of an account deactivated mid-session, on its next request.
            .addFilterAfter(accountStatusFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
