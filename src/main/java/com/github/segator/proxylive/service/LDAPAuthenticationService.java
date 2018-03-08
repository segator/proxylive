/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

import java.util.List;

/**
 *
 * @author isaac
 */
public class LDAPAuthenticationService implements AuthenticationService{

    @Override
    public boolean loginUser(String user, String password) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<String> getUserGroups(String user) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
