package com.github.segator.proxylive.helper;


import com.github.segator.proxylive.config.JwtConfiguration;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JwtHelper {
    private JwtConfiguration jwtConfiguration;

    public JwtHelper(JwtConfiguration jwtConfiguration) {
        this.jwtConfiguration = jwtConfiguration;
    }
    public String createJwtForClaims(String subject, List<GrantedAuthority> grantedAuthorities) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Instant.now().toEpochMilli());
        calendar.add(Calendar.HOUR, jwtConfiguration.getExpireInHours());
        return createJwtForClaims(subject,grantedAuthorities,calendar.getTime());
    }
    public String createJwtForClaims(String subject, List<GrantedAuthority> grantedAuthorities,Date expirationDate) {
        JwtBuilder builder =  Jwts.builder().setId("proxylive")
            .setSubject(subject)
            .claim("authorities",grantedAuthorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()))
            .setIssuedAt(new Date(System.currentTimeMillis()));

        if(expirationDate!=null) {
            builder.setExpiration(expirationDate);
        }
        return builder.signWith(SignatureAlgorithm.HS512,
                jwtConfiguration.getSecret().getBytes()).compact();
    }
}