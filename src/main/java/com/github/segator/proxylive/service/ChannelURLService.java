package com.github.segator.proxylive.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class ChannelURLService implements ChannelService {

    @Autowired
    private ProxyLiveConfiguration config;

    private List<Channel> channels;
    private File tempLogoFilePath;
    private long lastUpdate=0;


    @Override
    public List<Channel> getChannelList() {
        return channels;
    }

    @Override
    public Channel getChannelByID(String channelID) {
        Optional<Channel> channelOptional = channels.stream().filter(ch -> ch.getId().equals(channelID)).findFirst();
        if(channelOptional.isPresent()){
            return channelOptional.get();
        }else{
            return null;
        }
    }



    @Scheduled(fixedDelay = 60 * 1000) //Every Minute
    @PostConstruct
    public void getChannelInfoFromURL() throws Exception {
        if(new Date().getTime()-lastUpdate>+(config.getSource().getChannels().getRefresh()*1000)) {

            ObjectMapper objectMapper = new ObjectMapper();
            URL channelURL = new URL(config.getSource().getChannels().getUrl());
            BufferedReader in = new BufferedReader(new InputStreamReader(channelURL.openStream()));
            List<Channel> channels = objectMapper.readValue(in, new TypeReference<List<Channel>>(){});
            in.close();
            this.channels=channels;
            System.out.println("Channels refreshed");
            lastUpdate=new Date().getTime();
        }
    }
}
