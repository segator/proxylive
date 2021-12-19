/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

import com.github.segator.proxylive.helper.AuthorityRoles;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 *
 * @author isaac
 */

public interface AuthenticationService {
   public boolean loginUser(String user, String password) throws Exception;
   public List<GrantedAuthority> getUserRoles(String user);
}
