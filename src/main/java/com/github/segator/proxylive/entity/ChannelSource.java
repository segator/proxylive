package com.github.segator.proxylive.entity;

public class ChannelSource {
    private String url;
    private String closeHook;
    private Integer priority;
    private String type="raw";
    public ChannelSource(){

    }
    public ChannelSource(int priorty, String url,String type) {
        this.priority=priorty;
        this.url=url;
        this.type=type;
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

    public String getType() {
        return type;
    }

    public String getCloseHook() {
        return closeHook;
    }

    public void setCloseHook(String closeHook) {
        this.closeHook = closeHook;
    }

    public void setType(String type) {
        this.type = type;
    }
}
