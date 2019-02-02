package com.github.segator.proxylive.entity;

import java.util.Date;

public class AuthToken {
    private String user;
    private String id;
    private Date expirationDate;

    public AuthToken(String user, String id, Date expirationDate) {
        this.user = user;
        this.id = id;
        this.expirationDate = expirationDate;
    }

    public String getUser() {
        return user;
    }

    public String getId() {
        return id;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }
}
