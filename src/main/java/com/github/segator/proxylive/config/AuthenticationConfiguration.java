/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.config;

/**
 *
 * @author isaac
 */
public class AuthenticationConfiguration {
    
    private PlexAuthentication plex;
    private LDAPAutentication ldap;

    public PlexAuthentication getPlex() {
        return plex;
    }

    public void setPlex(PlexAuthentication plex) {
        this.plex = plex;
    }

    public LDAPAutentication getLdap() {
        return ldap;
    }

    public void setLdap(LDAPAutentication ldap) {
        this.ldap = ldap;
    }
    
}
