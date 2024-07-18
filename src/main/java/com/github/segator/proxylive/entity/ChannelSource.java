package com.github.segator.proxylive.entity;

import lombok.Data;

@Data
public class ChannelSource {
    private String url;
    private String closeHook;
    private Integer priority;
    private String type="raw";

    public ChannelSource(int priorty, String url,String type) {
        this.priority=priorty;
        this.url=url;
        this.type=type;
    }


}
