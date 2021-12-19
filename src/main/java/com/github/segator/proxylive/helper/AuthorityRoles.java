package com.github.segator.proxylive.helper;

public enum AuthorityRoles {
    USER("USER"),
    SERVICE_ACCOUNT("SERVICE_ACCOUNT"),
    ALLOW_ENCODING("ENCODE_PULLER"),
    ADMIN("ADMIN");
    private String authority;
    AuthorityRoles(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }
}
