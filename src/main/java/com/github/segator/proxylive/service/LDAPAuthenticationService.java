/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.LDAPAutentication;
import com.github.segator.proxylive.config.PlexAuthentication;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.util.Hashtable;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author isaac
 */
public class LDAPAuthenticationService implements AuthenticationService {
    private final Logger logger = LoggerFactory.getLogger(LDAPAuthenticationService.class);
    private LDAPAutentication ldapAuthConfig;
    @Autowired
    private ProxyLiveConfiguration configuration;

    @Override
    public boolean loginUser(String user, String password) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<String> getUserGroups(String user) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @PostConstruct
    private void initialize() throws MalformedURLException, ProtocolException, IOException, ParseException, NamingException {
        ldapAuthConfig = configuration.getAuthentication().getLdap();
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, ldapAuthConfig.getUser());
        env.put(Context.SECURITY_CREDENTIALS, ldapAuthConfig.getPassword());
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://"+ldapAuthConfig.getServer()+"/"+ldapAuthConfig.getSearchBase());
        env.put("java.naming.ldap.attributes.binary", "objectSID");
        LdapContext ctx = new InitialLdapContext();
        SearchResult srLdapUser = findAccountByAccountName(ctx, ldapAuthConfig.getSearchBase(), "segator");
           String primaryGroupSID = getPrimaryGroupSID(srLdapUser);
     
        
        //3) get the users Primary Group
        String primaryGroupName = findGroupBySID(ctx, ldapAuthConfig.getSearchBase(), primaryGroupSID);
        logger.trace(primaryGroupName);

    }

    public SearchResult findAccountByAccountName(DirContext ctx, String ldapSearchBase, String accountName) throws NamingException {

        String searchFilter = "(&(objectClass=user)(sAMAccountName=" + accountName + "))";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(ldapSearchBase, searchFilter, searchControls);

        SearchResult searchResult = null;
        if (results.hasMoreElements()) {
            searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if (results.hasMoreElements()) {
                logger.warn("Matched multiple users for the accountName: " + accountName);
                return null;
            }
        }

        return searchResult;
    }

    public String getPrimaryGroupSID(SearchResult srLdapUser) throws NamingException {
        byte[] objectSID = (byte[]) srLdapUser.getAttributes().get("objectSid").get();
        String strPrimaryGroupID = (String) srLdapUser.getAttributes().get("primaryGroupID").get();

        String strObjectSid = decodeSID(objectSID);

        return strObjectSid.substring(0, strObjectSid.lastIndexOf('-') + 1) + strPrimaryGroupID;
    }

    public String findGroupBySID(DirContext ctx, String ldapSearchBase, String sid) throws NamingException {

        String searchFilter = "(&(objectClass=group)(objectSid=" + sid + "))";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(ldapSearchBase, searchFilter, searchControls);

        if (results.hasMoreElements()) {
            SearchResult searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if (results.hasMoreElements()) {
                logger.warn("Matched multiple groups for the group with SID: " + sid);
                return null;
            } else {
                return (String) searchResult.getAttributes().get("sAMAccountName").get();
            }
        }
        return null;
    }

    public static String decodeSID(byte[] sid) {

        final StringBuilder strSid = new StringBuilder("S-");

        // get version
        final int revision = sid[0];
        strSid.append(Integer.toString(revision));

        //next byte is the count of sub-authorities
        final int countSubAuths = sid[1] & 0xFF;

        //get the authority
        long authority = 0;
        //String rid = "";
        for (int i = 2; i <= 7; i++) {
            authority |= ((long) sid[i]) << (8 * (5 - (i - 2)));
        }
        strSid.append("-");
        strSid.append(Long.toHexString(authority));

        //iterate all the sub-auths
        int offset = 8;
        int size = 4; //4 bytes for each sub auth
        for (int j = 0; j < countSubAuths; j++) {
            long subAuthority = 0;
            for (int k = 0; k < size; k++) {
                subAuthority |= (long) (sid[offset + k] & 0xFF) << (8 * k);
            }

            strSid.append("-");
            strSid.append(subAuthority);

            offset += size;
        }

        return strSid.toString();
    }

}
