package com.github.segator.proxylive.entity;

public class ChannelSource {
    private String url;
    private int priority;

    public ChannelSource(int priorty, String url) {
        this.priority=priorty;
        this.url=url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
