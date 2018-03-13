/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

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
    public List<String> getUserGroups(String user) {
        ArrayList<String> userGroups = new ArrayList();
        userGroups.add("all");
        return userGroups;
    }

}
