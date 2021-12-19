package com.github.segator.proxylive.config;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfiguration {
    private final Logger logger = LoggerFactory.getLogger(JwtConfiguration.class);
    private String secret;
    private Integer expireInHours;

    JwtConfiguration( @Value("${authentication.secret:}")String secret,@Value("${authentication.expireInHours}")Integer expireInHours){
        if(secret.isEmpty()){
            this.secret = RandomStringUtils.randomAlphanumeric(65);
        }else{
            this.secret=secret;
        }
        this.expireInHours=expireInHours;
    }


    public String getSecret() {
        return secret;
    }

    public Integer getExpireInHours() {
        return expireInHours;
    }
}