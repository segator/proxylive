package com.github.segator.proxylive.config;

public class EPGConfiguration {
    private String url;
    private long refresh;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getRefresh() {
        return refresh;
    }

    public void setRefresh(long refresh) {
        this.refresh = refresh;
    }
}
