/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 *
 * @author isaac
 */
@Configuration
public class AuthenticationServiceFactory {
    private final ProxyLiveConfiguration config;

    public AuthenticationServiceFactory(ProxyLiveConfiguration config) {
        this.config = config;
    }

    @Bean
    public AuthenticationService createAuthenticationService() {
        if(config.getAuthentication()==null){
            return new WithoutAuthenticationService();
        }
        if(config.getAuthentication().getLdap()!=null){
            return new LDAPAuthenticationService(config);
        }else if(config.getAuthentication().getPlex()!=null){
            return new PlexAuthenticationService(config);
        }else{
            return new WithoutAuthenticationService();
        }
    }
}
