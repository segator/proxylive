package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.AuthToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.util.*;

@Service
public class TokensService {

    @Autowired
    private ProxyLiveConfiguration config;

    private Map<String,AuthToken> tokensMap;

    public TokensService() {
        tokensMap=new HashMap();
    }

    public AuthToken getTokenByID(String id){
        if(config.getInternalToken().equals(id)){
            return new AuthToken("internal",id,null);
        }
        return tokensMap.get(id);
    }

    public void deleteTokenByID(String id){
        tokensMap.remove(id);
    }

    public void addTokenByID(AuthToken authToken) throws Exception {
        if(tokensMap.get(authToken.getId())!=null){
            throw new Exception("Token already exists");
        }
        tokensMap.put(authToken.getId(),authToken);
    }

    public List<AuthToken> getAllTokens(){
        return new ArrayList(tokensMap.values());
    }
    @Scheduled(fixedDelay = 60 * 1000)
    private void cronCleanExpiredTokens(){
        List<String> expiredTokens=new ArrayList();
        long now = new Date().getTime();
        for (AuthToken authToken:tokensMap.values()) {
            if(now > authToken.getExpirationDate().getTime()){
                expiredTokens.add(authToken.getId());
            }
        }
        for (String expiredTokenID:expiredTokens) {
            tokensMap.remove(expiredTokenID);
        }
    }
}
