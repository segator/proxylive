/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

import com.github.segator.proxylive.helper.AuthorityRoles;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author isaac
 */
public class WithoutAuthenticationService implements AuthenticationService {

    @Override
    public boolean loginUser(String user, String password) throws Exception {
        return true;
    }

    @Override
    public List<GrantedAuthority> getUserRoles(String user) {
        ArrayList<GrantedAuthority> roles = new ArrayList();

        roles.add(new SimpleGrantedAuthority(AuthorityRoles.USER.getAuthority()));
        roles.add(new SimpleGrantedAuthority(AuthorityRoles.ADMIN.getAuthority()));
        roles.add(new SimpleGrantedAuthority(AuthorityRoles.ALLOW_ENCODING.getAuthority()));
        return roles;
    }

}
