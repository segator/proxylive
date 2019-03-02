package com.github.segator.proxylive.entity;

public class ChannelSource {
    private String url;
    private Integer priority;

    public ChannelSource(){

    }
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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
