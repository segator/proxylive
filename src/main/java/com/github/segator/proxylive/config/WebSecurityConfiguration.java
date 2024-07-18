package com.github.segator.proxylive.config;

import com.github.segator.proxylive.helper.AuthorityRoles;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class WebSecurityConfiguration {

    private final JWTBasicFilter jwtBasicFilter;

    public WebSecurityConfiguration(JWTBasicFilter jwtBasicFilter) {
        this.jwtBasicFilter = jwtBasicFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(configurer ->
                        configurer
                                .requestMatchers("/login", "/error", "/actuator/**").permitAll()
                                .requestMatchers("/channel/**","/view/raw","/api/**").hasRole(AuthorityRoles.USER.getAuthority())
                                .requestMatchers("/view/**").hasRole(AuthorityRoles.ALLOW_ENCODING.getAuthority())
                                .requestMatchers("/ws/**").hasRole(AuthorityRoles.ADMIN.getAuthority())
                                .anyRequest().authenticated()
                )
                .addFilterAfter(jwtBasicFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

}