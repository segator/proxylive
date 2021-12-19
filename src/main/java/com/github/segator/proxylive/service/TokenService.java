package com.github.segator.proxylive.service;

import com.github.segator.proxylive.helper.AuthorityRoles;
import com.github.segator.proxylive.helper.JwtHelper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Calendar;
import java.util.List;

@Service
public class TokenService {
    private final JwtHelper jwtHelper;

    public TokenService(JwtHelper jwtHelper) {
        this.jwtHelper = jwtHelper;
    }

    public String createServiceAccountRequestToken(String subject){
        List<GrantedAuthority> grantedAuthorities = AuthorityUtils
                .createAuthorityList(
                        AuthorityRoles.SERVICE_ACCOUNT.getAuthority(),
                        AuthorityRoles.ALLOW_ENCODING.getAuthority()
                );
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Instant.now().toEpochMilli());
        calendar.add(Calendar.MINUTE, 15);
        return jwtHelper.createJwtForClaims(String.format("SA-%s",subject),grantedAuthorities,calendar.getTime());
    }
}
