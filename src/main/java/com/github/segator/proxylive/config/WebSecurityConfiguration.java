package com.github.segator.proxylive.config;

import com.github.segator.proxylive.helper.AuthorityRoles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final JWTBasicFilter jwtBasicFilter;
    private final JwtConfiguration jwtConfiguration;

    public WebSecurityConfiguration(JWTBasicFilter jwtBasicFilter, JwtConfiguration jwtConfiguration) {
        this.jwtBasicFilter = jwtBasicFilter;
        this.jwtConfiguration = jwtConfiguration;
    }
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/login","/error","/actuator/**");
    }
    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http
                .cors()
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests(configurer ->
                        configurer
                                .antMatchers("/channel/**","/view/raw","/api/**")
                                .hasRole(AuthorityRoles.USER.getAuthority())
                                .antMatchers("/view/**")
                                .hasRole(AuthorityRoles.ALLOW_ENCODING.getAuthority())
                                .antMatchers("/ws/**")
                                .hasRole(AuthorityRoles.ADMIN.getAuthority())
                ).addFilterAfter(jwtBasicFilter, UsernamePasswordAuthenticationFilter.class);
    }

}