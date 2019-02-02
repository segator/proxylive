package com.github.segator.proxylive.service;

import com.github.segator.proxylive.entity.Channel;

import java.util.List;

public interface ChannelService {
    public List<Channel> getChannelList();

    public Channel getChannelByID(String channelID);

}
