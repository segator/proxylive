package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChannelServiceFactory {

    private final ProxyLiveConfiguration config;

    public ChannelServiceFactory(ProxyLiveConfiguration config) {
        this.config = config;
    }

    @Bean
    public ChannelService createChannelService() {
        switch(config.getSource().getChannels().getType()){
            case "tvheadend":
                return new ChannelTVHeadendService();
            case "json":
                return new ChannelURLService();
            case "m3u8":
                return new ChannelM3u8Service();
        }
        return null;
    }
}
