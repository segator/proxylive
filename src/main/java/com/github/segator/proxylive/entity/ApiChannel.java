package com.github.segator.proxylive.entity;

import java.io.File;
import java.util.List;

public class ApiChannel {
    private Integer number;
    private String name;
    private String id;
    private String epgID;
    private String logoURL;
    private String channelURL;
    private List<EPGProgram> epgProgramList;
    private List<String> categories;

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEpgID() {
        return epgID;
    }

    public void setEpgID(String epgID) {
        this.epgID = epgID;
    }

    public String getLogoURL() {
        return logoURL;
    }

    public void setLogoURL(String logoURL) {
        this.logoURL = logoURL;
    }

    public String getChannelURL() {
        return channelURL;
    }

    public void setChannelURL(String channelURL) {
        this.channelURL = channelURL;
    }

    public List<EPGProgram> getEpgProgramList() {
        return epgProgramList;
    }

    public void setEpgProgramList(List<EPGProgram> epgProgramList) {
        this.epgProgramList = epgProgramList;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public static ApiChannel Create(Channel channel) {
        ApiChannel apiChannel= new ApiChannel();
        apiChannel.setNumber(channel.getNumber());
        apiChannel.setCategories(channel.getCategories());
        apiChannel.setEpgID(channel.getEpgID());
        apiChannel.setId(channel.getId());
        apiChannel.setName(channel.getName());
        return apiChannel;
    }
}
