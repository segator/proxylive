package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChannelServiceFactory {

    @Autowired
    private ProxyLiveConfiguration config;
    @Bean
    public ChannelService createChannelService() {
        switch(config.getSource().getChannels().getType()){
            case "tvheadend":
                return new ChannelTVHeadendService();
            case "json":
                return new ChannelURLService();
        }
        return null;
    }
}
